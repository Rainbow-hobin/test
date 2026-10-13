package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.constant.StatusConstant;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.Dish;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.service.AddressBookService;
import com.sky.service.ChatOrderService;
import com.sky.service.OrderService;
import com.sky.service.ShoppingCartService;
import com.sky.vo.OrderSubmitVO;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能客服对话下单状态机（内存态）。
 *
 * 流程：点菜（可多轮追加/移除）→ 地址确认（必须用户明确确认）→ 订单核对 → 提交。
 * 菜品/数量用中文短语文法解析（精确包含优先 + 拼音/编辑距离模糊兜底 + 多候选编号确认），
 * 不依赖外部 LLM，响应即时。
 * 提交时复用与用户端完全相同的 ShoppingCartService / OrderService.submitOrder 链路。
 */
@Service
@Slf4j
public class ChatOrderServiceImpl implements ChatOrderService {

    /** 会话 30 分钟无交互自动失效 */
    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;
    /** 配送费与用户端结算页保持一致（H5: total + 7） */
    private static final BigDecimal DELIVERY_FEE = new BigDecimal("7.00");

    private static final int COLLECT_DISH = 1;
    private static final int CONFIRM_ADDRESS = 2;
    private static final int CONFIRM_ORDER = 3;

    /** 数量：阿拉伯数字或常用中文数词，单位可省略 */
    private static final Pattern QTY_PATTERN =
            Pattern.compile("(\\d+|十|[一二两三四五六七八九])\\s*(?:份|个|盒|瓶|只|条|杯|碗|道)?");
    private static final Pattern PURE_NUMBER = Pattern.compile("^\\d{1,2}$");
    private static final String[] CN_NUM = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};

    /** 强意图词：无需命中菜名即可进入下单 */
    private static final String[] STRONG_INTENT = {"下单", "点单", "点餐", "点菜", "帮我点", "帮我下", "我要点", "我想点"};

    /** 推荐类问题关键词（“有推荐吗”“什么好吃”等，用销量数据回答，不走知识库） */
    private static final String[] RECOMMEND_WORDS = {
            "推荐", "招牌", "爆款", "卖得好", "卖得最好", "销量", "好吃", "吃啥", "吃什么", "啥好吃", "什么好吃", "想吃啥", "建议"
    };
    /** 弱意图词：配合菜名命中/模糊命中才进入下单 */
    private static final String[] WEAK_INTENT = {"来一份", "来两份", "来一", "来两", "来个", "来份", "来盘", "来碗",
            "点一份", "点个", "我要", "想吃", "还要", "再来", "加一份", "加个", "帮我来"};
    /** 「再来一单」意图词：整句命中（且不含具体菜名）时按最近一笔订单直接备菜 */
    private static final String[] REPEAT_WORDS = {
            "再来一单", "再下一单", "重新下一单", "重新点", "再来一次", "重复上一单", "重复上次",
            "上次那个订单", "和上次一样", "跟上次一样", "照旧", "再点一次"};
    private static final String[] REMOVE_WORDS = {"不要", "去掉", "移除", "删掉", "删除", "减了"};
    private static final String[] DONE_WORDS = {"好了", "就这些", "够了", "可以了", "完事", "结算", "买单",
            "下一步", "去下单", "提交", "确认下单", "就这样"};
    /** 疑问词：整句含疑问词时不做“整句≈菜名”的激进匹配，避免把提问劫持为下单 */
    private static final String[] QUESTION_WORDS = {"吗", "怎么", "好吃", "推荐", "介绍", "区别", "多少", "？", "?"};

    /** 分隔符：标点/空白等非文字字符，以及口语连接词（用于把一句话拆成候选片段） */
    private static final Pattern SEG_SEP = Pattern.compile(
            "[^\\p{L}\\p{N}]+|和|跟|与|还有|再来|以及|然后|再|加");
    /** 模糊匹配最低得分 */
    private static final double FUZZY_MIN_SCORE = 0.75;

    /** 点菜倾向的数量词：阿拉伯数字（单位可省）或中文数词+单位；裸中文数词不算（如“等一下”） */
    private static final Pattern ORDERISH_QTY =
            Pattern.compile("\\d+\\s*(?:份|个|盒|瓶|只|条|杯|碗|道)?|[一二两三四五六七八九十]\\s*(?:份|个|盒|瓶|只|条|杯|碗|道)");

    /** 拼音输出格式：小写、无声调 */
    private static final HanyuPinyinOutputFormat PY_FORMAT = new HanyuPinyinOutputFormat();

    static {
        PY_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        PY_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private AddressBookService addressBookService;
    @Autowired
    private ShoppingCartService shoppingCartService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private OrderMapper orderMapper;

    // ================= 对外入口 =================

    @Override
    public Result handle(Long userId, String text) {
        if (userId == null || text == null) {
            return Result.pass();
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return Result.pass();
        }

        Session s = sessions.get(userId);
        if (s != null && System.currentTimeMillis() - s.lastActive > SESSION_TTL_MS) {
            sessions.remove(userId);
            s = null;
        }

        // 进行中主动要求转人工：结束下单会话，交回 RAG/人工链路
        if (s != null && containsAny(t, "转人工", "找人工", "人工客服", "换客服", "真人")) {
            sessions.remove(userId);
            return Result.pass();
        }

        // 「再来一单」：整句命中复购意图且不含具体菜名时，按最近一笔订单直接备菜；
        // 仅在无会话或刚处于点菜阶段时触发（地址/核对阶段说“再来一单”不劫持）
        if ((s == null || s.state == COLLECT_DISH) && containsAny(t, REPEAT_WORDS)) {
            List<Dish> menu = onSaleMenu();
            if (parseDishes(t, menu).isEmpty() && fuzzyMatch(t, menu).isEmpty()) {
                return repeatLastOrder(userId, s, menu);
            }
        }

        // 推荐类问题（“有推荐吗”“什么好吃”）：用真实销量直接回答，不依赖知识库；
        // 消息里带具体菜名的不算（如“宫保鸡丁好吃吗”交给 RAG），进行中的会话不受影响
        if (containsAny(t, RECOMMEND_WORDS)) {
            List<Dish> menu = onSaleMenu();
            if (parseDishes(t, menu).isEmpty() && fuzzyMatch(t, menu).isEmpty()) {
                return Result.of(Reply.text(recommendText(s, menu)));
            }
        }

        try {
            if (s == null) {
                return startNew(userId, t);
            }
            s.lastActive = System.currentTimeMillis();
            switch (s.state) {
                case CONFIRM_ADDRESS:
                    return handleAddress(userId, s, t);
                case CONFIRM_ORDER:
                    return handleConfirm(userId, s, t);
                case COLLECT_DISH:
                default:
                    return handleCollect(userId, s, t);
            }
        } catch (Exception e) {
            log.error("对话下单处理异常 userId={}", userId, e);
            return Result.of(Reply.text("抱歉，下单助手刚刚开小差了，请稍后再试；回复「取消」可放弃本次下单。"));
        }
    }

    @Override
    public boolean hasActiveSession(Long userId) {
        Session s = sessions.get(userId);
        if (s == null) {
            return false;
        }
        if (System.currentTimeMillis() - s.lastActive > SESSION_TTL_MS) {
            sessions.remove(userId);
            return false;
        }
        return true;
    }

    @Override
    public void cancelSession(Long userId) {
        sessions.remove(userId);
    }

    // ================= 状态：点菜 =================

    private Result startNew(Long userId, String t) {
        List<Dish> menu = onSaleMenu();
        List<Hit> hits = parseDishes(t, menu);
        boolean loose = containsAny(t, STRONG_INTENT) || containsAny(t, WEAK_INTENT);

        if (hits.isEmpty()) {
            List<Candidate> fz = fuzzyMatch(t, menu);
            if (loose && !fz.isEmpty()) {
                // 有意图词 + 模糊命中：唯一直接采用，多个让用户确认
                Session s = newSession(userId);
                if (fz.size() == 1) {
                    mergeCandidate(s, fz.get(0));
                    return Result.of(Reply.text(collectedText(s, menu)));
                }
                s.pending = fz;
                return Result.of(Reply.text(pendingConfirmText(fz)));
            }
            if (!loose && fz.size() == 1) {
                // 无意图词：仅当整句近似单个菜名（如纯错别字报菜名）才接管，避免误伤普通提问
                Candidate c = fz.get(0);
                if (c.score >= 0.9 && !containsAny(t, QUESTION_WORDS)
                        && norm(t).length() <= norm(c.dish.getName()).length() + 2) {
                    Session s = newSession(userId);
                    mergeCandidate(s, c);
                    return Result.of(Reply.text(collectedText(s, menu)));
                }
            }
            if (!loose) {
                return Result.pass();
            }
            Session s = newSession(userId);
            return Result.of(Reply.text(welcomeMenuText(menu)));
        }

        if (!loose && !isOrderIntent(t, hits)) {
            return Result.pass();
        }
        Session s = newSession(userId);
        return mergeExactResult(s, t, menu, hits);
    }

    /**
     * 「再来一单」：读最近一笔未取消订单的明细 → 清空购物车 → 批量加入购物车 →
     * 发"去下单"按钮，用户点击后进入确认订单页自行提交。
     * 在售校验：明细里的菜品已下架时跳过并提示。
     */
    private Result repeatLastOrder(Long userId, Session cur, List<Dish> menu) {
        Orders last = asUser(userId, () -> orderMapper.getLatestByUserId(userId));
        if (last == null) {
            return Result.of(Reply.text("没有找到你的历史订单～直接告诉我菜名和数量即可，例如「宫保鸡丁2份」。"));
        }
        List<OrderDetail> details = orderDetailMapper.getByOrderId(last.getId());
        if (details == null || details.isEmpty()) {
            return Result.of(Reply.text("你上一笔订单没有商品明细，直接告诉我菜名和数量重新点吧～"));
        }

        // 中断进行中的对话下单会话，改为购物车流程
        if (cur != null) {
            sessions.remove(userId);
        }

        // 过滤已下架菜品，把在售明细转为购物车对象
        List<ShoppingCart> cartList = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (OrderDetail d : details) {
            boolean onSale = d.getDishId() != null
                    && menu.stream().anyMatch(x -> x.getId().equals(d.getDishId()));
            if (onSale) {
                ShoppingCart sc = new ShoppingCart();
                sc.setName(d.getName());
                sc.setUserId(userId);
                sc.setDishId(d.getDishId());
                sc.setSetmealId(d.getSetmealId());
                sc.setDishFlavor(d.getDishFlavor());
                int qty = d.getNumber() == null || d.getNumber() < 1 ? 1 : d.getNumber();
                sc.setNumber(qty);
                sc.setAmount(d.getAmount());
                sc.setImage(d.getImage());
                sc.setCreateTime(LocalDateTime.now());
                cartList.add(sc);
            } else if (d.getName() != null) {
                skipped.add(d.getName());
            }
        }
        if (cartList.isEmpty()) {
            return Result.of(Reply.text("你上一笔订单（尾号 " + orderNoTail(last) + "）里的菜品现在都不在售了，"
                    + "直接告诉我菜名重新点吧～"));
        }

        // 清空购物车再批量加入，确保购物车只含本次复购的菜品
        asUser(userId, () -> {
            shoppingCartService.cleanShoppingCart();
            shoppingCartService.addFromOrderDetails(cartList);
            return null;
        });

        BigDecimal goods = BigDecimal.ZERO;
        for (ShoppingCart c : cartList) {
            goods = goods.add(c.getAmount().multiply(new BigDecimal(c.getNumber())));
        }
        StringBuilder sb = new StringBuilder("已为你准备好上一单的 ")
                .append(cartList.size()).append(" 道菜品，小计 ￥").append(money(goods))
                .append("（另需配送费及打包费）");
        if (!skipped.isEmpty()) {
            sb.append("\n（上一单中的「").append(String.join("、", skipped)).append("」暂不在售，已跳过）");
        }
        return Result.of(
                Reply.text(sb.toString()),
                Reply.action("去下单", "order")
        );
    }

    /** 订单号脱敏展示：保留末 4 位 */
    private String orderNoTail(Orders o) {
        String no = o.getNumber();
        if (no == null || no.length() <= 4) {
            return no == null ? "" : no;
        }
        return no.substring(no.length() - 4);
    }

    private Result handleCollect(Long userId, Session s, String t) {
        List<Dish> menu = onSaleMenu();

        // 整体取消（消息里没有点/移除具体菜名时才算，避免“不要香菜”误判）
        if (isCancel(t) && parseDishes(t, menu).isEmpty()) {
            sessions.remove(userId);
            return Result.of(Reply.text("好的，已取消本次下单，需要时再叫我～"));
        }

        // 疑问句保护：消息含菜名但无点餐/移除意图时交给 RAG 回答，
        // 避免“宫保鸡丁怎么做”这类提问被当成加菜
        if (containsAny(t, QUESTION_WORDS)
                && !containsAny(t, STRONG_INTENT) && !containsAny(t, WEAK_INTENT)
                && !containsAny(t, REMOVE_WORDS)) {
            if (!parseDishes(t, menu).isEmpty() || !fuzzyMatch(t, menu).isEmpty()) {
                return Result.pass();
            }
        }

        // 移除已点菜品
        if (containsAny(t, REMOVE_WORDS)) {
            List<Hit> hits = parseDishes(t, menu);
            if (!hits.isEmpty()) {
                hits.forEach(h -> s.items.remove(h.dish.getId()));
                s.pending = Collections.emptyList();
                return Result.of(Reply.text(afterRemoveText(s, menu)));
            }
            // 待确认候选中的编号移除
            if (!s.pending.isEmpty()) {
                List<Integer> sel = pickIndexes(t, s.pending.size());
                if (!sel.isEmpty()) {
                    sel.forEach(i -> s.items.remove(s.pending.get(i).dish.getId()));
                    s.pending = Collections.emptyList();
                    return Result.of(Reply.text(afterRemoveText(s, menu)));
                }
            }
            // 模糊唯一命中时直接移除（如“去掉宫爆鸡丁”）
            List<Candidate> fz = fuzzyMatch(t, menu);
            if (fz.size() == 1) {
                s.items.remove(fz.get(0).dish.getId());
                return Result.of(Reply.text(afterRemoveText(s, menu)));
            }
            // 未匹配到要移除的菜 → 继续按加菜/其他指令处理
        }

        // 点菜完成 → 进入地址确认
        if (containsAny(t, DONE_WORDS)) {
            s.pending = Collections.emptyList();
            return enterAddress(userId, s);
        }

        // 精确加菜
        List<Hit> hits = parseDishes(t, menu);
        if (!hits.isEmpty()) {
            return mergeExactResult(s, t, menu, hits);
        }

        // 待确认候选：用户回复编号选择（支持「1」「1和2」「第2个」「都要」）
        // 仅当整句是编号选择样式才处理，避免“1和3哪个便宜”被误当选择
        if (!s.pending.isEmpty() && isSelectionText(t)) {
            List<Integer> sel = pickIndexes(t, s.pending.size());
            if (!sel.isEmpty()) {
                sel.forEach(i -> mergeCandidate(s, s.pending.get(i)));
                s.pending = Collections.emptyList();
                return Result.of(Reply.text(collectedText(s, menu)));
            }
            if (containsAny(t, "都要", "全要", "全部")) {
                s.pending.forEach(c -> mergeCandidate(s, c));
                s.pending = Collections.emptyList();
                return Result.of(Reply.text(collectedText(s, menu)));
            }
        }

        // 模糊加菜：同音错别字/增删字兜底
        // 含疑问词且无点餐意图时不做模糊加菜，交还 RAG 回答（避免“宫保鸡丁怎么做”被当成点菜）
        if (!containsAny(t, QUESTION_WORDS) || containsAny(t, STRONG_INTENT) || containsAny(t, WEAK_INTENT)) {
            List<Candidate> fz = fuzzyMatch(t, menu);
            if (fz.size() == 1) {
                mergeCandidate(s, fz.get(0));
                return Result.of(Reply.text(collectedText(s, menu)));
            }
            if (fz.size() > 1) {
                s.pending = fz;
                return Result.of(Reply.text(pendingConfirmText(fz)));
            }
        }

        // 全都认不出：空会话直接交还 RAG/人工
        if (s.items.isEmpty()) {
            sessions.remove(userId);
            return Result.pass();
        }
        // 已点菜的会话中：像点菜的话术（数量词/加菜引导词）→ 提示报菜名；
        // 其他消息（如“营业到几点”“怎么开发票”）→ 交给 RAG 回答，点菜进度保留不打断
        if (ORDERISH_QTY.matcher(t).find()
                || containsAny(t, "再来", "来份", "来个", "来一", "加个", "加份", "份")) {
            return Result.of(Reply.text("没听清是哪道菜～在售菜品有：" + menuNames(menu)
                    + "。直接回复如「宫保鸡丁2份」即可加菜，回复「好了」进入地址确认，回复「取消」放弃。"));
        }
        return Result.pass();
    }

    /** 移除后的统一文案 */
    private String afterRemoveText(Session s, List<Dish> menu) {
        if (s.items.isEmpty()) {
            return "已移除。还想吃点什么？直接回复菜名和数量即可，例如「鱼香肉丝1份」；回复「取消」放弃下单。";
        }
        return collectedText(s, menu);
    }

    // ================= 状态：地址确认 =================

    private Result enterAddress(Long userId, Session s) {
        List<AddressBook> list = asUser(userId, () ->
                addressBookService.list(AddressBook.builder().userId(userId).build()));
        s.addresses = list == null ? Collections.emptyList() : list;
        s.state = CONFIRM_ADDRESS;

        if (s.addresses.isEmpty()) {
            return Result.of(Reply.text("点菜完成，接下来需要确认配送地址。\n"
                    + "你还没有收货地址，请先在「我的 → 收货地址」中新增地址，添加好后回来回复我「好了」；"
                    + "回复「取消」可放弃下单。"));
        }
        AddressBook def = s.addresses.stream()
                .filter(a -> a.getIsDefault() != null && a.getIsDefault() == 1)
                .findFirst()
                .orElse(s.addresses.get(0));
        s.selectedAddressId = def.getId();
        return Result.of(Reply.text(addressListText(s.addresses, s.selectedAddressId)));
    }

    private Result handleAddress(Long userId, Session s, String t) {
        if (isCancel(t)) {
            sessions.remove(userId);
            return Result.of(Reply.text("好的，已取消本次下单，需要时再叫我～"));
        }
        if (containsAny(t, "加菜", "再点", "继续点菜", "还要点", "返回点菜")) {
            s.state = COLLECT_DISH;
            return Result.of(Reply.text("好的，继续点菜，直接回复菜名和数量即可，例如「麻婆豆腐1份」，点完回复「好了」。"));
        }
        if (containsAny(t, "添加好", "新增好", "有地址了", "地址好了") || t.equals("好了")) {
            return enterAddress(userId, s);
        }
        if (containsAny(t, "换地址", "换一个", "重新选", "重选", "换个地址")) {
            if (s.addresses.isEmpty()) {
                return enterAddress(userId, s);
            }
            return Result.of(Reply.text(addressListText(s.addresses, s.selectedAddressId)));
        }
        Integer idx = pickIndex(t, s.addresses.size());
        if (idx != null) {
            s.selectedAddressId = s.addresses.get(idx).getId();
            return Result.of(Reply.text(addressListText(s.addresses, s.selectedAddressId)));
        }
        if (isConfirm(t)) {
            return enterOrderConfirm(userId, s);
        }
        return Result.of(Reply.text("请回复「确认」使用当前地址，或回复地址编号（如 2）切换；"
                + "需要新增地址，添加后回复「好了」。"));
    }

    // ================= 状态：订单核对/提交 =================

    private Result enterOrderConfirm(Long userId, Session s) {
        AddressBook addr = s.addresses.stream()
                .filter(a -> a.getId() != null && a.getId().equals(s.selectedAddressId))
                .findFirst().orElse(null);
        if (addr == null) {
            return enterAddress(userId, s);
        }
        s.state = CONFIRM_ORDER;
        List<ShoppingCart> cart = asUser(userId, () -> shoppingCartService.showShoppingCart());
        return Result.of(Reply.text(confirmText(s, addr, cart)));
    }

    private Result handleConfirm(Long userId, Session s, String t) {
        if (isCancel(t)) {
            sessions.remove(userId);
            return Result.of(Reply.text("好的，已取消本次下单，购物车没有变动，需要时再叫我～"));
        }
        if (containsAny(t, "改地址", "换地址", "换个地址")) {
            return enterAddress(userId, s);
        }
        if (containsAny(t, "加菜", "再点", "还要点", "继续点")) {
            s.state = COLLECT_DISH;
            return Result.of(Reply.text("好的，继续点菜，直接回复菜名和数量即可，点完回复「好了」。"));
        }
        if (isPlaceOrder(t)) {
            return placeOrder(userId, s);
        }
        return Result.of(Reply.text("请回复「确认下单」提交订单，回复「改地址」「加菜」调整，或回复「取消」放弃。"));
    }

    private Result placeOrder(Long userId, Session s) {
        AddressBook addr = s.addresses.stream()
                .filter(a -> a.getId() != null && a.getId().equals(s.selectedAddressId))
                .findFirst().orElse(null);
        if (addr == null) {
            return enterAddress(userId, s);
        }
        List<Dish> menu = onSaleMenu();
        Map<Long, Dish> dishMap = new LinkedHashMap<>();
        menu.forEach(d -> dishMap.put(d.getId(), d));

        BaseContext.setCurrentId(userId);
        List<ShoppingCart> existed = null;
        try {
            existed = shoppingCartService.showShoppingCart();

            // 把机器人会话中的菜品加入购物车（每次 +1，加 qty 次）
            for (Map.Entry<Long, Integer> e : s.items.entrySet()) {
                if (!dishMap.containsKey(e.getKey())) {
                    // 菜品期间被停售
                    sessions.remove(userId);
                    return Result.of(Reply.text("抱歉，「" + dishNameOf(e.getKey(), s) + "」当前已停售，本次下单已终止，请重新下单。"));
                }
                ShoppingCartDTO dto = new ShoppingCartDTO();
                dto.setDishId(e.getKey());
                for (int i = 0; i < e.getValue(); i++) {
                    shoppingCartService.addShoppingCart(dto);
                }
            }

            // 机器人点菜金额（以当前菜品价格为准）
            BigDecimal robotTotal = BigDecimal.ZERO;
            for (Map.Entry<Long, Integer> e : s.items.entrySet()) {
                Dish d = dishMap.get(e.getKey());
                if (d != null && d.getPrice() != null) {
                    robotTotal = robotTotal.add(d.getPrice().multiply(BigDecimal.valueOf(e.getValue())));
                }
            }
            // 购物车中原有商品（已在确认文案中告知会一起结算）
            BigDecimal cartTotal = BigDecimal.ZERO;
            if (existed != null) {
                for (ShoppingCart c : existed) {
                    if (c.getAmount() != null) {
                        cartTotal = cartTotal.add(c.getAmount().multiply(BigDecimal.valueOf(c.getNumber())));
                    }
                }
            }
            BigDecimal amount = robotTotal.add(cartTotal).add(DELIVERY_FEE)
                    .setScale(2, RoundingMode.HALF_UP);

            OrdersSubmitDTO submit = new OrdersSubmitDTO();
            submit.setAddressBookId(addr.getId());
            submit.setPayMethod(1);
            submit.setRemark("");
            submit.setEstimatedDeliveryTime(null);
            submit.setDeliveryStatus(1);
            submit.setTablewareNumber(1);
            submit.setTablewareStatus(1);
            submit.setPackAmount(1);
            submit.setAmount(amount);

            OrderSubmitVO vo = orderService.submitOrder(submit);
            sessions.remove(userId);
            log.info("智能客服代下单成功 userId={} orderId={} amount={}", userId, vo.getId(), amount);
            return Result.of(
                    Reply.text("下单成功！\n订单号：" + vo.getOrderNumber()
                            + "\n应付 ￥" + money(vo.getOrderAmount())
                            + "，请在 15 分钟内到「订单」页完成支付，超时订单会自动取消。"),
                    Reply.orderCard(vo.getId()));
        } catch (Exception e) {
            log.warn("智能客服代下单失败 userId={}: {}", userId, e.getMessage());
            safeRestoreCart(existed);
            String msg = e.getMessage();
            if (msg == null || msg.isEmpty()) {
                msg = "服务繁忙，请稍后再试";
            }
            return Result.of(Reply.text("下单没成功：" + msg + "。可回复「确认下单」重试，或回复「取消」放弃。"));
        } finally {
            BaseContext.removeCurrentId();
        }
    }

    /** 下单失败后把购物车恢复到本次操作前的快照，避免机器人加的菜残留在用户购物车 */
    private void safeRestoreCart(List<ShoppingCart> snapshot) {
        if (snapshot == null) {
            return;
        }
        try {
            shoppingCartService.cleanShoppingCart();
            for (ShoppingCart c : snapshot) {
                ShoppingCartDTO dto = new ShoppingCartDTO();
                dto.setDishId(c.getDishId());
                dto.setSetmealId(c.getSetmealId());
                dto.setDishFlavor(c.getDishFlavor());
                int n = c.getNumber() == null ? 1 : c.getNumber();
                for (int i = 0; i < n; i++) {
                    shoppingCartService.addShoppingCart(dto);
                }
            }
        } catch (Exception ex) {
            log.warn("恢复购物车快照失败：{}", ex.getMessage());
        }
    }

    private String dishNameOf(Long dishId, Session s) {
        // 停售兜底：会话中不存菜名，简单返回占位
        return "该菜品";
    }

    // ================= 文案 =================

    private String welcomeMenuText(List<Dish> menu) {
        return "没问题，我来帮你下单。\n目前在售菜品有：" + menuNames(menu)
                + "。\n请告诉我菜品名称和数量，例如「宫保鸡丁2份」，也可以一次报多个菜。";
    }

    /** 推荐回复：优先真实销量（已完成订单 Top3，过滤已下架），无销量数据时取在售前 3 道 */
    private String recommendText(Session s, List<Dish> menu) {
        List<OrderDetail> tops = null;
        try {
            tops = orderDetailMapper.topSold(3);
        } catch (Exception e) {
            log.warn("查询销量推荐失败：{}", e.getMessage());
        }
        List<OrderDetail> recs = new ArrayList<>();
        if (tops != null) {
            for (OrderDetail od : tops) {
                for (Dish d : menu) {
                    if (d.getName().equals(od.getName())) {
                        recs.add(od);
                        break;
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder("为你推荐");
        String firstName;
        if (recs.size() >= 2) {
            sb.append("（近期销量最高）：\n");
            int i = 1;
            for (OrderDetail od : recs) {
                sb.append("· ").append(i++).append(". ").append(od.getName())
                        .append("（已售").append(od.getNumber()).append("份）\n");
            }
            firstName = recs.get(0).getName();
        } else {
            sb.append("：\n");
            int i = 1;
            firstName = menu.isEmpty() ? "宫保鸡丁" : menu.get(0).getName();
            for (Dish d : menu) {
                if (i > 3) {
                    break;
                }
                sb.append("· ").append(i++).append(". ").append(d.getName())
                        .append("（￥").append(money(d.getPrice())).append("）\n");
            }
        }
        if (s != null) {
            sb.append("回复菜名即可加入当前订单，如「").append(firstName).append("1份」；回复「好了」进入地址确认。");
        } else {
            sb.append("回复「帮我点餐」开始下单，或直接说「").append(firstName).append("1份」。");
        }
        return sb.toString();
    }

    private String collectedText(Session s, List<Dish> menu) {
        StringBuilder sb = new StringBuilder("好的，已为你记下：\n");
        BigDecimal goods = appendItems(sb, s, menu);
        sb.append("小计 ￥").append(money(goods)).append("（另需配送费 ￥7.00）\n");
        sb.append("还需要别的菜吗？回复菜名继续添加（如「麻婆豆腐1份」），回复「好了」进入地址确认。")
          .append("期间也可以直接问我别的问题（如「营业时间」），点菜进度会保留，回复「取消」放弃。");
        return sb.toString();
    }

    private String addressListText(List<AddressBook> list, Long selectedId) {
        StringBuilder sb = new StringBuilder("请确认配送地址（回复编号可切换）：\n");
        for (int i = 0; i < list.size(); i++) {
            AddressBook a = list.get(i);
            boolean picked = a.getId() != null && a.getId().equals(selectedId);
            sb.append(picked ? "✅ " : "    ");
            sb.append(i + 1).append(". ");
            sb.append(safe(a.getConsignee())).append("　").append(safe(a.getPhone())).append("　")
                    .append(safe(a.getDetail()));
            if (a.getIsDefault() != null && a.getIsDefault() == 1) {
                sb.append("（默认）");
            }
            sb.append("\n");
        }
        sb.append("回复「确认」使用 ✅ 地址；也可以去「我的 → 收货地址」新增地址后回复「好了」。");
        return sb.toString();
    }

    private String confirmText(Session s, AddressBook addr, List<ShoppingCart> cart) {
        StringBuilder sb = new StringBuilder("请核对订单：\n");
        List<Dish> menu = onSaleMenu();
        BigDecimal goods = appendItems(sb, s, menu);
        if (cart != null && !cart.isEmpty()) {
            for (ShoppingCart c : cart) {
                sb.append("· ").append(safe(c.getName())).append("（购物车已有） ×").append(c.getNumber())
                        .append("　￥").append(money(c.getAmount().multiply(BigDecimal.valueOf(c.getNumber())))).append("\n");
                if (c.getAmount() != null) {
                    goods = goods.add(c.getAmount().multiply(BigDecimal.valueOf(c.getNumber())));
                }
            }
        }
        sb.append("───\n");
        sb.append("商品合计 ￥").append(money(goods)).append("，配送费 ￥").append(money(DELIVERY_FEE)).append("\n");
        sb.append("应付：￥").append(money(goods.add(DELIVERY_FEE))).append("\n");
        sb.append("配送地址：").append(safe(addr.getConsignee())).append(" ")
                .append(safe(addr.getPhone())).append(" ").append(safe(addr.getDetail())).append("\n");
        sb.append("回复「确认下单」立即提交；回复「改地址」「加菜」可调整；回复「取消」放弃。");
        return sb.toString();
    }

    private BigDecimal appendItems(StringBuilder sb, Session s, List<Dish> menu) {
        BigDecimal goods = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : s.items.entrySet()) {
            Dish d = menu.stream().filter(x -> x.getId().equals(e.getKey())).findFirst().orElse(null);
            String name = d != null ? d.getName() : "菜品#" + e.getKey();
            BigDecimal price = d != null && d.getPrice() != null ? d.getPrice() : BigDecimal.ZERO;
            BigDecimal line = price.multiply(BigDecimal.valueOf(e.getValue()));
            goods = goods.add(line);
            sb.append("· ").append(name).append(" ×").append(e.getValue())
                    .append("　￥").append(money(line)).append("\n");
        }
        return goods;
    }

    private String menuNames(List<Dish> menu) {
        List<String> names = new ArrayList<>();
        for (Dish d : menu) {
            if (d.getName() != null) {
                names.add(d.getName());
            }
            if (names.size() >= 12) {
                break;
            }
        }
        return String.join("、", names);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ================= 模糊匹配（同音错别字/增删字兜底） =================

    /**
     * 模糊匹配用户话术中的菜品，精确包含匹配失败后的兜底层。
     * 评分（取各片段最高分）：归一化包含 0.95 → 反向包含 0.93（用户省略后缀，如“宫保鸡丁”→“宫保鸡丁饭”）
     * → 拼音包含 0.90（同音错别字，如 宫爆→宫保）→ 拼音反向 0.88 → 编辑距离相似度。
     * 返回得分降序、最多 5 个候选（≥ FUZZY_MIN_SCORE）。
     */
    private List<Candidate> fuzzyMatch(String t, List<Dish> menu) {
        List<int[]> segs = segments(t);
        Map<Long, Candidate> best = new LinkedHashMap<>();
        for (Dish d : menu) {
            String name = d.getName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            String nName = norm(name);
            String pName = pinyinOf(name);
            if (nName.isEmpty()) {
                continue;
            }
            double bestScore = 0;
            int bestQty = 1;
            for (int[] seg : segs) {
                String segText = t.substring(seg[0], seg[1]);
                // 先去掉数量词再匹配，避免「2份」等干扰包含判断与编辑距离
                String namePart = QTY_PATTERN.matcher(segText).replaceAll(" ");
                String nSeg = norm(namePart);
                if (nSeg.isEmpty()) {
                    continue;
                }
                String pSeg = pinyinOf(namePart);
                double score = 0;
                if (nSeg.contains(nName)) {
                    score = 0.95;
                } else if (nName.contains(nSeg) && nSeg.length() >= 2) {
                    score = 0.93;
                } else if (name.length() >= 3 && pSeg.contains(pName)) {
                    score = 0.90;
                } else if (pName.contains(pSeg) && pSeg.length() >= 4) {
                    score = 0.88;
                } else {
                    double sim = similarity(nSeg, nName);
                    if (sim >= FUZZY_MIN_SCORE) {
                        score = sim;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestQty = qtyInRange(t, seg[0], seg[1]);
                }
            }
            if (bestScore >= FUZZY_MIN_SCORE) {
                best.merge(d.getId(), new Candidate(d, bestQty, bestScore),
                        (a, b) -> a.score >= b.score ? a : b);
            }
        }
        List<Candidate> out = new ArrayList<>(best.values());
        out.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
        if (out.size() > 5) {
            out.subList(5, out.size()).clear();
        }
        return out;
    }

    /** 把一句话拆成候选片段（原文本偏移区间）：按标点/空白/连接词切分 */
    private List<int[]> segments(String t) {
        List<int[]> out = new ArrayList<>();
        int from = 0;
        Matcher m = SEG_SEP.matcher(t);
        while (m.find()) {
            if (m.start() > from) {
                out.add(new int[]{from, m.start()});
            }
            from = m.end();
        }
        if (from < t.length()) {
            out.add(new int[]{from, t.length()});
        }
        return out;
    }

    /** 片段内解析数量（如“宫保鸡丁2份”→2），无数量默认 1 */
    private int qtyInRange(String t, int start, int end) {
        Matcher m = QTY_PATTERN.matcher(t.substring(start, end));
        while (m.find()) {
            int q = parseQty(m.group(1));
            if (q > 0) {
                return Math.min(q, 99);
            }
        }
        return 1;
    }

    /** 归一化：全角→半角、小写、仅保留字母/数字（菜名与话术同规则处理后再比对） */
    private String norm(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= 0xFF01 && c <= 0xFF5E) {
                c -= 0xFEE0;
            } else if (c == 0x3000) {
                c = ' ';
            }
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /** 转无分隔拼音串（非汉字保留原文），用于同音错别字匹配：宫爆/宫保 → gongbao */
    private String pinyinOf(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            String[] py = null;
            try {
                py = PinyinHelper.toHanyuPinyinStringArray(c, PY_FORMAT);
            } catch (BadHanyuPinyinOutputFormatCombination e) {
                // 忽略格式异常，按非汉字处理
            }
            if (py != null && py.length > 0) {
                sb.append(py[0]);
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /** 相似度 = 1 - 编辑距离/较长串长度 */
    private double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int dist = levenshtein(a, b);
        return 1.0 - (double) dist / Math.max(a.length(), b.length());
    }

    private int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    /** 解析用户回复的候选编号：支持「1」「1和2」「第2个」「二」「2号」 */
    private List<Integer> pickIndexes(String t, int size) {
        List<Integer> out = new ArrayList<>();
        Matcher m = Pattern.compile("\\d{1,2}").matcher(t);
        while (m.find()) {
            int v = Integer.parseInt(m.group());
            if (v >= 1 && v <= size && !out.contains(v - 1)) {
                out.add(v - 1);
            }
        }
        if (out.isEmpty()) {
            for (int i = 0; i < size && i < CN_NUM.length; i++) {
                if (t.contains("第" + CN_NUM[i] + "个") || t.contains(CN_NUM[i] + "号") || t.equals(CN_NUM[i])) {
                    out.add(i);
                    break;
                }
            }
        }
        return out;
    }

    /** 整句是否为编号选择样式（如「1」「1和2」「第2个」「都要」），防止“1和3哪个便宜”被误当选择 */
    private boolean isSelectionText(String t) {
        String s = t.trim();
        if (s.isEmpty()) {
            return false;
        }
        boolean shape = s.matches("[0-9一二三四五六七八九十第个号和、,，。\\s都要全选]+");
        if (!shape) {
            return false;
        }
        return s.matches(".*\\d.*") || containsAny(s, CN_NUM) || containsAny(s, "都要", "全要", "全部");
    }

    /** 候选确认文案 */
    private String pendingConfirmText(List<Candidate> fz) {
        StringBuilder sb = new StringBuilder("你想点的可能是下面哪一样？\n");
        for (int i = 0; i < fz.size(); i++) {
            Dish d = fz.get(i).dish;
            sb.append(i + 1).append(". ").append(safe(d.getName()))
                    .append("　￥").append(money(d.getPrice())).append("\n");
        }
        sb.append("回复编号加入（可多选，如「1和2」）；都不是的话请换种说法，或回复「取消」放弃。");
        return sb.toString();
    }

    private Session newSession(Long userId) {
        Session s = new Session();
        s.lastActive = System.currentTimeMillis();
        sessions.put(userId, s);
        return s;
    }

    private void mergeCandidate(Session s, Candidate c) {
        s.items.merge(c.dish.getId(), c.qty, Integer::sum);
    }

    /**
     * 精确命中合并后，对剩余片段再做一次模糊匹配：
     * 避免“鱼香肉丝和宫爆鸡丁”这类一句里精确命中一个、另一个是错别字时被静默丢弃。
     * 剩余片段唯一命中 → 直接采用；多个 → 记入待确认候选并在文案后追问。
     */
    private Result mergeExactResult(Session s, String t, List<Dish> menu, List<Hit> hits) {
        mergeHits(s, hits);
        s.pending = Collections.emptyList();
        StringBuilder sb = new StringBuilder(t);
        for (Hit h : hits) {
            for (int i = h.start; i < h.end && i < sb.length(); i++) {
                sb.setCharAt(i, '，');
            }
        }
        List<Candidate> extra = fuzzyMatch(sb.toString(), menu);
        if (extra.isEmpty()) {
            return Result.of(Reply.text(collectedText(s, menu)));
        }
        if (extra.size() == 1) {
            mergeCandidate(s, extra.get(0));
            return Result.of(Reply.text(collectedText(s, menu)));
        }
        s.pending = extra;
        return Result.of(Reply.text(collectedText(s, menu) + "\n另外，" + pendingConfirmText(extra)));
    }

    // ================= 菜品/数量解析 =================

    private List<Dish> onSaleMenu() {
        List<Dish> list = dishMapper.list(Dish.builder().status(StatusConstant.ENABLE).build());
        return list == null ? Collections.emptyList() : list;
    }

    private void mergeHits(Session s, List<Hit> hits) {
        for (Hit h : hits) {
            s.items.merge(h.dish.getId(), h.qty, Integer::sum);
        }
    }

    /**
     * 从一句话中解析菜品及数量：
     * 菜名按长度降序做包含匹配并占用字符区间（防子串互吃），
     * 数量 token（数字/中文数词 ± 单位）按就近原则分配给菜名，未分配数量默认 1。
     */
    private List<Hit> parseDishes(String text, List<Dish> menu) {
        List<Dish> sorted = new ArrayList<>(menu);
        sorted.sort(Comparator.comparingInt((Dish d) -> d.getName() == null ? 0 : d.getName().length()).reversed());

        boolean[] used = new boolean[text.length()];
        List<Hit> hits = new ArrayList<>();
        for (Dish d : sorted) {
            String name = d.getName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            int idx = text.indexOf(name);
            while (idx >= 0) {
                if (!rangeOccupied(used, idx, idx + name.length())) {
                    markRange(used, idx, idx + name.length());
                    hits.add(new Hit(d, idx, idx + name.length()));
                    break;
                }
                idx = text.indexOf(name, idx + 1);
            }
        }
        if (hits.isEmpty()) {
            return hits;
        }

        // 收集数量 token
        List<int[]> qtys = new ArrayList<>();
        Matcher m = QTY_PATTERN.matcher(text);
        while (m.find()) {
            int qty = parseQty(m.group(1));
            if (qty > 0) {
                qtys.add(new int[]{m.start(), m.end(), qty});
            }
        }

        // 菜名与数量按距离贪心配对（距离 <= 8 个字符）
        boolean[] qtyUsed = new boolean[qtys.size()];
        List<long[]> pairs = new ArrayList<>(); // {hitIndex, qtyIndex, dist}
        for (int hi = 0; hi < hits.size(); hi++) {
            Hit h = hits.get(hi);
            for (int qi = 0; qi < qtys.size(); qi++) {
                int qs = qtys.get(qi)[0];
                int qe = qtys.get(qi)[1];
                long dist;
                if (qe <= h.start) {
                    dist = (long) h.start - qe;          // 数量在菜名前
                } else if (qs >= h.end) {
                    dist = (long) qs - h.end;            // 数量在菜名后
                } else {
                    // 与菜名区间交叠（如菜名本身含数字），不用
                    continue;
                }
                if (dist <= 8) {
                    pairs.add(new long[]{hi, qi, dist});
                }
            }
        }
        pairs.sort(Comparator.comparingLong(p -> p[2]));
        boolean[] hitAssigned = new boolean[hits.size()];
        for (long[] p : pairs) {
            int hi = (int) p[0];
            int qi = (int) p[1];
            if (hitAssigned[hi] || qtyUsed[qi]) {
                continue;
            }
            hits.get(hi).qty = qtys.get(qi)[2];
            hitAssigned[hi] = true;
            qtyUsed[qi] = true;
        }
        return hits;
    }

    private boolean rangeOccupied(boolean[] used, int from, int to) {
        for (int i = from; i < to; i++) {
            if (used[i]) {
                return true;
            }
        }
        return false;
    }

    private void markRange(boolean[] used, int from, int to) {
        for (int i = from; i < to; i++) {
            used[i] = true;
        }
    }

    private int parseQty(String s) {
        if (s == null || s.isEmpty()) {
            return 1;
        }
        char c = s.charAt(0);
        if (Character.isDigit(c)) {
            try {
                int v = Integer.parseInt(s);
                return Math.min(v, 99);
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        switch (s) {
            case "一": return 1;
            case "二":
            case "两": return 2;
            case "三": return 3;
            case "四": return 4;
            case "五": return 5;
            case "六": return 6;
            case "七": return 7;
            case "八": return 8;
            case "九": return 9;
            case "十": return 10;
            default: return 1;
        }
    }

    // ================= 意图/关键词 =================

    private boolean isOrderIntent(String t, List<Hit> hits) {
        // 强意图词：无需命中菜名也进入下单（进入后再问菜名）
        if (containsAny(t, STRONG_INTENT)) {
            return true;
        }
        if (hits.isEmpty()) {
            return false;
        }
        // 弱引导词 + 命中菜名
        if (containsAny(t, WEAK_INTENT)) {
            return true;
        }
        // 直接“菜名+显式数量”，如「宫保鸡丁2份」
        return hits.stream().anyMatch(h -> h.qty > 1) || containsAny(t, "份", "个菜");
    }

    private boolean isCancel(String t) {
        return containsAny(t, "取消", "不点了", "不下了", "算了", "放弃", "我不要了", "不要了");
    }

    private boolean isConfirm(String t) {
        return containsAny(t, "确认", "没错", "就这个", "用这个", "可以", "行", "好的", "好", "对的", "是的", "ok", "OK");
    }

    private boolean isPlaceOrder(String t) {
        return containsAny(t, "确认下单", "提交订单", "提交", "下单", "可以", "没问题", "确认", "是的", "对", "好的", "行", "ok", "OK");
    }

    private boolean containsAny(String t, String... words) {
        for (String w : words) {
            if (t.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** 选择地址编号：支持 "2"、"选2"、"用第2个"、"第二个" */
    private Integer pickIndex(String t, int size) {
        Integer n = null;
        if (PURE_NUMBER.matcher(t).matches()) {
            n = Integer.valueOf(t);
        } else if (containsAny(t, "第", "选", "用", "要", "个", "号")) {
            Matcher m = Pattern.compile("(\\d{1,2})").matcher(t);
            if (m.find()) {
                n = Integer.valueOf(m.group(1));
            }
        }
        if (n == null) {
            for (int i = 0; i < size && i < CN_NUM.length; i++) {
                if (t.contains("第" + CN_NUM[i] + "个") || t.equals(CN_NUM[i])) {
                    n = i + 1;
                    break;
                }
            }
        }
        if (n == null || n < 1 || n > size) {
            return null;
        }
        return n - 1;
    }

    private <T> T asUser(Long userId, Supplier<T> fn) {
        BaseContext.setCurrentId(userId);
        try {
            return fn.get();
        } finally {
            BaseContext.removeCurrentId();
        }
    }

    // ================= 内部模型 =================

    private static class Session {
        private int state = COLLECT_DISH;
        private final LinkedHashMap<Long, Integer> items = new LinkedHashMap<>();
        /** 待用户确认的模糊匹配候选（回复编号后按此合并） */
        private List<Candidate> pending = Collections.emptyList();
        private List<AddressBook> addresses = Collections.emptyList();
        private Long selectedAddressId;
        private long lastActive = System.currentTimeMillis();
    }

    /** 模糊匹配候选：菜品 + 片段内解析出的数量 + 匹配得分 */
    private static class Candidate {
        private final Dish dish;
        private final int qty;
        private final double score;

        private Candidate(Dish dish, int qty, double score) {
            this.dish = dish;
            this.qty = qty;
            this.score = score;
        }
    }

    private static class Hit {
        private final Dish dish;
        private final int start;
        private final int end;
        private int qty = 1;

        private Hit(Dish dish, int start, int end) {
            this.dish = dish;
            this.start = start;
            this.end = end;
        }
    }
}

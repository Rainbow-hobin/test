package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.constant.StatusConstant;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.Dish;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.service.AddressBookService;
import com.sky.service.ChatOrderService;
import com.sky.service.OrderService;
import com.sky.service.ShoppingCartService;
import com.sky.vo.OrderSubmitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 菜品/数量用中文短语文法解析（菜名包含匹配 + 数量就近分配），不依赖外部 LLM，响应即时。
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

    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private AddressBookService addressBookService;
    @Autowired
    private ShoppingCartService shoppingCartService;
    @Autowired
    private OrderService orderService;

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

    // ================= 状态：点菜 =================

    private Result startNew(Long userId, String t) {
        List<Dish> menu = onSaleMenu();
        List<Hit> hits = parseDishes(t, menu);
        if (!isOrderIntent(t, hits)) {
            return Result.pass();
        }
        Session s = new Session();
        s.lastActive = System.currentTimeMillis();
        sessions.put(userId, s);
        if (hits.isEmpty()) {
            return Result.of(Reply.text(welcomeMenuText(menu)));
        }
        mergeHits(s, hits);
        return Result.of(Reply.text(collectedText(s, menu)));
    }

    private Result handleCollect(Long userId, Session s, String t) {
        List<Dish> menu = onSaleMenu();

        // 整体取消（消息里没有点/移除具体菜名时才算，避免“不要香菜”误判）
        if (isCancel(t) && parseDishes(t, menu).isEmpty()) {
            sessions.remove(userId);
            return Result.of(Reply.text("好的，已取消本次下单，需要时再叫我～"));
        }

        // 移除已点菜品
        if (containsAny(t, "不要", "去掉", "移除", "删掉", "删除", "减了")) {
            List<Hit> hits = parseDishes(t, menu);
            if (!hits.isEmpty()) {
                hits.forEach(h -> s.items.remove(h.dish.getId()));
                if (s.items.isEmpty()) {
                    return Result.of(Reply.text("已移除。还想吃点什么？直接回复菜名和数量即可，例如「鱼香肉丝1份」；回复「取消」放弃下单。"));
                }
                return Result.of(Reply.text(collectedText(s, menu)));
            }
        }

        // 点菜完成 → 进入地址确认
        if (containsAny(t, "好了", "就这些", "够了", "可以了", "完事", "结算", "买单", "下一步", "去下单", "提交", "确认下单", "就这样")) {
            return enterAddress(userId, s);
        }

        List<Hit> hits = parseDishes(t, menu);
        if (hits.isEmpty()) {
            return Result.of(Reply.text("没听清菜名～在售菜品有：" + menuNames(menu)
                    + "。直接回复如「宫保鸡丁2份」即可加菜，回复「好了」进入地址确认。"));
        }
        mergeHits(s, hits);
        return Result.of(Reply.text(collectedText(s, menu)));
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

    private String collectedText(Session s, List<Dish> menu) {
        StringBuilder sb = new StringBuilder("好的，已为你记下：\n");
        BigDecimal goods = appendItems(sb, s, menu);
        sb.append("小计 ￥").append(money(goods)).append("（另需配送费 ￥7.00）\n");
        sb.append("还需要别的菜吗？回复菜名继续添加（如「麻婆豆腐1份」），回复「好了」进入地址确认。");
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
        if (containsAny(t, "下单", "点单", "点餐", "点菜", "帮我点", "帮我下", "我要点", "我想点")) {
            return true;
        }
        if (hits.isEmpty()) {
            return false;
        }
        // 弱引导词 + 命中菜名
        if (containsAny(t, "来一份", "来两份", "来一", "来两", "来个", "来份", "来盘", "来碗",
                "点一份", "点个", "我要", "想吃", "还要", "再来", "加一份", "加个", "帮我来")) {
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
        private List<AddressBook> addresses = Collections.emptyList();
        private Long selectedAddressId;
        private long lastActive = System.currentTimeMillis();
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

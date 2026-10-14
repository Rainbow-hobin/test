package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.Dish;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.DishMapper;
import com.sky.service.ChatOrderService;
import com.sky.service.ChatToolService;
import com.sky.service.OrderService;
import com.sky.service.ShoppingCartService;
import com.sky.vo.OrderSubmitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM tool-calling 智能客服下单。
 *
 * 优先于关键词状态机调用：
 *  - LLM 理解用户意图 → 调用 Java 工具执行 → 循环直到最终回复
 *  - LLM 失败/超时 → 返回 null，调用方 fallback 到 ChatOrderService 关键词状态机
 */
@Service
@Slf4j
public class ChatToolServiceImpl implements ChatToolService {

    private static final String CHAT_ORDER_URL = "http://127.0.0.1:8000/api/chat_order";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_ROUNDS = 8;
    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;

    private final RestTemplate restTemplate;

    // 每用户对话历史：userId -> messages list（OpenAI 格式）
    private static final Map<Long, List<Map<String, Object>>> USER_MESSAGES = new ConcurrentHashMap<>();
    private static final Map<Long, Long> LAST_ACTIVE = new ConcurrentHashMap<>();

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private ShoppingCartService shoppingCartService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private AddressBookMapper addressBookMapper;

    public ChatToolServiceImpl(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS))
                .setReadTimeout(Duration.ofMillis(READ_TIMEOUT_MS))
                .build();
    }

    @Override
    public boolean hasActiveSession(Long userId) {
        List<Map<String, Object>> msgs = USER_MESSAGES.get(userId);
        if (msgs == null || msgs.isEmpty()) return false;
        Long last = LAST_ACTIVE.get(userId);
        return last != null && System.currentTimeMillis() - last < SESSION_TTL_MS;
    }

    @Override
    public void clearSession(Long userId) {
        USER_MESSAGES.remove(userId);
        LAST_ACTIVE.remove(userId);
    }

    private List<Map<String, Object>> getMessages(Long userId) {
        List<Map<String, Object>> msgs = USER_MESSAGES.get(userId);
        if (msgs == null) {
            msgs = new ArrayList<>();
            USER_MESSAGES.put(userId, msgs);
        }
        Long last = LAST_ACTIVE.get(userId);
        if (last != null && System.currentTimeMillis() - last > SESSION_TTL_MS) {
            msgs.clear();
        }
        LAST_ACTIVE.put(userId, System.currentTimeMillis());
        return msgs;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChatOrderService.Result handle(Long userId, String text) {
        if (userId == null || text == null || text.trim().isEmpty()) {
            return null;
        }

        List<Map<String, Object>> messages = getMessages(userId);
        // 加入用户消息
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", text.trim());
        messages.add(userMsg);

        try {
            // 第一轮调 Python
            Map<String, Object> resp = callPython(messages);
            if (resp == null || resp.containsKey("error")) {
                messages.remove(messages.size() - 1);
                return null;
            }

            // 工具调用循环
            for (int round = 0; round < MAX_ROUNDS; round++) {
                Boolean finish = (Boolean) resp.get("finish");
                if (Boolean.TRUE.equals(finish)) {
                    String content = (String) resp.get("content");
                    Map<String, Object> asstMsg = (Map<String, Object>) resp.get("assistant_message");
                    if (asstMsg != null) messages.add(asstMsg);
                    if (content == null || content.isEmpty()) {
                        content = "好的，已为您处理。";
                    }
                    return ChatOrderService.Result.of(ChatOrderService.Reply.text(content));
                }

                // 有 tool_calls：先存 assistant 消息（含 tool_calls），再执行工具
                Map<String, Object> asstMsg = (Map<String, Object>) resp.get("assistant_message");
                if (asstMsg != null) messages.add(asstMsg);

                List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) resp.get("tool_calls");
                if (toolCalls == null || toolCalls.isEmpty()) break;

                for (Map<String, Object> tc : toolCalls) {
                    String toolName = (String) tc.get("name");
                    Map<String, Object> args = (Map<String, Object>) tc.get("arguments");
                    String toolCallId = (String) tc.get("id");
                    String toolResult = executeTool(userId, toolName, args);

                    // 存 tool 结果消息（OpenAI 格式）
                    Map<String, Object> toolMsg = new HashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", toolCallId);
                    toolMsg.put("content", toolResult);
                    messages.add(toolMsg);
                }

                // 继续调 Python
                resp = callPython(messages);
                if (resp == null || resp.containsKey("error")) {
                    log.warn("LLM 续轮失败");
                    break;
                }
            }

            log.warn("LLM tool-calling 超过最大轮次或异常，fallback");
            return null;

        } catch (ResourceAccessException e) {
            log.warn("Python tool-calling 服务不可达: {}", e.getMessage());
            messages.remove(messages.size() - 1);
            return null;
        } catch (Exception e) {
            log.error("LLM tool-calling 处理异常 userId={}", userId, e);
            messages.remove(messages.size() - 1);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callPython(List<Map<String, Object>> messages) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("messages", messages);
            ResponseEntity<Map> resp = restTemplate.postForEntity(CHAT_ORDER_URL, req, Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return null;
            }
            return resp.getBody();
        } catch (Exception e) {
            log.warn("调用 Python 失败: {}", e.getMessage());
            return null;
        }
    }

    // ---- 工具执行 ----

    private String executeTool(Long userId, String toolName, Map<String, Object> args) {
        if (args == null) args = new HashMap<>();
        BaseContext.setCurrentId(userId);
        try {
            switch (toolName) {
                case "get_menu":       return toolGetMenu();
                case "add_to_cart":    return toolAddToCart(args);
                case "get_addresses":  return toolGetAddresses(userId);
                case "submit_order":   return toolSubmitOrder(args);
                case "clear_cart":     return toolClearCart();
                case "get_cart":       return toolGetCart();
                default: return jsonError("未知工具: " + toolName);
            }
        } catch (Exception e) {
            log.error("工具执行异常 {}: {}", toolName, e.getMessage());
            return jsonError("工具执行失败: " + e.getMessage());
        }
    }

    private String jsonError(String msg) {
        return "{\"error\":\"" + msg.replace("\"", "\\\"") + "\"}";
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String safe(String s) { return s == null ? "" : s; }

    private String toolGetMenu() {
        List<Dish> list = dishMapper.list(Dish.builder().status(StatusConstant.ENABLE).build());
        if (list == null || list.isEmpty()) return "{\"dishes\":[]}";
        StringBuilder sb = new StringBuilder("{\"dishes\":[");
        for (int i = 0; i < list.size(); i++) {
            Dish d = list.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"id\":").append(d.getId())
              .append(",\"name\":\"").append(escape(d.getName())).append("\"")
              .append(",\"price\":").append(d.getPrice())
              .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String toolAddToCart(Map<String, Object> args) {
        String dishName = (String) args.get("dish_name");
        if (dishName == null || dishName.trim().isEmpty()) return jsonError("缺少菜名");
        int qty = 1;
        Object qObj = args.get("quantity");
        if (qObj instanceof Number) qty = ((Number) qObj).intValue();
        if (qty < 1) qty = 1;
        if (qty > 99) qty = 99;

        List<Dish> menu = dishMapper.list(Dish.builder().status(StatusConstant.ENABLE).build());
        Dish matched = null;
        if (menu != null) {
            String target = dishName.trim();
            for (Dish d : menu) {
                if (d.getName() != null && d.getName().equals(target)) { matched = d; break; }
            }
            if (matched == null) {
                for (Dish d : menu) {
                    if (d.getName() != null && (d.getName().contains(target) || target.contains(d.getName()))) {
                        matched = d; break;
                    }
                }
            }
        }
        if (matched == null) return jsonError("未找到菜品: " + dishName);

        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(matched.getId());
        for (int i = 0; i < qty; i++) {
            shoppingCartService.addShoppingCart(dto);
        }
        return "{\"ok\":true,\"dish\":\"" + escape(matched.getName()) + "\",\"quantity\":" + qty + "}";
    }

    private String toolGetAddresses(Long userId) {
        AddressBook query = new AddressBook();
        query.setUserId(userId);
        List<AddressBook> list = addressBookMapper.list(query);
        if (list == null || list.isEmpty()) return "{\"addresses\":[]}";
        StringBuilder sb = new StringBuilder("{\"addresses\":[");
        for (int i = 0; i < list.size(); i++) {
            AddressBook a = list.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"id\":").append(a.getId())
              .append(",\"consignee\":\"").append(escape(a.getConsignee())).append("\"")
              .append(",\"phone\":\"").append(escape(a.getPhone())).append("\"")
              .append(",\"detail\":\"").append(escape(safe(a.getProvinceName()) + safe(a.getCityName()) + safe(a.getDistrictName()) + safe(a.getDetail()))).append("\"")
              .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String toolSubmitOrder(Map<String, Object> args) {
        Object addrIdObj = args.get("address_id");
        if (!(addrIdObj instanceof Number)) return jsonError("缺少地址ID");
        Long addressId = ((Number) addrIdObj).longValue();

        List<ShoppingCart> cart = shoppingCartService.showShoppingCart();
        if (cart == null || cart.isEmpty()) return jsonError("购物车为空");

        BigDecimal total = BigDecimal.ZERO;
        for (ShoppingCart c : cart) {
            if (c.getAmount() != null) total = total.add(c.getAmount().multiply(BigDecimal.valueOf(c.getNumber())));
        }
        BigDecimal amount = total.add(new BigDecimal("7.00")).setScale(2, RoundingMode.HALF_UP);

        OrdersSubmitDTO submit = new OrdersSubmitDTO();
        submit.setAddressBookId(addressId);
        submit.setPayMethod(1);
        submit.setRemark("");
        submit.setEstimatedDeliveryTime(null);
        submit.setDeliveryStatus(1);
        submit.setTablewareNumber(1);
        submit.setTablewareStatus(1);
        submit.setPackAmount(1);
        submit.setAmount(amount);

        OrderSubmitVO vo = orderService.submitOrder(submit);
        log.info("LLM tool-calling 下单成功 orderId={}", vo.getId());
        return "{\"ok\":true,\"order_id\":" + vo.getId()
             + ",\"order_number\":\"" + escape(vo.getOrderNumber()) + "\""
             + ",\"amount\":" + vo.getOrderAmount() + "}";
    }

    private String toolClearCart() {
        shoppingCartService.cleanShoppingCart();
        return "{\"ok\":true}";
    }

    private String toolGetCart() {
        List<ShoppingCart> cart = shoppingCartService.showShoppingCart();
        if (cart == null || cart.isEmpty()) return "{\"items\":[]}";
        StringBuilder sb = new StringBuilder("{\"items\":[");
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < cart.size(); i++) {
            ShoppingCart c = cart.get(i);
            if (i >  0) sb.append(",");
            sb.append("{\"name\":\"").append(escape(c.getName())).append("\"")
              .append(",\"quantity\":").append(c.getNumber())
              .append(",\"amount\":").append(c.getAmount())
              .append("}");
            if (c.getAmount() != null) total = total.add(c.getAmount().multiply(BigDecimal.valueOf(c.getNumber())));
        }
        sb.append("],\"total\":").append(total).append("}");
        return sb.toString();
    }
}

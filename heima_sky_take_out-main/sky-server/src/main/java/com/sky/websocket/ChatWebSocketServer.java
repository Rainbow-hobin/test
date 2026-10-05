package com.sky.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.sky.constant.JwtClaimsConstant;
import com.sky.entity.ChatMessage;
import com.sky.entity.Orders;
import com.sky.json.JacksonObjectMapper;
import com.sky.mapper.ChatMessageMapper;
import com.sky.mapper.OrderMapper;
import com.sky.properties.JwtProperties;
import com.sky.service.BotService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户 ↔ 商家客服 聊天 WebSocket
 * 连接地址：/ws/chat?token={jwt}&type=1|2（1 用户端，2 商家端）
 * 身份在握手时由 token 解析，不能由客户端自报，防止串号冒充。
 */
@Component
@Slf4j
@ServerEndpoint("/ws/chat")
public class ChatWebSocketServer {

    //发送方类型
    private static final int TYPE_USER = 1;
    private static final int TYPE_MERCHANT = 2;
    private static final int TYPE_BOT = 3;
    //消息类型
    private static final int MSG_TEXT = 1;
    private static final int MSG_ORDER = 2;

    //机器人问答异步线程池（LLM 调用耗时长，不能阻塞 WS 线程）
    private static final ExecutorService BOT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "chat-bot");
        t.setDaemon(true);
        return t;
    });

    //在线用户：userId -> session（同一用户后连覆盖先连）
    private static final Map<Long, Session> USER_SESSIONS = new ConcurrentHashMap<>();
    //在线客服（员工）：empId -> session，用户消息群发给所有在线客服
    private static final Map<Long, Session> MERCHANT_SESSIONS = new ConcurrentHashMap<>();

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new JacksonObjectMapper();

    //@ServerEndpoint 每次连接新建实例，依赖通过静态注入
    private static JwtProperties jwtProperties;
    private static ChatMessageMapper chatMessageMapper;
    private static OrderMapper orderMapper;
    private static BotService botService;

    @Autowired
    public void setJwtProperties(JwtProperties jwtProperties) {
        ChatWebSocketServer.jwtProperties = jwtProperties;
    }

    @Autowired
    public void setChatMessageMapper(ChatMessageMapper chatMessageMapper) {
        ChatWebSocketServer.chatMessageMapper = chatMessageMapper;
    }

    @Autowired
    public void setOrderMapper(OrderMapper orderMapper) {
        ChatWebSocketServer.orderMapper = orderMapper;
    }

    @Autowired
    public void setBotService(BotService botService) {
        ChatWebSocketServer.botService = botService;
    }

    //当前连接身份
    private int identityType;
    private Long identityId;
    private Session session;
    private boolean authed;

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        this.session = session;
        try {
            Map<String, String> query = parseQuery(session.getQueryString());
            String token = query.get("token");
            String typeStr = query.get("type");
            int type = String.valueOf(TYPE_MERCHANT).equals(typeStr) ? TYPE_MERCHANT : TYPE_USER;
            if (token == null || token.isEmpty()) {
                reject(session, "缺少token");
                return;
            }
            Long id;
            if (type == TYPE_USER) {
                Claims claims = com.sky.utils.JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
                id = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
                USER_SESSIONS.put(id, session);
            } else {
                Claims claims = com.sky.utils.JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
                id = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
                MERCHANT_SESSIONS.put(id, session);
            }
            this.identityType = type;
            this.identityId = id;
            this.authed = true;
            log.info("聊天WebSocket建立连接：{} {}", type == TYPE_USER ? "用户" : "客服", id);
        } catch (Exception e) {
            log.warn("聊天WebSocket鉴权失败：{}", e.getMessage());
            reject(session, "鉴权失败");
        }
    }

    @OnMessage
    public void onMessage(String message) {
        if (!authed) {
            return;
        }
        try {
            JsonNode node = JSON.readTree(message);
            String clientMsgId = node.path("clientMsgId").asText(null);
            int msgType = node.path("msgType").asInt(MSG_TEXT);
            String content = node.path("content").asText(null);

            ChatMessage chatMessage;
            if (identityType == TYPE_USER) {
                //用户发给商家
                if (msgType == MSG_ORDER) {
                    long orderId = node.path("orderId").asLong(0);
                    String snapshot = buildOrderSnapshot(orderId, identityId);
                    if (snapshot == null) {
                        sendJson(session, errorEnvelope("订单不存在"));
                        return;
                    }
                    chatMessage = buildMessage(identityId, TYPE_USER, identityId, MSG_ORDER, snapshot, orderId);
                    chatMessageMapper.insert(chatMessage);
                    broadcastToMerchants(msgEnvelope(chatMessage));
                } else {
                    if (content == null || content.trim().isEmpty()) {
                        return;
                    }
                    chatMessage = buildMessage(identityId, TYPE_USER, identityId, MSG_TEXT, content.trim(), null);
                    chatMessageMapper.insert(chatMessage);
                    sendAck(session, clientMsgId, chatMessage);
                    if (node.path("forceHuman").asBoolean(false)) {
                        //用户选择了人工客服：跳过机器人，直接转人工
                        broadcastToMerchants(msgEnvelope(chatMessage));
                    } else {
                        //先回执用户，再异步走智能客服：答得上则机器人回复，答不上再转人工
                        asyncBotReply(chatMessage, session);
                    }
                    return;
                }
            } else {
                //客服发给指定用户
                long toUserId = node.path("toUser").asLong(0);
                if (toUserId <= 0 || content == null || content.trim().isEmpty()) {
                    sendJson(session, errorEnvelope("参数错误"));
                    return;
                }
                chatMessage = buildMessage(toUserId, TYPE_MERCHANT, identityId, MSG_TEXT, content.trim(), null);
                chatMessageMapper.insert(chatMessage);
                Session target = USER_SESSIONS.get(toUserId);
                if (target != null) {
                    sendJson(target, msgEnvelope(chatMessage));
                }
            }
            //回执给发送方（带库中id与时间，前端替换临时气泡）
            sendAck(session, clientMsgId, chatMessage);
        } catch (Exception e) {
            log.error("处理聊天消息失败", e);
            sendJson(session, errorEnvelope("消息发送失败"));
        }
    }

    private void sendAck(Session s, String clientMsgId, ChatMessage chatMessage) {
        Map<String, Object> ack = new HashMap<>();
        ack.put("type", "ack");
        ack.put("clientMsgId", clientMsgId);
        ack.put("message", chatMessage);
        sendJson(s, ack);
    }

    /**
     * 智能客服异步应答：答得上则机器人直接回复用户（不打扰商家），
     * 答不上则把用户消息转给在线人工客服。
     */
    private void asyncBotReply(ChatMessage userMsg, Session userSession) {
        BOT_EXECUTOR.submit(() -> {
            String answer = null;
            try {
                if (botService != null) {
                    answer = botService.askBot(userMsg.getContent());
                }
            } catch (Exception e) {
                log.error("智能客服调用失败", e);
            }
            if (answer != null && !answer.isEmpty()) {
                try {
                    //机器人已答复：用户消息置已读，避免商家端产生未读红点
                    chatMessageMapper.markReadById(userMsg.getId());
                    ChatMessage botMsg = buildMessage(userMsg.getUserId(), TYPE_BOT, 0L, MSG_TEXT, answer, null);
                    botMsg.setIsRead(1);
                    chatMessageMapper.insert(botMsg);
                    sendJson(userSession, msgEnvelope(botMsg));
                } catch (Exception e) {
                    log.error("保存机器人回复失败", e);
                }
            } else {
                //答不了：转人工，并告知用户已转接，避免用户干等
                boolean merchantOnline = !MERCHANT_SESSIONS.isEmpty();
                String tipText = merchantOnline
                        ? "这个问题我还答不上来，已为你转接人工客服，请稍候～"
                        : "这个问题我还答不上来，人工客服当前不在线，请留言，客服上线后会尽快回复你。";
                try {
                    ChatMessage tip = buildMessage(userMsg.getUserId(), TYPE_BOT, 0L, MSG_TEXT, tipText, null);
                    tip.setIsRead(1);
                    chatMessageMapper.insert(tip);
                    sendJson(userSession, msgEnvelope(tip));
                } catch (Exception e) {
                    log.error("发送转人工提示失败", e);
                }
                //通知前端把客服模式切到人工，后续消息不再走机器人
                Map<String, Object> sw = new HashMap<>();
                sw.put("type", "modeSwitch");
                sw.put("mode", "human");
                sendJson(userSession, sw);
                broadcastToMerchants(msgEnvelope(userMsg));
            }
        });
    }

    @OnClose
    public void onClose() {
        removeSelf();
    }

    @OnError
    public void onError(Throwable e) {
        //连接异常关闭时也会触发 onClose，这里只记日志
        if (authed) {
            log.debug("聊天WebSocket异常：{} {}", identityType, identityId);
        }
    }

    /**
     * 生成订单卡片快照（服务端生成，内容不可伪造；并校验订单归属）
     */
    private String buildOrderSnapshot(long orderId, long userId) throws Exception {
        Orders order = orderMapper.getById(orderId);
        if (order == null || order.getUserId() == null || !order.getUserId().equals(userId)) {
            return null;
        }
        Map<String, Object> snap = new HashMap<>();
        snap.put("orderId", order.getId());
        snap.put("number", order.getNumber());
        snap.put("amount", order.getAmount());
        snap.put("status", order.getStatus());
        return JSON.writeValueAsString(snap);
    }

    private ChatMessage buildMessage(Long userId, int senderType, Long senderId,
                                     int msgType, String content, Long orderId) {
        return ChatMessage.builder()
                .userId(userId)
                .senderType(senderType)
                .senderId(senderId)
                .msgType(msgType)
                .content(content)
                .orderId(orderId)
                .isRead(0)
                .createTime(LocalDateTime.now())
                .build();
    }

    private Map<String, Object> msgEnvelope(ChatMessage m) {
        Map<String, Object> env = new HashMap<>();
        env.put("type", "msg");
        env.put("message", m);
        return env;
    }

    private Map<String, Object> errorEnvelope(String msg) {
        Map<String, Object> env = new HashMap<>();
        env.put("type", "error");
        env.put("msg", msg);
        return env;
    }

    private void broadcastToMerchants(Map<String, Object> envelope) {
        for (Session s : MERCHANT_SESSIONS.values()) {
            sendJson(s, envelope);
        }
    }

    private void sendJson(Session s, Object payload) {
        if (s == null || !s.isOpen()) {
            return;
        }
        try {
            s.getAsyncRemote().sendText(JSON.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("聊天消息推送失败：{}", e.getMessage());
        }
    }

    private void removeSelf() {
        if (!authed) {
            return;
        }
        if (identityType == TYPE_USER) {
            //只移除属于自己的连接（避免覆盖场景误删新连接）
            Session cur = USER_SESSIONS.get(identityId);
            if (cur != null && cur.getId().equals(session.getId())) {
                USER_SESSIONS.remove(identityId);
            }
        } else {
            Session cur = MERCHANT_SESSIONS.get(identityId);
            if (cur != null && cur.getId().equals(session.getId())) {
                MERCHANT_SESSIONS.remove(identityId);
            }
        }
        log.info("聊天WebSocket断开：{} {}", identityType == TYPE_USER ? "用户" : "客服", identityId);
    }

    private void reject(Session s, String reason) {
        try {
            s.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (Exception ignored) {
        }
    }

    private Map<String, String> parseQuery(String qs) {
        Map<String, String> map = new HashMap<>();
        if (qs == null || qs.isEmpty()) {
            return map;
        }
        for (String pair : qs.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                String k = URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }

    /** 供其他组件查询在线人数（可选） */
    public static int onlineUserCount() {
        return USER_SESSIONS.size();
    }

    public static List<Long> onlineMerchantIds() {
        return List.copyOf(MERCHANT_SESSIONS.keySet());
    }
}

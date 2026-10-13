package com.sky.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能客服「帮我下单」对话状态机。
 *
 * 在 WebSocket 收到用户文本消息时优先调用：
 *  - 命中下单流程（新意图或进行中的会话）→ handled=true，按 replies 回复用户；
 *  - 与下单无关 → handled=false，调用方继续走 RAG 问答/转人工。
 *
 * 状态保存在内存中（单机部署），30 分钟无交互自动过期。
 */
public interface ChatOrderService {

    /**
     * 处理一条用户消息。
     *
     * @param userId 用户 id
     * @param text   用户消息原文
     * @return 处理结果（是否接管 + 机器人回复列表）
     */
    ChatOrderService.Result handle(Long userId, String text);

    /**
     * 该用户是否存在进行中的下单会话（用于 forceHuman 等场景判断优先级）。
     */
    boolean hasActiveSession(Long userId);

    /**
     * 取消进行中的下单会话（用户明确要求转人工时调用，避免人工对话中状态机突然接管）。
     */
    void cancelSession(Long userId);

    /** 处理结果 */
    class Result {
        private final boolean handled;
        private final List<Reply> replies;

        private Result(boolean handled, List<Reply> replies) {
            this.handled = handled;
            this.replies = replies;
        }

        public static Result pass() {
            return new Result(false, new ArrayList<>());
        }

        public static Result of(Reply... replies) {
            List<Reply> list = new ArrayList<>();
            for (Reply r : replies) {
                list.add(r);
            }
            return new Result(true, list);
        }

        public static Result of(List<Reply> replies) {
            return new Result(true, replies);
        }

        public boolean isHandled() {
            return handled;
        }

        public List<Reply> getReplies() {
            return replies;
        }
    }

    /** 单条机器人回复：文本 / 订单卡片 / 动作按钮 */
    class Reply {
        /** 1 文本消息，2 订单卡片消息，3 动作按钮（与 ChatMessage.msgType 对齐） */
        private final int type;
        private final String text;
        /** type=2 时关联的订单 id */
        private final Long orderId;
        /** type=3 时前端跳转的路由名（如 "order"） */
        private final String action;

        private Reply(int type, String text, Long orderId, String action) {
            this.type = type;
            this.text = text;
            this.orderId = orderId;
            this.action = action;
        }

        public static Reply text(String text) {
            return new Reply(1, text, null, null);
        }

        public static Reply orderCard(Long orderId) {
            return new Reply(2, null, orderId, null);
        }

        /** 动作按钮：前端点击后跳转到指定路由 */
        public static Reply action(String text, String action) {
            return new Reply(3, text, null, action);
        }

        public int getType() {
            return type;
        }

        public String getText() {
            return text;
        }

        public Long getOrderId() {
            return orderId;
        }

        public String getAction() {
            return action;
        }
    }
}

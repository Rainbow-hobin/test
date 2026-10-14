package com.sky.service;

/**
 * LLM tool-calling 智能客服下单。
 *
 * 优先于关键词状态机调用：
 *  - LLM 理解用户意图 → 调用 Java 工具执行 → 循环直到最终回复
 *  - LLM 失败/超时 → 返回 null，调用方 fallback 到 ChatOrderService 关键词状态机
 */
public interface ChatToolService {

    /**
     * 处理一条用户消息，尝试用 LLM tool calling 完成。
     *
     * @param userId 用户 id
     * @param text   用户消息原文
     * @return 处理结果（是否接管 + 机器人回复列表）；null 表示 LLM 不可用需 fallback
     */
    ChatOrderService.Result handle(Long userId, String text);

    /**
     * 该用户是否存在进行中的 tool-calling 对话。
     */
    boolean hasActiveSession(Long userId);

    /**
     * 清除用户对话历史（转人工时调用）。
     */
    void clearSession(Long userId);
}

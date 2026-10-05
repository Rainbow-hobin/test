package com.sky.service;

/**
 * 智能客服机器人服务
 */
public interface BotService {
    /**
     * 向 RAG 机器人提问。
     *
     * @param question 用户问题
     * @return 机器人回答；如果知识库不足以回答或调用失败，返回 null（应转人工）
     */
    String askBot(String question);
}

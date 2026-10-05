package com.sky.service.impl;

import com.sky.service.BotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class BotServiceImpl implements BotService {

    private static final String RAG_URL = "http://127.0.0.1:8000/api/ask";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    //LLM 生成回答耗时长（10~30秒），读超时需要放宽
    private static final int READ_TIMEOUT_MS = 60000;

    private final RestTemplate restTemplate;

    @Autowired
    public BotServiceImpl(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS))
                .setReadTimeout(Duration.ofMillis(READ_TIMEOUT_MS))
                .build();
    }

    @Override
    public String askBot(String question) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("question", question);
            req.put("top_k", 3);

            ResponseEntity<Map> resp = restTemplate.postForEntity(RAG_URL, req, Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("RAG 服务返回异常状态: {}", resp.getStatusCode());
                return null;
            }

            Map<String, Object> body = resp.getBody();
            Object answerObj = body.get("answer");
            if (answerObj == null) {
                return null;
            }
            String answer = answerObj.toString().trim();
            if (answer.isEmpty()) {
                return null;
            }

            // Prompt 中明确要求知识不足时回复「这个问题我需要转人工处理」
            if (answer.contains("转人工")) {
                log.info("RAG 无法回答，需转人工: {}", question);
                return null;
            }
            return answer;
        } catch (ResourceAccessException e) {
            log.warn("RAG 服务不可达: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("调用 RAG 服务异常", e);
            return null;
        }
    }
}

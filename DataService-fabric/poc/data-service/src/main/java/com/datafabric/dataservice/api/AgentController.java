package com.datafabric.dataservice.api;

import com.datafabric.dataservice.agent.CustomerInsightAgent;
import com.datafabric.dataservice.agent.RawDbAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * W3 AI Agent 对比端点
 *
 *   POST /api/v1/agent/insight  → 走 Data Fabric 路径（治理全开）
 *   POST /api/v1/agent/raw      → 走直查表路径（对照组，无治理）
 *
 * 演示对比：同一问题、两种路径、不同治理结果。
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private static final long DEFAULT_TIMEOUT_MS = 60_000L;

    private final CustomerInsightAgent insightAgent;
    private final RawDbAgent rawDbAgent;

    public AgentController(CustomerInsightAgent insightAgent, RawDbAgent rawDbAgent) {
        this.insightAgent = insightAgent;
        this.rawDbAgent = rawDbAgent;
    }

    @PostMapping("/insight")
    public Map<String, Object> insight(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "").trim();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        log.info("Agent[insight] Q='{}'", question);
        long t0 = System.currentTimeMillis();
        try {
            String answer = insightAgent.answer(question);
            long elapsed = System.currentTimeMillis() - t0;
            return Map.of(
                    "path", "fabric",
                    "question", question,
                    "answer", answer,
                    "elapsedMs", elapsed);
        } catch (Exception e) {
            log.error("Agent[insight] failed", e);
            return Map.of(
                    "path", "fabric",
                    "question", question,
                    "error", e.getClass().getSimpleName(),
                    "elapsedMs", System.currentTimeMillis() - t0);
        }
    }

    @PostMapping("/raw")
    public Map<String, Object> raw(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "").trim();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        log.info("Agent[raw] Q='{}'", question);
        long t0 = System.currentTimeMillis();
        try {
            String answer = rawDbAgent.answer(question);
            long elapsed = System.currentTimeMillis() - t0;
            return Map.of(
                    "path", "raw",
                    "question", question,
                    "answer", answer,
                    "elapsedMs", elapsed);
        } catch (Exception e) {
            log.error("Agent[raw] failed", e);
            return Map.of(
                    "path", "raw",
                    "question", question,
                    "error", e.getClass().getSimpleName(),
                    "elapsedMs", System.currentTimeMillis() - t0);
        }
    }
}

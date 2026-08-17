package com.datafabric.dataservice.api;

import com.datafabric.dataservice.agent.CustomerInsightAgent;
import com.datafabric.dataservice.agent.CustomerInsightStreamAgent;
import com.datafabric.dataservice.agent.RawDbAgent;
import com.datafabric.dataservice.agent.RawDbStreamAgent;
import dev.langchain4j.service.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * W3 AI Agent 对比端点
 *
 *   POST /api/v1/agent/insight  → 走 Data Fabric 路径（治理全开）
 *   POST /api/v1/agent/raw      → 走直查表路径（对照组，无治理）
 *
 * F5 流式：
 *   POST /api/v1/agent/insight/stream  → SseEmitter，逐 token 推送
 *   POST /api/v1/agent/raw/stream      → SseEmitter（对照）
 *
 * SSE 事件 JSON：
 *   {"type":"token","content":"..."}   部分 token
 *   {"type":"done","elapsedMs":1234}   正常完成
 *   {"type":"error","message":"..."}   异常
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private static final long DEFAULT_TIMEOUT_MS = 60_000L;

    private final CustomerInsightAgent insightAgent;
    private final RawDbAgent rawDbAgent;
    private final CustomerInsightStreamAgent insightStreamAgent;
    private final RawDbStreamAgent rawStreamAgent;

    public AgentController(
            CustomerInsightAgent insightAgent,
            RawDbAgent rawDbAgent,
            CustomerInsightStreamAgent insightStreamAgent,
            RawDbStreamAgent rawStreamAgent) {
        this.insightAgent = insightAgent;
        this.rawDbAgent = rawDbAgent;
        this.insightStreamAgent = insightStreamAgent;
        this.rawStreamAgent = rawStreamAgent;
    }

    @PostMapping("/insight")
    public Map<String, Object> insight(@RequestBody Map<String, String> body) {
        String question = requireQuestion(body);
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
        String question = requireQuestion(body);
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

    /**
     * F5 流式端点（A 路治理）。
     * 返回 SseEmitter，Spring MVC 把回调里的 send 转成 SSE 帧。
     */
    @PostMapping(value = "/insight/stream")
    public SseEmitter insightStream(@RequestBody Map<String, String> body) {
        String question = requireQuestion(body);
        log.info("Agent[insight/stream] Q='{}'", question);
        return startStream(insightStreamAgent.answer(question), "fabric", question);
    }

    /**
     * F5 流式端点（B 路对照）。
     */
    @PostMapping(value = "/raw/stream")
    public SseEmitter rawStream(@RequestBody Map<String, String> body) {
        String question = requireQuestion(body);
        log.info("Agent[raw/stream] Q='{}'", question);
        return startStream(rawStreamAgent.answer(question), "raw", question);
    }

    private static String requireQuestion(Map<String, String> body) {
        String q = body.getOrDefault("question", "").trim();
        if (q.isEmpty()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        return q;
    }

    /**
     * 把 LangChain4j {@link TokenStream} 桥接到 Spring MVC {@link SseEmitter}。
     *
     * onPartialResponse → 一条 {@code token} 事件；onCompleteResponse → {@code done}；
     * onError → {@code error} 然后关闭。客户端 disconnection 通过 send 抛 IOException 探测。
     */
    private SseEmitter startStream(TokenStream stream, String path, String question) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        final long t0 = System.currentTimeMillis();
        final String tag = "Agent[" + path + "/stream]";

        emitter.onTimeout(() -> log.warn("{} timeout", tag));
        emitter.onError(ex -> log.warn("{} client disconnected: {}", tag, ex.toString()));

        stream
            .onPartialResponse(token -> {
                try {
                    emitter.send(SseEmitter.event().data(Map.of("type", "token", "content", token)));
                } catch (IOException e) {
                    log.warn("{} client gone during token push", tag);
                    emitter.completeWithError(e);
                }
            })
            .onCompleteResponse(chatResponse -> {
                long elapsed = System.currentTimeMillis() - t0;
                try {
                    emitter.send(SseEmitter.event().data(Map.of("type", "done", "elapsedMs", elapsed)));
                    emitter.complete();
                    log.info("{} done in {}ms", tag, elapsed);
                } catch (IOException e) {
                    log.warn("{} client gone on completion", tag);
                    emitter.completeWithError(e);
                }
            })
            .onError(err -> {
                log.error("{} failed", tag, err);
                try {
                    emitter.send(SseEmitter.event().data(Map.of(
                            "type", "error",
                            "message", err.getClass().getSimpleName() + ": " + err.getMessage(),
                            "elapsedMs", System.currentTimeMillis() - t0)));
                } catch (IOException ignored) {
                    // client already gone
                }
                emitter.completeWithError(err);
            })
            .start();

        return emitter;
    }
}

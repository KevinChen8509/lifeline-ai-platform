package com.datafabric.dataservice.api;

import com.datafabric.dataservice.agent.CustomerInsightAgent;
import com.datafabric.dataservice.agent.CustomerInsightStreamAgent;
import com.datafabric.dataservice.agent.RawDbAgent;
import com.datafabric.dataservice.agent.RawDbStreamAgent;
import com.datafabric.dataservice.config.LlmProperties;
import com.datafabric.dataservice.governance.TraceContext;
import com.datafabric.dataservice.governance.TraceEvent;
import com.datafabric.dataservice.governance.TraceStore;
import com.datafabric.dataservice.observability.TokenUsageStore;
import com.datafabric.dataservice.observability.UsageContext;
import dev.langchain4j.service.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
 * F9 追溯：每个请求分配 requestId，响应/SSE done 事件回传；
 *   GET /api/v1/agent/trace/{requestId} 查询完整治理链路。
 *
 * SSE 事件 JSON：
 *   {"type":"token","content":"..."}        部分 token
 *   {"type":"done","elapsedMs":1234,"requestId":"..."}   正常完成
 *   {"type":"error","message":"..."}        异常
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
    private final TraceStore traceStore;
    private final TokenUsageStore tokenUsageStore;
    private final LlmProperties llmProperties;

    public AgentController(
            CustomerInsightAgent insightAgent,
            RawDbAgent rawDbAgent,
            CustomerInsightStreamAgent insightStreamAgent,
            RawDbStreamAgent rawStreamAgent,
            TraceStore traceStore,
            TokenUsageStore tokenUsageStore,
            LlmProperties llmProperties) {
        this.insightAgent = insightAgent;
        this.rawDbAgent = rawDbAgent;
        this.insightStreamAgent = insightStreamAgent;
        this.rawStreamAgent = rawStreamAgent;
        this.traceStore = traceStore;
        this.tokenUsageStore = tokenUsageStore;
        this.llmProperties = llmProperties;
    }

    @PostMapping("/insight")
    public Map<String, Object> insight(@RequestBody Map<String, String> body) {
        String question = requireQuestion(body);
        String requestId = newRequestId("fabric", question);
        log.info("Agent[insight] requestId={} Q='{}'", requestId, question);
        TraceContext.set(requestId);
        UsageContext.set("fabric", requestId);
        long t0 = System.currentTimeMillis();
        try {
            String answer = insightAgent.answer(question);
            long elapsed = System.currentTimeMillis() - t0;
            finishTrace(requestId, "SUCCESS", elapsed);
            return Map.of(
                    "path", "fabric",
                    "requestId", requestId,
                    "question", question,
                    // LLM 可能返回 null（如全部工具失败后空补全），Map.of 不容忍 null
                    "answer", Objects.requireNonNullElse(answer, ""),
                    "elapsedMs", elapsed);
        } catch (Exception e) {
            log.error("Agent[insight] failed", e);
            long elapsed = System.currentTimeMillis() - t0;
            finishTrace(requestId, "FAILED", elapsed);
            return Map.of(
                    "path", "fabric",
                    "requestId", requestId,
                    "question", question,
                    "error", e.getClass().getSimpleName(),
                    "elapsedMs", elapsed);
        } finally {
            TraceContext.clear();
            UsageContext.clear();
        }
    }

    @PostMapping("/raw")
    public Map<String, Object> raw(@RequestBody Map<String, String> body) {
        String question = requireQuestion(body);
        String requestId = newRequestId("raw", question);
        log.info("Agent[raw] requestId={} Q='{}'", requestId, question);
        UsageContext.set("raw", requestId);
        long t0 = System.currentTimeMillis();
        try {
            String answer = rawDbAgent.answer(question);
            long elapsed = System.currentTimeMillis() - t0;
            finishTrace(requestId, "SUCCESS", elapsed);
            return Map.of(
                    "path", "raw",
                    "requestId", requestId,
                    "question", question,
                    "answer", Objects.requireNonNullElse(answer, ""),
                    "elapsedMs", elapsed);
        } catch (Exception e) {
            log.error("Agent[raw] failed", e);
            long elapsed = System.currentTimeMillis() - t0;
            finishTrace(requestId, "FAILED", elapsed);
            return Map.of(
                    "path", "raw",
                    "requestId", requestId,
                    "question", question,
                    "error", e.getClass().getSimpleName(),
                    "elapsedMs", elapsed);
        } finally {
            UsageContext.clear();
        }
    }

    /**
     * F5 流式端点（A 路治理）。
     * 返回 SseEmitter，Spring MVC 把回调里的 send 转成 SSE 帧。
     * F9：request 级 trace（工具级 ThreadLocal 在流式回调线程不可见，见 TraceContext）。
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

    /**
     * F6 Token 用量与成本快照：全局 + 分路径聚合 + 估算成本 + 最近调用。
     */
    @GetMapping("/usage")
    public Map<String, Object> usage() {
        return tokenUsageStore.snapshot(llmProperties);
    }

    private static String requireQuestion(Map<String, String> body) {
        String q = body.getOrDefault("question", "").trim();
        if (q.isEmpty()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        return q;
    }

    private String newRequestId(String path, String question) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        traceStore.start(requestId, path, question);
        return requestId;
    }

    private void finishTrace(String requestId, String status, long elapsedMs) {
        traceStore.add(requestId, TraceEvent.of("REQUEST", Map.of(
                "status", status,
                "elapsedMs", elapsedMs)));
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
        final String requestId = newRequestId(path, question);

        emitter.onTimeout(() -> {
            log.warn("{} timeout", tag);
            finishTrace(requestId, "TIMEOUT", System.currentTimeMillis() - t0);
        });
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
                finishTrace(requestId, "SUCCESS", elapsed);
                // F6: 流式路径用量在此记录（同步路径走 SyncTokenListener，互不双计）
                tokenUsageStore.record(path, requestId, chatResponse.tokenUsage());
                try {
                    emitter.send(SseEmitter.event().data(Map.of(
                            "type", "done", "elapsedMs", elapsed, "requestId", requestId)));
                    emitter.complete();
                    log.info("{} done in {}ms", tag, elapsed);
                } catch (IOException e) {
                    log.warn("{} client gone on completion", tag);
                    emitter.completeWithError(e);
                }
            })
            .onError(err -> {
                log.error("{} failed", tag, err);
                finishTrace(requestId, "FAILED", System.currentTimeMillis() - t0);
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

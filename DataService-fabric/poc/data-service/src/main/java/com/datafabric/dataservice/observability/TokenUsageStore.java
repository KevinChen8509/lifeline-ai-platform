package com.datafabric.dataservice.observability;

import com.datafabric.dataservice.config.LlmProperties;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * F6 Token 用量与成本估算存储
 *
 * 记录粒度 = 一次 LLM 调用（工具循环的每个往返各记一条，requestId 相同），
 * 聚合维度 = 全局 + 分路径（fabric / raw）。
 *
 * 成本是估算值：单价可配置（datafabric.llm.pricing），按百万 token 计。
 */
@Component
public class TokenUsageStore {

    public record UsageRecord(
            Instant timestamp,
            String path,
            String requestId,
            long inputTokens,
            long outputTokens,
            long totalTokens) {}

    private record Aggregate(long llmCalls, long inputTokens, long outputTokens, long totalTokens) {

        static Aggregate zero() {
            return new Aggregate(0, 0, 0, 0);
        }

        Aggregate plus(UsageRecord r) {
            return new Aggregate(llmCalls + 1, inputTokens + r.inputTokens(),
                    outputTokens + r.outputTokens(), totalTokens + r.totalTokens());
        }

        Map<String, Object> toMap() {
            return Map.of(
                    "llmCalls", llmCalls,
                    "inputTokens", inputTokens,
                    "outputTokens", outputTokens,
                    "totalTokens", totalTokens);
        }
    }

    private static final int MAX_RECENT = 100;
    private static final String UNKNOWN_PATH = "unknown";

    private final ConcurrentMap<String, Aggregate> byPath = new ConcurrentHashMap<>();
    private final Deque<UsageRecord> recent = new ArrayDeque<>();

    /**
     * 记一次 LLM 调用。usage 为 null（模型未回 usage 字段）时按 0 计但调用数照加，
     * 保证调用次数可见。
     */
    public synchronized void record(String path, String requestId, TokenUsage usage) {
        String p = path == null ? UNKNOWN_PATH : path;
        UsageRecord r = new UsageRecord(
                Instant.now(), p, requestId,
                usage == null || usage.inputTokenCount() == null ? 0 : usage.inputTokenCount(),
                usage == null || usage.outputTokenCount() == null ? 0 : usage.outputTokenCount(),
                usage == null || usage.totalTokenCount() == null ? 0 : usage.totalTokenCount());

        Aggregate delta = new Aggregate(1, r.inputTokens(), r.outputTokens(), r.totalTokens());
        byPath.merge(p, delta, (a, b) -> new Aggregate(
                a.llmCalls() + b.llmCalls(),
                a.inputTokens() + b.inputTokens(),
                a.outputTokens() + b.outputTokens(),
                a.totalTokens() + b.totalTokens()));
        recent.addLast(r);
        while (recent.size() > MAX_RECENT) {
            recent.removeFirst();
        }
    }

    /** 聚合快照：全局 + 分路径 + 成本估算 + 最近记录。 */
    public synchronized Map<String, Object> snapshot(LlmProperties pricing) {
        Aggregate total = byPath.values().stream()
                .reduce(Aggregate.zero(), (a, b) -> new Aggregate(
                        a.llmCalls() + b.llmCalls(),
                        a.inputTokens() + b.inputTokens(),
                        a.outputTokens() + b.outputTokens(),
                        a.totalTokens() + b.totalTokens()));

        Map<String, Object> byPathMap = new LinkedHashMap<>();
        byPath.forEach((path, agg) -> byPathMap.put(path, agg.toMap()));

        double inputPrice = pricing.inputPricePerMillion();
        double outputPrice = pricing.outputPricePerMillion();
        double inputCost = total.inputTokens() * inputPrice / 1_000_000d;
        double outputCost = total.outputTokens() * outputPrice / 1_000_000d;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totals", total.toMap());
        out.put("byPath", byPathMap);
        out.put("costEstimateCny", Map.of(
                "input", round6(inputCost),
                "output", round6(outputCost),
                "total", round6(inputCost + outputCost)));
        out.put("pricing", Map.of(
                "inputPerMillion", inputPrice,
                "outputPerMillion", outputPrice,
                "note", "估算值，单价可配 datafabric.llm.*-price-per-million"));
        out.put("recent", new ArrayList<>(recent));
        return out;
    }

    public synchronized int recentCount() {
        return recent.size();
    }

    private static double round6(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }
}

package com.datafabric.dataservice.observability;

/**
 * F6 用量上下文：同步 Agent 调用线程上标注（path, requestId），
 * 供 {@link SyncTokenListener} 在同线程回调时读取关联信息。
 *
 * 与 {@code TraceContext} 分开的原因：raw 路径没有 TraceContext，
 * 且这里还需要 path 维度做分路统计。
 *
 * 流式不适用：LLM 回调跑在 HTTP client 线程，ThreadLocal 不可见
 * （流式用量由控制器在 onCompleteResponse 里直接记录）。
 */
public final class UsageContext {

    public record Call(String path, String requestId) {}

    private static final ThreadLocal<Call> CURRENT = new ThreadLocal<>();

    private UsageContext() {
    }

    public static void set(String path, String requestId) {
        CURRENT.set(new Call(path, requestId));
    }

    public static String currentPath() {
        Call call = CURRENT.get();
        return call == null ? null : call.path();
    }

    public static String currentRequestId() {
        Call call = CURRENT.get();
        return call == null ? null : call.requestId();
    }

    public static void clear() {
        CURRENT.remove();
    }
}

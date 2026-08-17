package com.datafabric.dataservice.governance;

/**
 * F9 requestId ThreadLocal 传递
 *
 * AgentController（入口）set → CustomerInsightTools（同线程执行 @Tool）get，
 * 用于给工具自调 HTTP 附加 X-Request-Id 头。
 *
 * 覆盖范围：同步端点（insight/raw）。LLM 工具在 servlet 线程内联执行，ThreadLocal 可见。
 * 流式端点的工具回调跑在 HTTP client 线程，ThreadLocal 不可见 —— 流式只记录
 * REQUEST 级事件（已在 AgentController 内直接写 TraceStore）。
 *
 * 请求结束必须 clear（防线程池复用泄漏）。
 */
public final class TraceContext {

    public static final String HEADER = "X-Request-Id";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void set(String requestId) {
        CURRENT.set(requestId);
    }

    /** 当前 Agent 请求的 requestId；非 Agent 上下文返回 null */
    public static String currentRequestId() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}

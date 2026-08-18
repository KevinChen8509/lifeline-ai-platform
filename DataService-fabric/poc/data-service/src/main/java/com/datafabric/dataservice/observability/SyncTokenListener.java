package com.datafabric.dataservice.observability;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * F6 同步模型用量监听器：ChatModelListener.onResponse 与 agent.answer()
 * 同线程执行，因此能读到 {@link UsageContext} 标注的 (path, requestId)。
 *
 * 只注册在同步 ChatModel 上：
 *   - 同步路径：本监听器负责记录（answer() 不回传 tokenUsage）
 *   - 流式路径：回调在 HTTP client 线程读不到 ThreadLocal，且 TokenStream
 *     的 onCompleteResponse 已带 ChatResponse —— 由控制器直接记录，
 *     此处不注册避免双计
 */
public class SyncTokenListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(SyncTokenListener.class);

    private final TokenUsageStore store;

    public SyncTokenListener(TokenUsageStore store) {
        this.store = store;
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        String path = UsageContext.currentPath();
        String requestId = UsageContext.currentRequestId();
        if (path == null) {
            // 同步 Agent 请求之外触发的 LLM 调用（PoC 理论上没有），仍计数归 unknown
            log.debug("LLM 调用无 UsageContext，归入 unknown");
        }
        store.record(path, requestId, context.chatResponse().tokenUsage());
    }
}

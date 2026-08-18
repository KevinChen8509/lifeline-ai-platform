package com.datafabric.dataservice.observability;

import com.datafabric.dataservice.config.LlmProperties;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F6 SyncTokenListener：onResponse 在同线程读到 UsageContext 并入账
 */
class SyncTokenListenerTest {

    private static final LlmProperties PRICING = new LlmProperties(
            "http://localhost", "test-key", "test-model", null, null, null, null, null);

    @AfterEach
    void tearDown() {
        UsageContext.clear();
    }

    @Test
    void onResponse_withContext_recordsPathAndRequestId() {
        TokenUsageStore store = new TokenUsageStore();
        SyncTokenListener listener = new SyncTokenListener(store);

        UsageContext.set("fabric", "req-42");
        listener.onResponse(responseWithUsage(120, 80, 200));

        List<TokenUsageStore.UsageRecord> recent = recent(store);
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).path()).isEqualTo("fabric");
        assertThat(recent.get(0).requestId()).isEqualTo("req-42");
        assertThat(recent.get(0).inputTokens()).isEqualTo(120);
        assertThat(recent.get(0).outputTokens()).isEqualTo(80);
    }

    @Test
    void onResponse_withoutContext_recordsAsUnknown() {
        TokenUsageStore store = new TokenUsageStore();
        SyncTokenListener listener = new SyncTokenListener(store);

        listener.onResponse(responseWithUsage(10, 5, 15));

        List<TokenUsageStore.UsageRecord> recent = recent(store);
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).path()).isEqualTo("unknown");
        assertThat(recent.get(0).requestId()).isNull();
    }

    private static ChatModelResponseContext responseWithUsage(int in, int out, int total) {
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(in, out, total))
                        .build())
                .build();
        return new ChatModelResponseContext(
                response,
                ChatRequest.builder().messages(UserMessage.from("hi")).build(),
                ModelProvider.OPEN_AI, Map.of());
    }

    @SuppressWarnings("unchecked")
    private static List<TokenUsageStore.UsageRecord> recent(TokenUsageStore store) {
        return (List<TokenUsageStore.UsageRecord>) store.snapshot(PRICING).get("recent");
    }
}

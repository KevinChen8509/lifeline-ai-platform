package com.datafabric.dataservice.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 包装 {@link DefaultToolExecutor}，对 LLM 返回的 {@code arguments} 做归一化。
 *
 * <p>背景：GLM-4.7（火山方舟 Ark API）的 OpenAI 兼容层对<b>无参 @Tool</b>
 * 偶尔只发半个 {@code "{"}（被截断的 JSON）或 {@code ""}，导致 LangChain4j
 * 0.36.2 的 {@code ToolExecutionRequestUtil.argumentsAsMap} 直接抛
 * {@link com.google.gson.JsonSyntaxException}（"End of input at line 1 column 2"）。
 *
 * <p>本 wrapper 在委托前把异常 args 归一化为 {@code "{}"}，让 DefaultToolExecutor
 * 继续走 reflect 调用（无参方法直接命中）。
 *
 * <p>长期：升级到 LangChain4j 1.0.0 stable（已发布）后该 case 已修复，
 * 本类可移除。
 */
public final class SanitizingToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(SanitizingToolExecutor.class);

    private final DefaultToolExecutor delegate;

    public SanitizingToolExecutor(Object bean, Method method) {
        this.delegate = new DefaultToolExecutor(bean, method);
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        String args = request.arguments();
        String normalized = normalize(args);
        if (!Objects.equals(args, normalized)) {
            log.warn("Tool[{}] args 残缺 '{}'，归一化为 '{}'", request.name(), args, normalized);
            request = ToolExecutionRequest.builder()
                    .id(request.id())
                    .name(request.name())
                    .arguments(normalized)
                    .build();
        }
        return delegate.execute(request, memoryId);
    }

    /**
     * 把 LLM 偶尔返回的残缺 arguments 归一化为合法 JSON。
     *
     * 视为残缺：
     *   - null 或空白
     *   - 不是以 {@code '{'} 开头（说明不是 JSON 对象字面量）
     *   - 等于 {@code "{"}（GLM-4.7 偶发，本来应该是 {@code "{}"})
     */
    static String normalize(String args) {
        if (args == null || args.isBlank()) return "{}";
        String trimmed = args.trim();
        if (trimmed.isEmpty() || trimmed.equals("{")) return "{}";
        if (!trimmed.startsWith("{")) return "{}";
        return args;
    }
}

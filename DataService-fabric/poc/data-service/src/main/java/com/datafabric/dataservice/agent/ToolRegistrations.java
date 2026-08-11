package com.datafabric.dataservice.agent;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.ToolExecutor;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * W3 工具注册辅助
 *
 * <p>把 Spring 容器里的工具 bean（含 {@link Tool} 注解方法的 {@code @Component}）
 * 转成 LangChain4j {@code AiServices.builder().tools(Map)} 所需的
 * {@code Map<ToolSpecification, ToolExecutor>}，全部走 {@link SanitizingToolExecutor}
 * 包装，统一兜底 GLM-4.7 的残缺 arguments。
 */
public final class ToolRegistrations {

    private ToolRegistrations() {}

    /**
     * 扫描 bean 中所有 {@code @Tool} 方法，构造 spec→executor 映射。
     *
     * @param bean Spring 容器里的工具 bean（如 {@link CustomerInsightTools} / {@link RawDbTools}）
     * @return 有序 map；迭代顺序 = 方法声明顺序
     */
    public static Map<ToolSpecification, ToolExecutor> buildToolMap(Object bean) {
        Map<ToolSpecification, ToolExecutor> map = new LinkedHashMap<>();
        for (Method m : bean.getClass().getDeclaredMethods()) {
            if (m.isAnnotationPresent(Tool.class)) {
                ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(m);
                ToolExecutor exec = new SanitizingToolExecutor(bean, m);
                map.put(spec, exec);
            }
        }
        return map;
    }
}

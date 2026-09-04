package com.datafabric.dataservice.service;

import com.datafabric.dataservice.service.ServiceRegistryStore.DayCount;
import com.datafabric.dataservice.service.ServiceRegistryStore.UsageSummary;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单元测试用注册表存储替身：内存 Map 语义（同 slug 覆盖 / usage 按 day 累计），
 * 用于 ServiceRegistry 的生命周期/限流用例（不引入 JDBC 依赖）。
 */
class InMemoryRegistryStore implements ServiceRegistryStore {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Map<String, ServiceDefinition> defs = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AtomicLong>> usageBySlug = new ConcurrentHashMap<>();

    @Override
    public void save(ServiceDefinition def) {
        defs.put(def.slug(), def);
    }

    @Override
    public void delete(String slug) {
        defs.remove(slug);
    }

    @Override
    public List<ServiceDefinition> loadAll() {
        return defs.values().stream()
                .sorted(Comparator.comparing(ServiceDefinition::createdAt))
                .toList();
    }

    @Override
    public void recordUsage(String slug) {
        usageBySlug.computeIfAbsent(slug, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(LocalDate.now().format(DAY_FMT), k -> new AtomicLong())
                .incrementAndGet();
    }

    @Override
    public UsageSummary usage(String slug) {
        Map<String, AtomicLong> days = usageBySlug.getOrDefault(slug, Map.of());
        List<DayCount> recent = days.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicLong>comparingByKey().reversed())
                .limit(14)
                .map(e -> new DayCount(e.getKey(), e.getValue().get()))
                .toList();
        long total = days.values().stream().mapToLong(AtomicLong::get).sum();
        long today = days.getOrDefault(LocalDate.now().format(DAY_FMT), new AtomicLong()).get();
        return new UsageSummary(slug, total, today, recent);
    }
}

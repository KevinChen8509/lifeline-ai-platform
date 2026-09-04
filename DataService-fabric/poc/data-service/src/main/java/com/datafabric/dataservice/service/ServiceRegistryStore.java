package com.datafabric.dataservice.service;

import java.util.List;

/**
 * W6-B 注册表持久化接口：发布的服务定义（含 API Key 与策略）+ 每日用量。
 *
 * 语义：
 *   - save 幂等（同 slug 覆盖：rotate/revoke 后全量覆写）
 *   - loadAll 启动时灌回内存注册表（重启后 Key 不变的核心路径）
 *   - recordUsage 当日调用 +1（计量，失败不阻断查询）
 *
 * 降级约定（PoC）：库不可达时 save/delete/recordUsage 仅告警不抛 —— 内存注册表
 * 照常服务当次会话；loadAll 失败返回空表并 ERROR（等价"未持久化过"）。
 */
public interface ServiceRegistryStore {

    void save(ServiceDefinition def);

    void delete(String slug);

    List<ServiceDefinition> loadAll();

    void recordUsage(String slug);

    /** day 为 ISO yyyy-MM-dd 字符串（前端直渲染） */
    record DayCount(String day, long calls) {}

    record UsageSummary(String slug, long totalCalls, long todayCalls, List<DayCount> recentDays) {}

    UsageSummary usage(String slug);
}

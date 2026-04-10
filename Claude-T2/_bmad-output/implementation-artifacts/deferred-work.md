# Deferred Work

This file tracks issues deferred during code review that should be addressed in future stories.

---

## Deferred from: code review of 1-1-project-init-and-framework-setup (2026-03-27)

- **CORS 配置过于宽松** — `origin: true` 允许所有来源，应在安全加固 Story 中限制为特定域名
- **无登录限流保护** — 将在 Story 1.3 (用户登录认证) 中实现 rate limiting

## Deferred from: code review of 1-9-operation-audit-log-recording (2026-04-02)

- **AC3: cleanupOldLogs() 允许删除审计日志** — 延后到部署阶段，届时与DBA一起实现数据库级INSERT-only权限保护
- **AC4: 保留策略不可配置且无自动调度** — 记录为技术债务，在后续运维加固迭代中实现可配置保留策略（环境变量）和自动调度（@nestjs/schedule）


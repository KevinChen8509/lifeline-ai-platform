# Story 1.10: 审计日志查询

Status: review

## Story

**As a** 管理员,
**I want** 查询用户操作审计日志,
**So that** 追溯用户操作行为和排查问题.

## Acceptance Criteria

1. **AC1: 审计日志列表显示**
   - **Given** 管理员进入审计日志页面
   - **When** 页面加载
   - **Then** 系统显示审计日志列表（时间、用户、操作、对象、IP、结果）
   - **And** 按时间倒序排列
   - **And** 支持分页显示（默认每页20条）

2. **AC2: 时间范围筛选**
   - **Given** 管理员选择时间范围
   - **When** 设置开始时间和结束时间
   - **Then** 系统只显示该时间范围内的审计日志

3. **AC3: 用户筛选**
   - **Given** 管理员选择用户筛选
   - **When** 输入用户名或选择用户
   - **Then** 系统只显示该用户的操作日志

4. **AC4: 操作类型筛选**
   - **Given** 管理员选择操作类型
   - **When** 选择特定操作（如登录、创建用户、删除等）
   - **Then** 系统只显示该类型的日志

5. **AC5: 查询性能**
   - **Given** 审计日志数据量达到10万条
   - **When** 管理员执行查询
   - **Then** 查询响应时间≤3秒

6. **AC6: 导出CSV**
   - **Given** 管理员查看审计日志列表
   - **When** 点击导出按钮
   - **Then** 系统导出当前筛选结果为CSV文件
   - **And** CSV包含所有显示字段

## Tasks / Subtasks

- [x] **Task 1: 后端API - 查询接口** (AC: 1, 2, 3, 4, 5)
  - [x] 1.1 创建 AuditLogController
  - [x] 1.2 实现 GET /api/v1/audit-logs 端点
  - [x] 1.3 创建 QueryAuditLogsDto (startTime, endTime, userId, action, page, pageSize)
  - [x] 1.4 添加权限守卫（仅管理员可访问）
  - [x] 1.5 添加 Swagger 文档

- [x] **Task 2: 后端API - 导出接口** (AC: 6)
  - [x] 2.1 实现 GET /api/v1/audit-logs/export 端点
  - [x] 2.2 生成 CSV 格式响应
  - [x] 2.3 设置正确的 Content-Disposition 头
  - [x] 2.4 限制最大导出条数（10000条）

- [x] **Task 3: 前端页面** (AC: 1, 2, 3, 4, 6)
  - [x] 3.1 创建审计日志页面 views/system/audit-log/index.vue
  - [x] 3.2 实现表格显示（时间、用户、操作、对象、IP、描述）
  - [x] 3.3 实现时间范围选择器
  - [x] 3.4 实现用户筛选（可输入用户名）
  - [x] 3.5 实现操作类型下拉选择
  - [x] 3.6 实现导出按钮

- [x] **Task 4: 单元测试**
  - [x] 4.1 测试查询接口（分页、筛选）
  - [x] 4.2 测试导出接口
  - [x] 4.3 测试权限控制（非管理员不可访问）

## Dev Notes

### 已存在的实现 [Source: Story 1.6, 1.9]

**审计模块已存在:**
- `modules/audit/audit-log.entity.ts` - 审计日志实体
- `modules/audit/audit-log.service.ts` - 审计日志服务（已实现查询方法）
- `modules/audit/audit.module.ts` - 全局模块

**AuditLogService 已有方法:**
```typescript
// 查询审计日志（支持分页和筛选）
findAll(options: FindAuditLogsOptions): Promise<{ items, total, page, pageSize }>

// FindAuditLogsOptions 接口
interface FindAuditLogsOptions {
  page?: number;
  pageSize?: number;
  action?: string;
  targetType?: string;
  targetId?: string;
  operatorId?: string;
  startDate?: Date;
  endDate?: Date;
}
```

### API 端点

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/v1/audit-logs | 查询审计日志 | admin |
| GET | /api/v1/audit-logs/export | 导出CSV | admin |

### References

- [Source: epics.md#Story 1.10] - 原始Story定义
- [Source: Story 1.6] - 审计日志实体和服务
- [Source: Story 1.9] - 登录/登出审计日志集成
- [Source: NFR-P04] - 历史数据查询响应≤3秒
- [Source: NFR-S05] - 审计日志保留≥6个月

---

**Story Context Generated:** 2026-04-02
**Analysis Method:** Ultimate BMad Method Story Context Engine

## File List

**后端 (apps/api/src/):**
- `modules/audit/audit-log.controller.ts` - 新增：审计日志控制器
- `modules/audit/audit-log.controller.spec.ts` - 新增：控制器测试（9个测试用例）
- `modules/audit/dto/query-audit-logs.dto.ts` - 新增：查询DTO
- `modules/audit/dto/audit-log-response.dto.ts` - 新增：响应DTO
- `modules/audit/audit.module.ts` - 更新：添加Controller

**前端 (apps/web/src/):**
- `api/system/auditLog.ts` - 新增：审计日志API
- `views/system/audit-log/index.vue` - 新增：审计日志页面
- `router/index.ts` - 更新：添加路由

## Completion Notes

1. ✅ **Task 1: 后端API - 查询接口**
   - AuditLogController 已实现
   - 支持分页查询（page, pageSize）
   - 支持时间范围筛选（startTime, endTime）
   - 支持用户筛选
   - 支持操作类型筛选
   - 支持目标类型筛选

2. ✅ **Task 2: 后端API - 导出接口**
   - CSV 导出已实现
   - 包含 BOM 头支持中文
   - 最大导出 10000 条
   - 特殊字符正确转义

3. ✅ **Task 3: 前端页面**
   - 审计日志列表页面已创建
   - 时间范围选择器已实现
   - 用户筛选已实现
   - 操作类型下拉已实现
   - 导出按钮已实现

4. ✅ **Task 4: 单元测试**
   - 9 个测试用例全部通过
   - 覆盖查询、导出、权限控制

**测试结果:**
```
Test Suites: 7 passed, 7 total
Tests:       76 passed, 76 total
```

**实现日期:** 2026-04-02

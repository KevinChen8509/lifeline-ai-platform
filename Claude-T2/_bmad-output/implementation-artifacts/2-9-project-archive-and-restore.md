# Story 2.9: 项目归档与恢复

Status: done

## Story

**As a** 管理员,
**I want** 归档不再使用的项目或恢复已归档的项目,
**So that** 保持项目列表整洁同时保留历史数据.

## Acceptance Criteria

1. **AC1: 项目归档**
   - **Given** 管理员在项目详情页面
   - **When** 管理员点击"归档项目"
   - **Then** 项目状态变更为"已归档"

2. **AC2: 归档项目隐藏**
   - **Given** 项目已归档
   - **When** 查看项目列表
   - **Then** 归档项目不在默认列表显示（需勾选"显示归档项目"）

3. **AC3: 项目恢复**
   - **Given** 项目已归档
   - **When** 管理员点击"恢复项目"
   - **Then** 项目状态恢复为"活跃"
   - **And** 项目重新出现在默认列表

4. **AC4: 审计日志**
   - **Given** 执行归档或恢复操作
   - **When** 操作完成
   - **Then** 系统记录审计日志

## Tasks / Subtasks

- [x] **Task 1: 后端 API** (AC: 1, 3, 4)
  - [x] 1.1 添加 archive 方法到 ProjectService
  - [x] 1.2 添加 restore 方法到 ProjectService
  - [x] 1.3 添加 PUT /projects/:id/archive 端点
  - [x] 1.4 添加 PUT /projects/:id/restore 端点
  - [x] 1.5 审计日志记录

- [x] **Task 2: 前端实现** (AC: 1, 2, 3)
  - [x] 2.1 添加 restoreProject API 函数
  - [x] 2.2 项目列表添加恢复按钮
  - [x] 2.3 项目详情页添加恢复按钮
  - [x] 2.4 状态筛选支持归档/活跃

## Dev Notes

### API 设计

```http
PUT /api/projects/:id/archive
Response: { id, name, code, status: 'archived', ... }

PUT /api/projects/:id/restore
Response: { id, name, code, status: 'active', ... }
```

### 状态枚举

```typescript
enum ProjectStatus {
  ACTIVE = 'active',
  ARCHIVED = 'archived'
}
```

### References

- [Source: epics.md#Story 2.9] - 原始Story定义
- [Source: FR3] - 状态管理功能需求

---

**Story Context Generated:** 2026-04-07

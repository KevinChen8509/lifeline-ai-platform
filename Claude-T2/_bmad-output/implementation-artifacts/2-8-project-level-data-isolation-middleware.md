# Story 2.8: 项目级数据隔离中间件

Status: done

## Story

**As a** 系统,
**I want** 自动按项目隔离数据访问,
**So that** 用户只能看到自己有权限的项目数据.

## Acceptance Criteria

1. **AC1: 项目访问守卫**
   - **Given** 用户请求项目相关数据
   - **When** 请求经过守卫
   - **Then** 系统验证用户是项目成员

2. **AC2: 管理员绕过**
   - **Given** 管理员用户
   - **When** 请求任何项目数据
   - **Then** 允许访问所有项目

3. **AC3: 跨项目访问拒绝**
   - **Given** 用户尝试访问无权限项目
   - **When** 请求经过守卫
   - **Then** 返回403错误

## Tasks / Subtasks

- [ ] **Task 1: 创建项目访问守卫** (AC: 1, 2, 3)
  - [ ] 1.1 创建 ProjectAccessGuard
  - [ ] 1.2 实现项目成员验证
  - [ ] 1.3 管理员绕过逻辑

- [ ] **Task 2: 创建装饰器** (AC: 1)
  - [ ] 2.1 @RequireProjectAccess 装饰器

## Dev Notes

### 实现方案

由于 TypeORM 的 `@BeforeFind()` 监听器在 NestJS 中使用较为复杂，采用更简单的守卫方案：

1. **ProjectAccessGuard**: 检查用户是否有权访问特定项目
2. **@RequireProjectAccess 装饰器**: 标记需要项目访问检查的端点
3. **管理员绕过**: 检查用户是否有 manage:all 权限

### 代码示例

```typescript
// 使用示例
@Get('projects/:projectId/devices')
@UseGuards(ProjectAccessGuard)
async getProjectDevices(@Param('projectId') projectId: string) {
  return this.deviceService.findByProject(projectId);
}
```

### 排除接口

以下接口不需要项目隔离:
- `/api/auth/*` - 认证接口
- `/api/users/*` - 用户管理（管理员专用）
- `/api/roles/*` - 角色管理（管理员专用）

### References

- [Source: epics.md#Story 2.8] - 原始Story定义
- [Source: FR5] - 项目数据隔离功能需求

---

**Story Context Generated:** 2026-04-07

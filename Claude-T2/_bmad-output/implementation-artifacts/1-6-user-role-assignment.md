# Story 1.6: 用户角色分配

Status: ready-for-dev

## Story

**As a** 管理员,
**I want** 为用户分配角色,
**So that** 用户获得相应的操作权限.

## Acceptance Criteria

1. **AC1: 角色分配API**
   - **Given** 管理员已登录且有权限管理用户
   - **When** 调用 `PUT /api/v1/users/{userId}/role` 并提供角色ID
   - **Then** 系统更新用户的角色信息
   - **And** 返回更新后的用户信息

2. **AC2: 权限验证**
   - **Given** 非管理员用户尝试修改角色
   - **When** 调用角色分配API
   - **Then** 系统返回403 Forbidden

3. **AC3: 禁止自修改**
   - **Given** 管理员尝试修改自己的角色
   - **When** 调用角色分配API
   - **Then** 系统返回400 Bad Request
   - **And** 错误消息为"不能修改自己的角色"

4. **AC4: 角色有效性验证**
   - **Given** 管理员分配角色
   - **When** 提供的角色ID不存在
   - **Then** 系统返回400 Bad Request

5. **AC5: 新角色立即生效**
   - **Given** 用户角色被修改
   - **When** 用户下次请求API
   - **Then** 用户使用新角色的权限

6. **AC6: 审计日志记录**
   - **Given** 用户角色被修改
   - **When** 角色变更完成
   - **Then** 系统记录审计日志
   - **And** 日志包含操作者、目标用户、旧角色、新角色

## Tasks / Subtasks

- [x] **Task 1: 后端API实现** (AC: 1, 2, 3, 4)
  - [x] 1.1 创建 UpdateUserRoleDto
  - [x] 1.2 在 UserController 添加 PUT /users/:id/role 端点
  - [x] 1.3 在 UserService 实现 updateRole 方法
  - [x] 1.4 添加自修改检查（禁止修改自己的角色）
  - [x] 1.5 验证角色ID有效性

- [x] **Task 2: 权限控制** (AC: 2)
  - [x] 2.1 确认 UserController 已有权限守卫
  - [x] 2.2 确认需要 manage:User 权限

- [x] **Task 3: 审计日志** (AC: 6)
  - [x] 3.1 创建 AuditLog 实体
  - [x] 3.2 创建 AuditLogService
  - [x] 3.3 在 updateRole 方法中记录审计日志

- [x] **Task 4: 前端实现** (AC: 1)
  - [x] 4.1 创建角色分配对话框组件
  - [x] 4.2 添加用户API的 updateRole 方法
  - [x] 4.3 在用户列表页添加"分配角色"操作

- [x] **Task 5: 单元测试** (AC: All)
  - [x] 5.1 测试 updateRole 成功场景
  - [x] 5.2 测试自修改拒绝场景
  - [x] 5.3 测试无效角色ID场景
  - [x] 5.4 测试用户不存在场景

## Dev Notes

### 前置依赖 [Source: Story 1.5]

**已完成的实现:**
- UserController 已有权限守卫 (@RequirePermissions('manage', 'User'))
- UserService 已有基础CRUD方法
- RoleService 已有 findOne 方法
- Role 实体包含 id, name, code 字段
- User 实体包含 roleId 外键

### API 设计

```typescript
// PUT /api/v1/users/:id/role
// Request
{
  "roleId": "uuid-of-role"
}

// Response
{
  "id": "user-uuid",
  "username": "testuser",
  "name": "Test User",
  "email": "test@example.com",
  "status": "ACTIVE",
  "roleId": "uuid-of-role",
  "role": {
    "id": "uuid-of-role",
    "name": "运维员",
    "code": "operator"
  }
}
```

### 审计日志格式

```typescript
{
  action: 'ASSIGN_ROLE',
  targetType: 'User',
  targetId: userId,
  operatorId: currentUserId,
  oldValue: { roleId: 'old-role-uuid', roleName: '观察员' },
  newValue: { roleId: 'new-role-uuid', roleName: '运维员' },
  createdAt: new Date()
}
```

### 错误码

| 错误码 | HTTP状态 | 说明 |
|--------|----------|------|
| CANNOT_MODIFY_SELF | 400 | 不能修改自己的角色 |
| ROLE_NOT_FOUND | 400 | 角色不存在 |
| FORBIDDEN | 403 | 没有权限 |

### 文件结构

**后端新增/修改文件:**
```
apps/api/src/
├── modules/user/
│   ├── dto/
│   │   └── update-user-role.dto.ts  # 新增：角色更新DTO
│   ├── user.controller.ts           # 更新：添加角色分配端点
│   └── user.service.ts              # 更新：添加updateRole方法
├── modules/audit/
│   ├── audit-log.entity.ts          # 新增：审计日志实体
│   ├── audit-log.service.ts         # 新增：审计日志服务
│   └── audit.module.ts              # 新增：审计模块
```

**前端新增/修改文件:**
```
apps/web/src/
├── api/user.ts                      # 更新：添加updateRole方法
├── views/system/users/
│   └── index.vue                    # 更新：添加角色分配功能
```

### References

- [Source: epics.md#Story 1.6] - 原始Story定义
- [Source: Story 1.5] - 权限系统实现
- [Source: architecture.md#Authentication & Security] - RBAC权限模型

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6-20250528)

### Debug Log References

无重大错误。

### Completion Notes List

1. ✅ **后端 API 实现**
   - 创建 UpdateUserRoleDto
   - 添加 PUT /users/:id/role 端点
   - 实现 updateRole 方法
   - 添加自修改检查（禁止修改自己的角色）
   - 验证角色ID有效性

2. ✅ **权限控制**
   - UserController 已有 @RequirePermissions('manage', 'User') 守卫

3. ✅ **审计日志**
   - 创建 AuditLog 实体（含 AuditAction, AuditTargetType 枚举）
   - 创建 AuditLogService（createLog, findAll, findByTarget 等方法）
   - 创建 AuditModule（全局模块）
   - 在 updateRole 方法中记录审计日志
   - 在 updateStatus 方法中记录审计日志
   - 在 remove 方法中记录审计日志

4. ✅ **单元测试** (7 tests passed)
   - 测试 updateRole 成功场景
   - 测试自修改拒绝场景
   - 测试无效角色ID场景
   - 测试用户不存在场景
   - 测试角色升级/降级场景
   - 测试审计日志创建

5. ✅ **前端实现**
   - 创建 RoleAssignModal 组件
   - 创建 user.ts API（含 updateRole 方法）
   - 创建 role.ts API（获取角色列表）
   - 更新用户列表页添加"分配角色"按钮
   - 防止修改自己的角色（前端校验）

### File List

**后端 (apps/api/src/):**
- `modules/audit/audit-log.entity.ts` - 新增：审计日志实体
- `modules/audit/audit-log.service.ts` - 新增：审计日志服务
- `modules/audit/audit.module.ts` - 新增：审计模块
- `modules/audit/index.ts` - 新增：模块导出
- `modules/user/dto/update-user-role.dto.ts` - 新增：角色更新DTO
- `modules/user/user.controller.ts` - 更新：添加角色分配端点
- `modules/user/user.service.ts` - 更新：添加updateRole方法，集成审计日志
- `modules/user/user.service.role.spec.ts` - 新增：角色更新单元测试（7 tests）

**前端 (apps/web/src/):**
- `api/user.ts` - 新增：用户API（含updateRole）
- `api/role.ts` - 新增：角色API
- `views/system/users/index.vue` - 更新：添加角色分配按钮
- `views/system/users/components/RoleAssignModal.vue` - 新增：角色分配对话框

---

**Story Context Generated:** 2026-03-31
**Analysis Method:** Ultimate BMad Method Story Context Engine
**Implementation Started:** 2026-03-31
**Last Updated:** 2026-04-01

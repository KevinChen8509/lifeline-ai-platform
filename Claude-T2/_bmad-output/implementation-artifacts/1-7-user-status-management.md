# Story 1.7: 用户状态管理与账号禁用

Status: done

## Story

**As a** 管理员,
**I want** 禁用或删除用户账号,
**So that** 控制用户对系统的访问权限.

## Acceptance Criteria

1. **AC1: 禁用账号**
   - **Given** 管理员在用户管理页面
   - **When** 管理员选择某个用户，点击"禁用账号"
   - **Then** 用户状态变更为"已禁用"
   - **And** 该用户当前会话立即失效（Token撤销）
   - **And** 该用户无法再登录系统

2. **AC2: 启用账号**
   - **Given** 管理员选择已禁用的用户
   - **When** 点击"启用账号"
   - **Then** 用户状态变更为"已激活"
   - **And** 用户可以正常登录

3. **AC3: 删除用户**
   - **Given** 管理员需要删除用户
   - **When** 管理员点击"删除用户"
   - **Then** 系统软删除用户记录（设置deleted_at）
   - **And** 保留审计日志中的操作记录
   - **And** 该用户所有Token立即失效

4. **AC4: 禁用用户无法登录**
   - **Given** 用户状态为"已禁用"
   - **When** 用户尝试登录
   - **Then** 系统返回403 Forbidden
   - **And** 错误消息为"账号已被禁用"

## Tasks / Subtasks

- [x] **Task 1: 后端状态更新API** (AC: 1, 2, 4)
  - [x] 1.1 PUT /users/:id/status 端点已存在
  - [x] 1.2 updateStatus 方法已存在
  - [x] 1.3 登录时检查用户状态（已在Story 1.3实现）

- [x] **Task 2: Token撤销机制** (AC: 1, 3)
  - [x] 2.1 在 RedisService 添加 delByPattern 方法（已存在）
  - [x] 2.2 禁用用户时撤销所有Token
  - [x] 2.3 删除用户时撤销所有Token

- [x] **Task 3: 审计日志** (AC: 3)
  - [x] 3.1 updateStatus 已集成审计日志
  - [x] 3.2 remove 已集成审计日志

- [x] **Task 4: 前端实现** (AC: 1, 2)
  - [x] 4.1 添加"禁用/启用"按钮到用户列表
  - [x] 4.2 实现禁用/启用确认对话框
  - [x] 4.3 更新用户API添加状态管理方法

- [x] **Task 5: 单元测试** (AC: All)
  - [x] 5.1 测试禁用用户时Token撤销
  - [x] 5.2 测试删除用户时Token撤销
  - [x] 5.3 测试Token撤销错误处理

## Completion Notes

1. ✅ **后端状态更新API** - 已在Story 1.3/1.6实现
2. ✅ **Token撤销机制** - 已添加revokeAllUserTokens私有方法
3. ✅ **审计日志** - 已在Story 1.6集成
4. ✅ **前端实现** - 添加禁用/启用按钮和确认对话框
5. ✅ **单元测试** - 10个测试全部通过

## File List

**后端 (apps/api/src/):**
- `modules/user/user.service.ts` - 更新：添加Token撤销逻辑
- `modules/user/user.service.role.spec.ts` - 更新：添加RedisService mock
- `modules/user/user.service.status.spec.ts` - 新增：状态管理单元测试（10 tests）

**前端 (apps/web/src/):**
- `views/system/users/index.vue` - 更新：添加禁用/启用按钮和handler方法

---

**Implementation Date:** 2026-04-01

### 前置依赖 [Source: Story 1.6]

**已完成的实现:**
- UserController 已有 PUT /users/:id/status 端点
- UserService.updateStatus 方法已实现
- UserService.remove 方法已实现（软删除）
- 审计日志已集成到 updateStatus 和 remove
- 登录时检查用户状态（ACTIVE检查）

**本次实现:**
- Token撤销机制（禁用/删除用户时）
- 前端禁用/启用功能

### Token撤销逻辑

```typescript
// 禁用用户时
async updateStatus(id: string, status: UserStatus, operatorId?: string): Promise<User> {
  const user = await this.findOne(id);
  const oldStatus = user.status;
  user.status = status;
  const updatedUser = await this.userRepository.save(user);

  // 如果是禁用操作，撤销所有Token
  if (status === UserStatus.DISABLED) {
    await this.redisService.delByPattern(`refresh:${id}:*`);
  }

  // 记录审计日志
  // ...
}
```

### 状态枚举

```typescript
export enum UserStatus {
  PENDING = 'PENDING',   // 待激活
  ACTIVE = 'ACTIVE',     // 已激活
  DISABLED = 'DISABLED', // 已禁用
}
```

### API 端点

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|----------|
| PUT | /api/v1/users/:id/status | 更新用户状态 | manage:User |
| DELETE | /api/v1/users/:id | 删除用户（软删除） | manage:User |

### 文件结构

**后端修改文件:**
```
apps/api/src/
├── modules/user/
│   ├── user.service.ts              # 更新：添加Token撤销
│   └── user.controller.ts           # 更新：传递operatorId
```

**前端修改文件:**
```
apps/web/src/
├── api/user.ts                      # 更新：添加状态管理方法
├── views/system/users/
│   └── index.vue                    # 更新：添加禁用/启用按钮
```

### References

- [Source: epics.md#Story 1.7] - 原始Story定义
- [Source: Story 1.3] - 登录实现（状态检查）
- [Source: Story 1.6] - 审计日志实现

---

**Story Context Generated:** 2026-04-01
**Analysis Method:** Ultimate BMad Method Story Context Engine

# Story 1.8: 用户列表与查询

Status: done

## Story

**As a** 管理员,
**I want** 查看用户列表并搜索用户,
**So that** 管理和查找系统用户.

## Acceptance Criteria

1. **AC1: 用户列表显示**
   - **Given** 管理员进入用户管理页面
   - **When** 页面加载
   - **Then** 系统显示用户列表（用户名、姓名、邮箱、手机号、角色、状态、创建时间）
   - **And** 支持分页显示（默认每页20条）
   - **And** 按创建时间倒序排列

2. **AC2: 搜索功能**
   - **Given** 管理员在搜索框输入关键词
   - **When** 点击搜索或按回车
   - **Then** 系统按用户名/姓名/邮箱模糊匹配
   - **And** 返回匹配的用户列表

3. **AC3: 状态筛选**
   - **Given** 管理员选择状态筛选
   - **When** 选择特定状态（待激活/已激活/已禁用）
   - **Then** 系统只显示该状态的用户

4. **AC4: 角色筛选**
   - **Given** 管理员选择角色筛选
   - **When** 选择特定角色（管理员/运维员/观察员）
   - **Then** 系统只显示该角色的用户

## Tasks / Subtasks

- [x] **Task 1: 后端基础API** (AC: 1, 2, 3)
  - [x] 1.1 GET /users 端点已存在
  - [x] 1.2 findAll 方法已实现分页和搜索
  - [x] 1.3 状态筛选已实现

- [x] **Task 2: 后端角色筛选** (AC: 4)
  - [x] 2.1 在 FindAllOptions 添加 roleId 参数
  - [x] 2.2 在 findAll 方法添加角色筛选逻辑
  - [x] 2.3 在 UserController 添加 roleId 查询参数

- [x] **Task 3: 前端角色筛选** (AC: 4)
  - [x] 3.1 在搜索表单添加角色下拉选择器
  - [x] 3.2 获取角色列表用于下拉选项
  - [x] 3.3 将 roleId 参数传递给 API

- [x] **Task 4: 单元测试**
  - [x] 4.1 测试角色筛选功能
  - [x] 4.2 测试组合筛选（状态+角色+搜索）

## Dev Notes

### 已完成的实现 [Source: Story 1.1-1.7]

**后端已存在:**
- `GET /api/v1/users` 端点
- `UserService.findAll` 方法（支持分页、状态筛选、搜索）
- `UserController.findAll` 端点

**前端已存在:**
- `apps/web/src/views/system/users/index.vue` - 用户列表页面
- 搜索表单（状态筛选、搜索框）
- 用户表格显示

### 待实现功能

**角色筛选** - 这是本Story唯一缺失的功能:

```typescript
// FindAllOptions 需要添加 roleId
export interface FindAllOptions {
  page: number;
  pageSize: number;
  status?: UserStatus;
  search?: string;
  roleId?: string;  // 新增
}

// findAll 方法需要添加角色筛选
if (roleId) {
  queryBuilder.andWhere('user.roleId = :roleId', { roleId });
}
```

### API 端点

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | /api/v1/users | 获取用户列表 | page, pageSize, status, search, roleId |

**请求示例:**
```
GET /api/v1/users?page=1&pageSize=20&status=ACTIVE&roleId=xxx&search=john
```

**响应格式:**
```json
{
  "items": [
    {
      "id": "uuid",
      "username": "johndoe",
      "name": "John Doe",
      "email": "john@example.com",
      "phone": "13800138000",
      "status": "ACTIVE",
      "roleId": "role-uuid",
      "role": {
        "id": "role-uuid",
        "name": "运维员",
        "code": "operator"
      },
      "createdAt": "2026-04-01T10:00:00Z"
    }
  ],
  "total": 100,
  "page": 1,
  "pageSize": 20
}
```

### 文件结构

**后端修改文件:**
```
apps/api/src/
├── modules/user/
│   ├── user.service.ts              # 更新：添加roleId筛选
│   └── user.controller.ts           # 更新：添加roleId参数
```

**前端修改文件:**
```
apps/web/src/
├── views/system/users/
│   └── index.vue                    # 更新：添加角色筛选下拉框
```

### 状态枚举

```typescript
export enum UserStatus {
  PENDING = 'PENDING',   // 待激活
  ACTIVE = 'ACTIVE',     // 已激活
  DISABLED = 'DISABLED', // 已禁用
}
```

### 角色枚举

```typescript
export enum RoleCode {
  ADMIN = 'admin',       // 管理员
  OPERATOR = 'operator', // 运维员
  OBSERVER = 'observer', // 观察员
}
```

### References

- [Source: epics.md#Story 1.8] - 原始Story定义
- [Source: Story 1.5] - 角色定义
- [Source: Story 1.6] - 用户角色分配

---

**Story Context Generated:** 2026-04-02
**Analysis Method:** Ultimate BMad Method Story Context Engine

## File List

**后端 (apps/api/src/):**
- `modules/user/user.service.ts` - 更新：添加roleId筛选参数和逻辑
- `modules/user/user.controller.ts` - 更新：添加roleId查询参数
- `modules/user/user.service.spec.ts` - 更新：添加角色筛选测试（4个新测试）

**前端 (apps/web/src/):**
- `views/system/users/index.vue` - 更新：添加角色下拉选择器和获取角色列表逻辑

## Completion Notes

1. ✅ **Task 1: 后端基础API** - 已在之前Stories实现
2. ✅ **Task 2: 后端角色筛选** - 添加roleId参数到FindAllOptions和findAll方法
3. ✅ **Task 3: 前端角色筛选** - 添加角色下拉选择器，获取角色列表
4. ✅ **Task 4: 单元测试** - 4个新测试全部通过（31个测试总计）

**测试结果:**
```
Test Suites: 3 passed, 3 total
Tests:       31 passed, 31 total
```

**实现日期:** 2026-04-02

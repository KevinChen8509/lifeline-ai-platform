# Story 1.11: 前端路由权限控制

Status: done

## Story

**As a** 系统,
**I want** 根据用户角色控制前端路由和菜单显示,
**So that** 用户只能看到有权限访问的功能.

## Acceptance Criteria

1. **AC1: 路由权限检查**
   - **Given** 用户登录成功
   - **When** 访问需要权限的页面
   - **Then** 系统检查用户是否有该页面的访问权限
   - **And** 无权限时跳转到403页面

2. **AC2: 菜单权限过滤**
   - **Given** 用户登录成功
   - **When** 系统加载菜单
   - **Then** 系统根据用户角色过滤菜单项
   - **And** 无权限的菜单不显示

3. **AC3: 403页面显示**
   - **Given** 用户直接访问无权限页面
   - **When** 路由守卫检测到无权限
   - **Then** 显示403错误页面
   - **And** 提示用户无访问权限

4. **AC4: 权限一致性**
   - **Given** 菜单显示某个功能
   - **When** 用户点击菜单
   - **Then** 页面可以正常访问
   - **And** 菜单权限与后端API权限一致

## Tasks / Subtasks

- [x] **Task 1: 路由权限守卫** (AC: 1, 4)
  - [x] 1.1 路由 meta 已配置 permission 字段
  - [x] 1.2 router.beforeEach 已实现权限检查
  - [x] 1.3 无权限时跳转到403页面

- [x] **Task 2: 菜单权限过滤** (AC: 2, 4)
  - [x] 2.1 menuConfig 已配置 permission 字段
  - [x] 2.2 filterMenuByPermission 函数已实现
  - [x] 2.3 审计日志菜单项已添加

- [x] **Task 3: 403错误页面** (AC: 3)
  - [x] 3.1 创建 views/error/403.vue 页面
  - [x] 3.2 添加403路由
  - [x] 3.3 更新路由守卫跳转逻辑

- [x] **Task 4: 单元测试**
  - [x] 4.1 测试路由权限守卫
  - [x] 4.2 测试菜单过滤函数
  - [x] 4.3 11个测试全部通过

## Dev Notes

### 已存在的实现

**路由权限守卫 (router/index.ts):**
```typescript
// 路由守卫已实现权限检查
router.beforeEach((to, from, next) => {
  // 权限检查
  if (to.meta.permission) {
    const { action, subject } = to.meta.permission;
    if (!permissionStore.can(action, subject)) {
      // 无权限，重定向到403页面或首页
      next({ name: 'Dashboard' }); // 需要改为403页面
      return;
    }
  }
  next();
});
```

**菜单配置 (config/menu.ts):**
```typescript
// 菜单权限过滤函数已存在
export function filterMenuByPermission(
  menus: MenuItem[],
  can: (action: string, subject: string) => boolean
): MenuItem[]

// 菜单项已配置permission
{
  key: 'users',
  label: '用户管理',
  path: '/system/users',
  permission: { action: 'manage', subject: 'User' },
}
```

**权限Store (stores/permission.ts):**
```typescript
// 权限检查方法已实现
function can(action: string, subject: string): boolean
function cannot(action: string, subject: string): boolean
```

### 待实现功能

**1. 创建403错误页面:**

```vue
<!-- views/error/403.vue -->
<template>
  <div class="error-page">
    <a-result
      status="403"
      title="403"
      sub-title="抱歉，您没有权限访问此页面"
    >
      <template #extra>
        <a-button type="primary" @click="goBack">返回上一页</a-button>
        <a-button @click="goHome">返回首页</a-button>
      </template>
    </a-result>
  </div>
</template>
```

**2. 添加403路由:**

```typescript
{
  path: '/403',
  name: 'Forbidden',
  component: () => import('@/views/error/403.vue'),
  meta: {
    title: '无访问权限',
  },
}
```

**3. 更新路由守卫:**

```typescript
// 无权限时跳转到403页面
if (!permissionStore.can(action, subject)) {
  next({ name: 'Forbidden' });
  return;
}
```

**4. 添加审计日志菜单:**

```typescript
{
  key: 'system',
  label: '系统管理',
  icon: 'SettingOutlined',
  children: [
    // ... existing items
    {
      key: 'audit-log',
      label: '审计日志',
      path: '/system/audit-log',
      permission: { action: 'read', subject: 'AuditLog' },
    },
  ],
}
```

### 文件结构

**前端新增/修改文件:**
```
apps/web/src/
├── views/error/
│   └── 403.vue                     # 新增：403错误页面
├── router/
│   └── index.ts                    # 更新：添加403路由，更新守卫
├── config/
│   └── menu.ts                     # 更新：添加审计日志菜单
└── views/error/
    └── 403.spec.ts                 # 新增：403页面测试
```

### 路由权限配置表

| 路由 | 权限 | 角色要求 |
|------|------|---------|
| /dashboard | 无 | 登录即可 |
| /system/users | manage:User | admin |
| /system/roles | manage:Role | admin |
| /system/audit-log | read:AuditLog | admin |
| /projects | read:Project | operator, admin |
| /devices | read:Device | operator, admin |
| /models | read:Model | operator, admin |
| /alerts | read:Alert | observer, operator, admin |

### References

- [Source: epics.md#Story 1.11] - 原始Story定义
- [Source: Story 1.5] - 角色定义和权限规则
- [Source: FR51] - 系统可以根据用户角色限制功能访问权限
- [Source: UX-DR4] - Vue Vben Admin内置权限管理

---

**Story Context Generated:** 2026-04-02
**Analysis Method:** Ultimate BMad Method Story Context Engine

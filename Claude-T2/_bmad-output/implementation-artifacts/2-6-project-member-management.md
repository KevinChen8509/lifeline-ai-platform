# Story 2.6: 项目成员管理

Status: done

## Story

**As a** 管理员,
**I want** 为项目添加或移除成员,
**So that** 控制哪些用户可以访问该项目的数据.

## Acceptance Criteria

1. **AC1: 添加成员**
   - **Given** 管理员在项目成员页面
   - **When** 添加新成员（选择用户、设置角色）
   - **Then** 成员添加成功
   - **And** 记录审计日志

2. **AC2: 移除成员**
   - **Given** 项目有多个成员
   - **When** 移除某个成员
   - **Then** 成员从项目中移除
   - **And** 记录审计日志

3. **AC3: 修改成员角色**
   - **Given** 成员已有角色
   - **When** 修改角色
   - **Then** 角色更新成功

4. **AC4: 权限控制**
   - **Given** 用户需要项目管理员权限
   - **When** 访问成员管理功能
   - **Then** 系统验证权限

## Tasks / Subtasks

- [ ] **Task 1: 后端成员管理API** (AC: 1, 2, 3)
  - [ ] 1.1 添加成员端点 POST /projects/:id/members
  - [ ] 1.2 移除成员端点 DELETE /projects/:id/members/:userId
  - [ ] 1.3 更新成员角色端点 PUT /projects/:id/members/:userId

- [ ] **Task 2: 前端成员管理UI** (AC: 1, 2, 3)
  - [ ] 2.1 成员列表显示
  - [ ] 2.2 添加成员弹窗
  - [ ] 2.3 移除成员确认
  - [ ] 2.4 角色修改功能

## Dev Notes

### API 设计

**添加成员:**
```http
POST /api/projects/:id/members
{
  "userId": "user-uuid",
  "role": "member" | "admin"
}
```

**更新成员角色:**
```http
PUT /api/projects/:id/members/:userId
{
  "role": "admin" | "member"
}
```

**移除成员:**
```http
DELETE /api/projects/:id/members/:userId
```

### 项目角色

| 角色 | 权限 |
|------|------|
| admin | 项目管理、成员管理、配置编辑 |
| member | 查看项目、操作设备 |

### 文件结构

```
apps/api/src/modules/project/
├── dto/
│   └── add-member.dto.ts          # 新增
├── project.service.ts             # 更新
└── project.controller.ts          # 更新

apps/web/src/views/projects/[id]/
└── index.vue                      # 更新成员Tab
```

### References

- [Source: epics.md#Story 2.6] - 原始Story定义
- [Source: FR4] - 按项目组织设备功能需求

---

**Story Context Generated:** 2026-04-07

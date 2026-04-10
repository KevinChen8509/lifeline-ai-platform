# Story 2.5: 项目配置编辑

Status: done

## Story

**As a** 管理员,
**I want** 编辑项目配置信息,
**So that** 更新项目的基本信息和设置.

## Acceptance Criteria

1. **AC1: 项目基本信息编辑**
   - **Given** 管理员在项目详情页面
   - **When** 点击编辑按钮
   - **Then** 可以修改项目名称、描述

2. **AC2: 项目编码锁定**
   - **Given** 编辑项目信息
   - **When** 查看项目编码字段
   - **Then** 编码字段不可修改

3. **AC3: 审计日志记录**
   - **Given** 项目更新成功
   - **When** 系统记录变更
   - **Then** 审计日志记录更新操作

4. **AC4: 权限控制**
   - **Given** 用户需要 manage:Project 权限
   - **When** 访问编辑功能
   - **Then** 系统验证用户权限

## Tasks / Subtasks

- [x] **Task 1: 后端API** (AC: 1-4)
  - [x] 1.1 ProjectService.update 已实现
  - [x] 1.2 编码字段不可修改
  - [x] 1.3 审计日志记录

- [x] **Task 2: 前端编辑功能** (AC: 1, 2)
  - [x] 2.1 项目详情页编辑弹窗
  - [x] 2.2 编码字段禁用

## Dev Notes

### API 设计

**请求:**
```http
PUT /api/projects/:id
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "更新后的项目名",
  "description": "更新后的描述"
}
```

**注意:** `code` 字段不可修改

### 已实现的文件

**后端:**
- `apps/api/src/modules/project/project.service.ts` - update 方法
- `apps/api/src/modules/project/project.controller.ts` - PUT /projects/:id

**前端:**
- `apps/web/src/views/projects/[id]/index.vue` - 编辑弹窗

### References

- [Source: epics.md#Story 2.5] - 原始Story定义
- [Source: FR3] - 编辑项目配置功能需求

---

**Story Context Generated:** 2026-04-07

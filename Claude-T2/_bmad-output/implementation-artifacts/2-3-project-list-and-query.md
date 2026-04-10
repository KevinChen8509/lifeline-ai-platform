# Story 2.3: 项目列表与查询

Status: done

## Story

**As a** 运维人员,
**I want** 查看项目列表并搜索项目,
**So that** 快速找到需要管理的项目.

## Acceptance Criteria

1. **AC1: 项目列表显示**
   - **Given** 运维人员进入项目列表页面
   - **When** 页面加载
   - **Then** 系统显示用户有权限访问的项目列表

2. **AC2: 搜索功能**
   - **Given** 项目列表页面
   - **When** 输入搜索关键词
   - **Then** 系统按项目名称/编码搜索

3. **AC3: 状态筛选**
   - **Given** 项目列表页面
   - **When** 选择状态筛选
   - **Then** 系统显示对应状态的项目

4. **AC4: 分页显示**
   - **Given** 项目数量超过20条
   - **When** 查看列表
   - **Then** 支持分页显示

## Tasks / Subtasks

- [x] **Task 1: 后端API** (AC: 1-4)
  - [x] 1.1 ProjectService.findAll 已实现
  - [x] 1.2 支持分页、搜索、状态筛选

- [x] **Task 2: 前端API** (AC: 1)
  - [x] 2.1 创建 project API client

- [x] **Task 3: 前端页面** (AC: 1-4)
  - [x] 3.1 创建项目列表页面
  - [x] 3.2 实现搜索功能
  - [x] 3.3 实现状态筛选
  - [x] 3.4 实现分页

- [x] **Task 4: 路由配置** (AC: 1)
  - [x] 4.1 项目路由已配置

## Dev Notes

### 已实现的文件

**后端:**
- `apps/api/src/modules/project/project.service.ts` - findAll 方法
- `apps/api/src/modules/project/project.controller.ts` - GET /projects 端点

**前端:**
- `apps/web/src/api/project/index.ts` - Project API client
- `apps/web/src/views/projects/index.vue` - 项目列表页面

### API 设计

**请求:**
```http
GET /api/projects?page=1&pageSize=20&search={keyword}&status={status}
```

**响应:**
```json
{
  "items": [
    {
      "id": "uuid",
      "name": "项目名称",
      "code": "PROJECT001",
      "description": "描述",
      "status": "active",
      "createdAt": "2026-04-03T10:00:00Z"
    }
  ],
  "total": 100,
  "page": 1,
  "pageSize": 20
}
```

### References

- [Source: epics.md#Story 2.3] - 原始Story定义
- [Source: FR1] - 项目列表查询功能需求

---

**Story Context Generated:** 2026-04-07

# Story 2.2: 项目创建

Status: done

## Story

**As a** 运维人员,
**I want** 创建新项目并设置基本信息,
**So that** 为设备组织和管理建立项目框架.

## Acceptance Criteria

1. **AC1: 项目创建 API**
   - **Given** 运维人员已登录系统
   - **When** 调用 `POST /api/projects` 创建项目
   - **Then** 系统验证项目编码唯一性（全局唯一）
   - **And** 系统创建项目记录，状态为"active"
   - **And** 创建者自动成为项目管理员

2. **AC2: 项目编码格式验证**
   - **Given** 用户提交项目创建请求
   - **When** 验证项目编码
   - **Then** 编码格式为大写字母+数字，3-20字符
   - **And** 编码全局唯一

3. **AC3: 审计日志记录**
   - **Given** 项目创建成功
   - **When** 系统记录操作
   - **Then** 审计日志记录项目创建操作

4. **AC4: 权限控制**
   - **Given** 用户需要 manage:Project 权限
   - **When** 访问项目创建 API
   - **Then** 系统验证用户权限

## Tasks / Subtasks

- [ ] **Task 1: 创建 DTO** (AC: 1, 2)
  - [ ] 1.1 创建 CreateProjectDto
  - [ ] 1.2 添加验证装饰器

- [ ] **Task 2: 创建 Service** (AC: 1, 3)
  - [ ] 2.1 创建 ProjectService
  - [ ] 2.2 实现 create 方法
  - [ ] 2.3 添加项目编码唯一性检查
  - [ ] 2.4 自动添加创建者为项目管理员

- [ ] **Task 3: 创建 Controller** (AC: 1, 4)
  - [ ] 3.1 创建 ProjectController
  - [ ] 3.2 添加权限守卫
  - [ ] 3.3 添加 Swagger 文档

- [ ] **Task 4: 单元测试** (AC: 1-4)
  - [ ] 4.1 测试项目创建成功
  - [ ] 4.2 测试编码唯一性验证
  - [ ] 4.3 测试权限控制

## Dev Notes

### API 设计

**请求:**
```http
POST /api/projects
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "智慧水务监测项目",
  "code": "WATER2024",
  "description": "城市供水管网智能监测系统"
}
```

**响应:**
```json
{
  "id": "uuid",
  "name": "智慧水务监测项目",
  "code": "WATER2024",
  "description": "城市供水管网智能监测系统",
  "status": "active",
  "createdAt": "2026-04-03T10:00:00Z",
  "updatedAt": "2026-04-03T10:00:00Z"
}
```

### 项目编码规则

- 格式: 大写字母开头，可包含大写字母和数字
- 长度: 3-20 字符
- 正则: `^[A-Z][A-Z0-9]{2,19}$`
- 全局唯一

### 文件结构

```
apps/api/src/modules/project/
├── project.module.ts
├── project.entity.ts
├── project-user.entity.ts
├── project.service.ts
├── project.controller.ts
├── dto/
│   ├── create-project.dto.ts
│   └── project-response.dto.ts
└── __tests__/
    ├── project.service.spec.ts
    └── project.controller.spec.ts
```

### References

- [Source: epics.md#Story 2.2] - 原始Story定义
- [Source: FR1] - 创建新项目功能需求

---

**Story Context Generated:** 2026-04-03

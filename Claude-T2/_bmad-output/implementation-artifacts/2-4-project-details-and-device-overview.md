# Story 2.4: 项目详情与设备概览

Status: done

## Story

**As a** 运维人员,
**I want** 查看项目详情和设备状态概览,
**So that** 了解项目整体运行情况.

## Acceptance Criteria

1. **AC1: 项目基本信息显示**
   - **Given** 运维人员点击项目
   - **When** 进入项目详情页面
   - **Then** 系统显示项目基本信息（名称、编码、描述、创建时间）

2. **AC2: 设备统计卡片**
   - **Given** 项目详情页面
   - **When** 页面加载
   - **Then** 系统显示设备统计卡片（总设备数、在线数、离线数、告警数）

3. **AC3: 最近活动记录**
   - **Given** 项目详情页面
   - **When** 页面加载
   - **Then** 系统显示最近5条设备状态变化记录

## Tasks / Subtasks

- [ ] **Task 1: 后端 Overview API** (AC: 2)
  - [ ] 1.1 创建 ProjectOverviewResponseDto
  - [ ] 1.2 添加 getOverview 方法到 ProjectService
  - [ ] 1.3 添加 GET /projects/:id/overview 端点

- [ ] **Task 2: 前端详情页** (AC: 1, 2, 3)
  - [ ] 2.1 创建项目详情页面
  - [ ] 2.2 实现设备统计卡片
  - [ ] 2.3 实现最近活动列表（占位）

- [ ] **Task 3: 路由配置** (AC: 1)
  - [ ] 3.1 添加项目详情路由

## Dev Notes

### API 设计

**请求:**
```http
GET /api/projects/:id
GET /api/projects/:id/overview
```

**Overview 响应:**
```json
{
  "stats": {
    "totalDevices": 100,
    "onlineDevices": 85,
    "offlineDevices": 10,
    "alertDevices": 5
  },
  "recentActivities": []
}
```

### 文件结构

```
apps/api/src/modules/project/
├── dto/
│   └── project-overview.dto.ts    # 新增
└── project.controller.ts          # 更新

apps/web/src/views/projects/
└── [id].vue                       # 新增：项目详情页
```

### References

- [Source: epics.md#Story 2.4] - 原始Story定义
- [Source: FR2] - 查看项目设备列表功能需求

---

**Story Context Generated:** 2026-04-07

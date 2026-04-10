# Story 3.4: 设备实时状态查看

Status: done

## Story

**As a** 运维人员,
**I want** 在设备列表中查看所有设备的实时状态,
**So that** 快速了解设备在线/离线/告警情况.

## Acceptance Criteria

1. **AC1: 设备列表显示**
   - **Given** 运维人员进入设备列表页面
   - **When** 页面加载完成
   - **Then** 显示设备列表，每个设备显示状态徽章（在线/离线/告警/维护）

2. **AC2: 状态筛选**
   - **Given** 设备列表已加载
   - **When** 选择状态筛选条件
   - **Then** 只显示符合筛选条件的设备

3. **AC3: 搜索功能**
   - **Given** 设备列表已加载
   - **When** 输入搜索关键词
   - **Then** 按设备名称/编号搜索

## Tasks / Subtasks

- [x] **Task 1: 后端API** (AC: 1, 2, 3)
  - [x] 1.1 实现 findAll 方法
  - [x] 1.2 状态筛选支持
  - [x] 1.3 搜索功能支持

- [ ] **Task 2: 前端实现** (AC: 1, 2, 3)
  - [ ] 2.1 设备列表页面
  - [ ] 2.2 状态筛选组件
  - [ ] 2.3 搜索组件

## Dev Notes

### API 设计

```http
GET /api/devices?status=online&search=SN001&page=1&pageSize=20
Response: {
  "items": [...],
  "total": 100,
  "page": 1,
  "pageSize": 20
}
```

### 状态徽章颜色

| 状态 | 颜色 |
|------|------|
| online | green |
| offline | default |
| alert | red |
| maintenance | orange |
| pending | blue |
| activating | cyan |
| failed | red |

### References

- [Source: epics.md#Story 3.4] - 原始Story定义
- [Source: FR8] - 设备实时状态查看功能需求

---

**Story Context Generated:** 2026-04-07

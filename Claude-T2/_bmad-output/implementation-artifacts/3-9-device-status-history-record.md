# Story 3.9: 设备历史状态变更记录

Status: done

## Story

**As a** 运维人员,
**I want** 查看设备的历史状态变更记录,
**So that** 追溯设备状态变化原因，辅助故障排查.

## Acceptance Criteria

1. **AC1: 状态历史列表**
   - **Given** 运维人员在设备详情页面
   - **When** 点击"状态历史"标签页
   - **Then** 显示设备状态变更历史列表

2. **AC2: 时间范围筛选**
   - **Given** 状态历史列表已加载
   - **When** 选择时间范围
   - **Then** 筛选指定范围内的记录

3. **AC3: 记录保留**
   - **Given** 设备状态变更
   - **When** 系统记录变更
   - **Then** 记录保留至少2个月

## Tasks / Subtasks

- [ ] **Task 1: 后端API** (AC: 1, 2, 3)
  - [ ] 1.1 创建 DeviceStatusHistory 实体
  - [ ] 1.2 添加 getStatusHistory 方法
  - [ ] 1.3 GET /devices/:id/status-history 端点

## Dev Notes

### API 设计

```http
GET /api/devices/:id/status-history?startDate=2024-01-01&endDate=2024-01-31
Response: {
  "items": [
    {
      "id": "uuid",
      "fromStatus": "online",
      "toStatus": "offline",
      "reason": "连接超时",
      "timestamp": "2024-01-15T10:30:00Z"
    }
  ],
  "total": 50
}
```

### 数据存储

- PostgreSQL 存储最近2个月数据
- ClickHouse 存储历史归档数据（可选）

### References

- [Source: epics.md#Story 3.9] - 原始Story定义
- [Source: FR13] - 设备历史状态变更记录功能需求

---

**Story Context Generated:** 2026-04-08

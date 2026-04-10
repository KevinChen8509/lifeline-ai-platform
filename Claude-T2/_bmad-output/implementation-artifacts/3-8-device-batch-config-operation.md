# Story 3.8: 设备批量配置操作

Status: done

## Story

**As a** 运维人员,
**I want** 批量对多台设备执行相同的配置操作,
**So that** 提高运维效率，避免逐台重复操作.

## Acceptance Criteria

1. **AC1: 批量选择**
   - **Given** 运维人员在设备列表页面
   - **When** 勾选多台设备（同一类型）
   - **Then** 显示批量操作工具栏

2. **AC2: 类型验证**
   - **Given** 尝试批量配置
   - **When** 选中设备类型不一致
   - **Then** 显示警告信息

3. **AC3: 批量执行**
   - **Given** 提交批量配置
   - **When** 开始执行
   - **Then** 创建批量任务并显示进度

4. **AC4: 结果汇总**
   - **Given** 批量操作完成
   - **When** 查看结果
   - **Then** 显示成功/失败计数

## Tasks / Subtasks

- [ ] **Task 1: 后端API** (AC: 1, 2, 3, 4)
  - [ ] 1.1 创建 BatchUpdateConfigDto
  - [ ] 1.2 添加 batchUpdateConfig 方法
  - [ ] 1.3 POST /devices/batch-config 端点

## Dev Notes

### API 设计

```http
POST /api/devices/batch-config
Request: {
  "deviceIds": ["uuid1", "uuid2", ...],
  "config": {
    "collectInterval": 15,
    "uploadInterval": 60,
    "alertThresholds": { ... }
  }
}
Response: {
  "taskId": "task-uuid",
  "status": "processing",
  "total": 10,
  "success": 0,
  "failed": 0
}
```

### 限制

- 最多同时批量操作 100 台设备
- 只能对同类型设备进行批量配置

### References

- [Source: epics.md#Story 3.8] - 原始Story定义
- [Source: FR12] - 设备批量配置操作功能需求

---

**Story Context Generated:** 2026-04-07

# Story 3.6: 设备采集与上传配置

Status: done

## Story

**As a** 运维人员,
**I want** 远程修改设备的采集频次和上传频次,
**So that** 根据实际需求调整设备上报频率，优化网络资源.

## Acceptance Criteria

1. **AC1: 配置验证**
   - **Given** 运维人员修改采集频次
   - **When** 输入值超出范围（1-60分钟）
   - **Then** 系统显示验证错误

2. **AC2: 配置下发**
   - **Given** 配置验证通过，设备在线
   - **When** 提交配置
   - **Then** 系统通过MQTT下发配置更新指令

3. **AC3: 配置确认**
   - **Given** 设备确认接收
   - **When** 系统收到确认
   - **Then** 更新数据库中的配置记录

## Tasks / Subtasks

- [ ] **Task 1: 后端API** (AC: 1, 2, 3)
  - [ ] 1.1 创建 UpdateConfigDto
  - [ ] 1.2 添加 updateConfig 方法
  - [ ] 1.3 PUT /devices/:id/config 端点

## Dev Notes

### API 设计

```http
PUT /api/devices/:id/config
Request: {
  "collectInterval": 15,
  "uploadInterval": 60
}
Response: {
  "success": true,
  "config": { ... }
}
```

### MQTT 指令

```json
{
  "cmd": "update_config",
  "collectInterval": 15,
  "uploadInterval": 60,
  "timestamp": "2024-01-01T00:00:00Z"
}
```

### 参数范围

| 参数 | 最小值 | 最大值 | 单位 |
|------|--------|--------|------|
| collectInterval | 1 | 60 | 分钟 |
| uploadInterval | 5 | 1440 | 分钟 |

### References

- [Source: epics.md#Story 3.6] - 原始Story定义
- [Source: FR10] - 设备采集与上传配置功能需求

---

**Story Context Generated:** 2026-04-07

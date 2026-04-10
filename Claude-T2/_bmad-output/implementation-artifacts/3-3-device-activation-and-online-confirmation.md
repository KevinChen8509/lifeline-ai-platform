# Story 3.3: 设备激活与上线确认

Status: done

## Story

**As a** 运维人员,
**I want** 确认设备信息后一键激活设备,
**So that** 设备正式接入系统并开始上报数据.

## Acceptance Criteria

1. **AC1: 激活指令发送**
   - **Given** 设备处于"待激活"状态
   - **When** 运维人员点击"激活设备"
   - **Then** 系统向设备发送激活指令

2. **AC2: 激活进度显示**
   - **Given** 激活指令已发送
   - **When** 设备响应
   - **Then** 显示激活进度（网络连接→服务器注册→数据上报）

3. **AC3: 上线确认**
   - **Given** 设备首次上报数据
   - **When** 系统收到数据
   - **Then** 设备状态变更为"在线"

## Tasks / Subtasks

- [ ] **Task 1: 后端激活API** (AC: 1, 2, 3)
  - [ ] 1.1 添加 activate 方法到 DeviceService
  - [ ] 1.2 添加 POST /devices/:id/activate 端点
  - [ ] 1.3 设备状态流转 (PENDING → ACTIVATING → ONLINE)

- [ ] **Task 2: 激活进度追踪** (AC: 2)
  - [ ] 2.1 激活进度实体/存储
  - [ ] 2.2 激活状态查询API

## Dev Notes

### 状态流转

```
PENDING → ACTIVATING → ONLINE
              ↓
           FAILED (可重试)
```

### MQTT Topics

- 下行: `device/{deviceId}/command`
- 上行: `device/{deviceId}/status`

### 激活指令格式

```json
{
  "cmd": "activate",
  "timestamp": "2024-01-01T00:00:00Z",
  "server": "mqtt.example.com",
  "port": 1883
}
```

### References

- [Source: epics.md#Story 3.3] - 原始Story定义
- [Source: FR6] - 设备激活功能需求

---

**Story Context Generated:** 2026-04-07

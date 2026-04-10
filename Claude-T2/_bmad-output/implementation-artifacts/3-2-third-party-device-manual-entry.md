# Story 3.2: 第三方设备手动录入

Status: done

## Story

**As a** 运维人员,
**I want** 手动录入第三方设备的基本信息,
**So that** 可以管理非自研设备，实现统一平台管理.

## Acceptance Criteria

1. **AC1: 手动录入表单**
   - **Given** 运维人员需要接入第三方设备
   - **When** 在设备注册页面选择"手动添加设备"
   - **Then** 显示设备信息录入表单

2. **AC2: 必填字段验证**
   - **Given** 填写设备信息
   - **When** 提交表单
   - **Then** 系统验证必填字段完整性（名称、序列号、通信协议）

3. **AC3: 设备创建**
   - **Given** 表单验证通过
   - **When** 提交
   - **Then** 创建设备记录并标记为"第三方设备"

## Tasks / Subtasks

- [ ] **Task 1: 后端 API** (AC: 1, 2, 3)
  - [ ] 1.1 创建 CreateDeviceDto
  - [ ] 1.2 实现 POST /devices 端点
  - [ ] 1.3 支持设备来源字段

- [ ] **Task 2: 前端实现** (AC: 1, 2, 3)
  - [ ] 2.1 手动录入表单组件
  - [ ] 2.2 协议选择组件

## Dev Notes

### 支持的通信协议

- MQTT
- Modbus (TCP/RTU)
- HTTP

### API 设计

```http
POST /api/devices
Request: {
  "name": "第三方液位计",
  "serialNumber": "TP20240001",
  "deviceType": "WATER_LEVEL_SENSOR",
  "manufacturer": "第三方厂商",
  "protocol": "MQTT",
  "description": "备注信息"
}
```

### References

- [Source: epics.md#Story 3.2] - 原始Story定义
- [Source: FR7] - 第三方设备手动录入功能需求

---

**Story Context Generated:** 2026-04-07

# Story 3.10: 设备固件OTA升级

Status: done

## Story

**As a** 管理员,
**I want** 远程触发设备固件OTA升级,
**So that** 设备功能迭代和漏洞修复，无需现场操作.

## Acceptance Criteria

1. **AC1: 版本兼容性验证**
   - **Given** 管理员选择目标固件版本
   - **When** 开始升级
   - **Then** 系统验证设备当前版本与目标版本兼容性

2. **AC2: 升级指令下发**
   - **Given** 验证通过
   - **When** 发送升级指令
   - **Then** 通过MQTT下发升级指令和固件下载地址

3. **AC3: 升级进度追踪**
   - **Given** 升级进行中
   - **When** 设备上报进度
   - **Then** 显示进度（下载中→安装中→重启中→完成）

4. **AC4: 失败回滚**
   - **Given** 升级失败
   - **When** 检测到失败
   - **Then** 自动回滚到原版本

## Tasks / Subtasks

- [ ] **Task 1: 后端API** (AC: 1, 2, 3, 4)
  - [ ] 1.1 添加 firmwareVersion 字段到 Device 实体
  - [ ] 1.2 创建 FirmwareRelease 实体
  - [ ] 1.3 添加 triggerOtaUpgrade 方法
  - [ ] 1.4 POST /devices/:id/ota 端点

## Dev Notes

### API 设计

```http
POST /api/devices/:id/ota
Request: {
  "targetVersion": "v2.1.0"
}
Response: {
  "taskId": "ota-uuid",
  "status": "downloading",
  "progress": 0
}
```

### MQTT 指令

```json
{
  "cmd": "ota_upgrade",
  "version": "v2.1.0",
  "url": "https://firmware.example.com/v2.1.0.bin",
  "signature": "rsa-sha256-...",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

### References

- [Source: epics.md#Story 3.10] - 原始Story定义
- [Source: FR14] - 设备固件OTA升级功能需求

---

**Story Context Generated:** 2026-04-08

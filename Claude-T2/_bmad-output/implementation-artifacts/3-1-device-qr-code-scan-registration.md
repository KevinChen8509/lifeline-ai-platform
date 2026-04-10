# Story 3.1: 设备二维码扫描注册

Status: done

## Story

**As a** 运维人员,
**I want** 通过扫描设备二维码快速完成设备注册,
**So that** 无需手动输入设备信息，10分钟内完成设备上线第一步.

## Acceptance Criteria

1. **AC1: 二维码解析**
   - **Given** 运维人员打开设备注册页面
   - **When** 扫描设备上的二维码
   - **Then** 系统自动解析二维码中的设备序列号和型号信息

2. **AC2: 设备预览**
   - **Given** 二维码解析成功
   - **When** 显示设备预览信息
   - **Then** 显示设备型号、出厂配置、推荐项目

3. **AC3: 已注册检测**
   - **Given** 设备已在系统中存在
   - **When** 扫描二维码
   - **Then** 显示"设备已注册"提示并提供查看详情入口

## Tasks / Subtasks

- [ ] **Task 1: 后端 API** (AC: 1, 2, 3)
  - [ ] 1.1 创建 ScanRegisterDto
  - [ ] 1.2 实现 QR 码解析逻辑
  - [ ] 1.3 实现 scan-register 端点
  - [ ] 1.4 设备预览信息返回

- [ ] **Task 2: 前端实现** (AC: 1, 2, 3)
  - [ ] 2.1 扫码组件集成
  - [ ] 2.2 设备预览页面
  - [ ] 2.3 已注册设备提示

## Dev Notes

### 二维码格式

```
LK://{device_sn}:{device_type}:{factory_id}
```

示例: `LK://SN20240001:WATER_LEVEL_SENSOR:FACTORY001`

### API 设计

```http
POST /api/devices/scan-register
Request: { "qrData": "LK://SN20240001:WATER_LEVEL_SENSOR:FACTORY001" }
Response: {
  "isNew": true,
  "device": { ... },
  "preview": {
    "deviceType": "WATER_LEVEL_SENSOR",
    "recommendedProject": null
  }
}
```

### 设备类型映射

| device_type | 名称 | 描述 |
|-------------|------|------|
| WATER_LEVEL_SENSOR | 液位传感器 | 水位监测 |
| FLOW_METER | 流量计 | 流量监测 |
| PRESSURE_SENSOR | 压力传感器 | 压力监测 |

### References

- [Source: epics.md#Story 3.1] - 原始Story定义
- [Source: FR6] - 设备二维码扫描注册功能需求

---

**Story Context Generated:** 2026-04-07

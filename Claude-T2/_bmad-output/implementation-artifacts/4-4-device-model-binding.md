# Story 4.4: 设备-模型绑定操作

Status: completed

## Story

**As a** 运维人员,
**I want** 将AI模型绑定到指定设备,
**So that** 激活设备的AI分析能力.

## Acceptance Criteria

1. **AC1: 设备状态验证**
   - **Given** 运维人员在设备详情页面
   - **When** 设备处于在线状态
   - **Then** 系统允许绑定模型

2. **AC2: 模型兼容性验证**
   - **Given** 运维人员选择AI模型
   - **When** 系统验证模型与设备类型兼容性
   - **Then** 不兼容的模型被拒绝

3. **AC3: 绑定记录创建**
   - **Given** 模型验证通过
   - **When** 系统创建绑定记录
   - **Then** 状态为"待同步"

4. **AC4: MQTT指令下发**
   - **Given** 绑定记录创建成功
   - **When** 系统通过MQTT下发模型加载指令
   - **Then** 边缘设备接收指令

5. **AC5: 边缘设备确认**
   - **Given** 边缘设备确认接收
   - **When** 状态变更为"运行中"
   - **Then** 显示绑定成功提示

## Tasks / Subtasks

- [x] **Task 1: 创建绑定DTO** (AC: 1-5)
  - [x] 1.1 创建 BindModelsDto

- [x] **Task 2: 实现绑定服务方法** (AC: 1-5)
  - [x] 2.1 实现设备状态验证
  - [x] 2.2 实现模型兼容性检查
  - [x] 2.3 创建绑定记录
  - [x] 2.4 准备MQTT指令

- [x] **Task 3: 实现解绑服务方法** (AC: 1-5)
  - [x] 3.1 删除绑定记录
  - [x] 3.2 准备卸载指令

- [x] **Task 4: 添加控制器端点** (AC: 1-5)
  - [x] 4.1 POST /devices/:id/models
  - [x] 4.2 DELETE /devices/:id/models/:modelId
  - [x] 4.3 GET /devices/:id/models

- [x] **Task 5: 更新模块配置** (AC: 1-5)
  - [x] 5.1 添加 DeviceModelBinding 和 AiModel 到 TypeORM

## Dev Notes

### API 端点

- `POST /api/v1/devices/{deviceId}/models`
- `DELETE /api/v1/devices/{deviceId}/models/{modelId}`
- `GET /api/v1/devices/{deviceId}/models`

### 请求体 (绑定)

```json
{
  "modelIds": ["model-uuid-1", "model-uuid-2"]
}
```

### 响应格式 (绑定)

```json
{
  "bindings": [
    {
      "id": "binding-uuid",
      "deviceId": "device-uuid",
      "modelId": "model-uuid",
      "status": "pending",
      "boundAt": "2024-01-15T10:00:00Z"
    }
  ],
  "mqttCommand": {
    "cmd": "load_model",
    "models": [
      {
        "id": "model-uuid",
        "code": "MIXED_CONNECTION_V2",
        "version": "v2.1.0",
        "url": "https://...",
        "checksum": "sha256:..."
      }
    ],
    "timestamp": "2024-01-15T10:00:00Z"
  }
}
```

### MQTT 指令格式

```json
{
  "cmd": "load_model",
  "models": [{ "id": "xxx", "url": "xxx", "checksum": "xxx" }]
}
```

### 兼容性检查

- 检查 `ai_models.applicable_device_types` 是否包含设备类型
- 如果数组为空，则认为兼容所有设备类型

### References

- [Source: epics.md#Story 4.4] - 原始Story定义
- [Source: FR17] - 设备-模型绑定功能需求

---

**Story Implemented:** 2026-04-08

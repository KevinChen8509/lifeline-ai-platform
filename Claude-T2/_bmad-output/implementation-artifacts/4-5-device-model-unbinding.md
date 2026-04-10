# Story 4.5: 设备-模型绑定解除

Status: completed

## Story

**As a** 运维人员,
**I want** 解除设备与AI模型的绑定关系,
**So that** 停止设备的某项AI分析能力.

## Acceptance Criteria

1. **AC1: 绑定存在验证**
   - **Given** 设备已绑定模型
   - **When** 运维人员点击"解除绑定"按钮
   - **Then** 系统验证绑定关系存在

2. **AC2: 确认提示**
   - **Given** 绑定关系存在
   - **When** 系统显示确认对话框
   - **Then** 警告：解除后将停止该分析能力

3. **AC3: MQTT卸载指令**
   - **Given** 用户确认解除
   - **When** 系统通过MQTT下发模型卸载指令
   - **Then** 边缘设备接收卸载指令

4. **AC4: 删除绑定记录**
   - **Given** 边缘设备确认卸载
   - **When** 系统删除绑定记录
   - **Then** 数据库中不再存在该绑定

5. **AC5: 历史数据保留**
   - **Given** 绑定已解除
   - **When** 查询历史分析结果
   - **Then** 历史数据仍然可以查询

## Tasks / Subtasks

- [x] **Task 1: 实现解绑服务方法** (AC: 1-5)
  - [x] 1.1 验证绑定关系存在
  - [x] 1.2 准备MQTT卸载指令
  - [x] 1.3 删除绑定记录
  - [x] 1.4 记录审计日志

- [x] **Task 2: 添加控制器端点** (AC: 1-5)
  - [x] 2.1 DELETE /devices/:id/models/:modelId

## Dev Notes

### API 端点

- `DELETE /api/v1/devices/{deviceId}/models/{modelId}`

### 响应格式

```json
{
  "success": true,
  "mqttCommand": {
    "cmd": "unload_model",
    "modelId": "model-uuid",
    "timestamp": "2024-01-15T10:00:00Z"
  }
}
```

### MQTT 指令格式

```json
{
  "cmd": "unload_model",
  "modelId": "xxx"
}
```

### 历史数据保留

- `ai_analysis_results` 表中的历史分析结果保留不变
- 通过 `device_id` 和 `model_id` 仍可查询历史数据

### References

- [Source: epics.md#Story 4.5] - 原始Story定义
- [Source: FR18] - 设备-模型绑定解除功能需求

---

**Story Implemented:** 2026-04-08

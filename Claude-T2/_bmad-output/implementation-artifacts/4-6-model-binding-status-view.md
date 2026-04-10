# Story 4.6: 模型绑定状态查看

Status: completed

## Story

**As a** 运维人员,
**I want** 查看设备绑定的模型运行状态,
**So that** 确认AI模型是否正常工作.

## Acceptance Criteria

1. **AC1: 绑定状态展示**
   - **Given** 运维人员在设备详情页面
   - **When** 查看"已绑定模型"区块
   - **Then** 系统显示每个绑定模型的状态（运行中/待同步/异常/离线）

2. **AC2: 最后同步时间**
   - **Given** 查看绑定模型信息
   - **When** 页面加载完成
   - **Then** 显示最后同步时间

3. **AC3: 模型版本号**
   - **Given** 查看绑定模型信息
   - **When** 页面加载完成
   - **Then** 显示模型版本号

4. **AC4: 异常状态高亮**
   - **Given** 模型状态为异常
   - **When** 显示状态
   - **Then** 使用红色警示显示

5. **AC5: 模型详情链接**
   - **Given** 运维人员点击模型
   - **When** 跳转到模型详情页面
   - **Then** 显示模型完整信息

## Tasks / Subtasks

- [x] **Task 1: 实现获取绑定列表方法** (AC: 1-5)
  - [x] 1.1 getBoundModels 服务方法
  - [x] 1.2 返回模型信息和绑定状态
  - [x] 1.3 返回最后同步时间
  - [x] 1.4 返回绑定版本

- [x] **Task 2: 添加控制器端点** (AC: 1-5)
  - [x] 2.1 GET /devices/:id/models
  - [x] 2.2 支持分页参数

## Dev Notes

### API 端点

- `GET /api/v1/devices/{deviceId}/models?page=1&pageSize=20`

### 响应格式

```json
{
  "items": [
    {
      "id": "binding-uuid",
      "deviceId": "device-uuid",
      "modelId": "model-uuid",
      "status": "running",
      "boundVersion": "v2.1.0",
      "boundAt": "2024-01-15T10:00:00Z",
      "lastSyncAt": "2024-01-15T12:00:00Z",
      "error": null,
      "model": {
        "id": "model-uuid",
        "name": "错混接检测模型",
        "code": "MIXED_CONNECTION_V2",
        "type": "mixed_connection",
        "version": "v2.1.0"
      }
    }
  ],
  "total": 3,
  "page": 1,
  "pageSize": 20
}
```

### 状态枚举

| 状态 | 代码 | 说明 |
|------|------|------|
| 待同步 | PENDING | 等待边缘设备确认 |
| 同步中 | SYNCING | 正在同步模型文件 |
| 运行中 | RUNNING | 模型正常运行 |
| 异常 | ERROR | 模型运行异常 |
| 离线 | OFFLINE | 设备离线，模型停止 |

### References

- [Source: epics.md#Story 4.6] - 原始Story定义
- [Source: FR19] - 模型绑定状态查看功能需求

---

**Story Implemented:** 2026-04-08

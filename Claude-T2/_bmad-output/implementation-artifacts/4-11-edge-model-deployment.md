# Story 4.11: 边缘模型下发与更新

Status: completed

## Story

**As a** 管理员,
**I want** 从云端向边缘设备下发更新的AI模型,
**So that** 设备获得最新的AI分析能力.

## Acceptance Criteria

1. **AC1: 选择目标设备**
   - **Given** 管理员在模型详情页面，新版本已发布
   - **When** 管理员点击"下发更新"，选择目标设备
   - **Then** 支持单个或批量选择设备

2. **AC2: 设备状态验证**
   - **Given** 选择目标设备后
   - **When** 系统验证设备在线状态
   - **Then** 离线设备被标记并排除

3. **AC3: MQTT指令下发**
   - **Given** 验证通过
   - **When** 系统通过MQTT下发模型更新指令
   - **Then** 指令包含下载URL和签名

4. **AC4: 设备确认加载**
   - **Given** 边缘设备下载新模型并验证签名
   - **When** 设备加载新模型并上报更新状态
   - **Then** 绑定记录的版本号自动更新

5. **AC5: 失败回滚**
   - **Given** 更新失败
   - **When** 设备上报失败
   - **Then** 保留原模型运行

## Tasks / Subtasks

- [x] **Task 1: 创建部署实体** (AC: 1-5)
  - [x] 1.1 创建 ModelDeployment 实体
  - [x] 1.2 创建 DeviceDeployment 实体
  - [x] 1.3 定义部署状态枚举

- [x] **Task 2: 实现部署服务** (AC: 1-5)
  - [x] 2.1 createDeployment 方法
  - [x] 2.2 getDeployment 方法
  - [x] 2.3 updateDeviceDeploymentStatus 方法
  - [x] 2.4 getDeploymentCommand 方法

- [x] **Task 3: 添加控制器端点** (AC: 1-5)
  - [x] 3.1 POST /ai-models/:id/deploy
  - [x] 3.2 GET /ai-models/:id/deployments
  - [x] 3.3 GET /ai-models/:id/deployments/:deploymentId
  - [x] 3.4 POST /ai-models/:id/deployments/:deploymentId/retry
  - [x] 3.5 POST /ai-models/:id/deployments/:deploymentId/cancel

## Dev Notes

### API 端点

- `POST /api/v1/ai-models/{modelId}/deploy`
- `GET /api/v1/ai-models/{modelId}/deployments/{deploymentId}`

### 请求体 (部署)

```json
{
  "deviceIds": ["device-uuid-1", "device-uuid-2"],
  "version": "v2.1.0",
  "force": false
}
```

### MQTT 指令格式

```json
{
  "cmd": "update_model",
  "modelId": "xxx",
  "version": "v2.1.0",
  "url": "https://...",
  "checksum": "sha256:...",
  "signature": "RSA-2048签名"
}
```

### 部署状态流转

```
PENDING → IN_PROGRESS → COMPLETED
                    ↘ FAILED
                    ↘ CANCELLED
```

### 设备部署状态流转

```
PENDING → DOWNLOADING → INSTALLING → SUCCESS
                                ↘ FAILED
```

### References

- [Source: epics.md#Story 4.11] - 原始Story定义
- [Source: FR22] - 边缘模型下发功能需求
- [Source: NFR-S08] - 固件/模型安全升级

---

**Story Status:** Completed

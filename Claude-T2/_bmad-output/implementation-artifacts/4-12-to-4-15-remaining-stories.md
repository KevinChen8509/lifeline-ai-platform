# Story 4.12: 模型下发进度监控

Status: planned

## Story

**As a** 管理员,
**I want** 实时查看模型下发进度,
**So that** 了解更新状态和发现问题.

## Acceptance Criteria

1. **AC1: 总体进度展示**
   - **Given** 模型下发任务已创建
   - **When** 管理员查看下发任务详情
   - **Then** 显示总体进度（成功X台/失败Y台/进行中Z台/待处理N台）

2. **AC2: 设备详细状态**
   - **Given** 查看部署详情
   - **When** 展开设备列表
   - **Then** 显示每台设备的详细状态

3. **AC3: 失败原因展示**
   - **Given** 设备部署失败
   - **When** 查看失败详情
   - **Then** 显示具体失败原因

4. **AC4: WebSocket实时更新**
   - **Given** 用户正在查看进度
   - **When** 设备状态变更
   - **Then** 通过WebSocket实时推送更新

5. **AC5: 重试失败设备**
   - **Given** 存在失败设备
   - **When** 点击"重试"
   - **Then** 重新下发部署指令

## Dev Notes

### API 端点

- `GET /api/v1/ai-models/{modelId}/deployments/{deploymentId}`

### WebSocket 事件

- `deployment.{deploymentId}.progress`

### 响应格式

```json
{
  "deployment": {
    "id": "deployment-uuid",
    "status": "in_progress",
    "totalDevices": 10,
    "successCount": 5,
    "failedCount": 1,
    "inProgressCount": 2,
    "pendingCount": 2
  },
  "deviceDeployments": [...],
  "progress": {
    "total": 10,
    "success": 5,
    "failed": 1,
    "pending": 2,
    "downloading": 1,
    "installing": 1
  }
}
```

---

# Story 4.13: 模型运行状态监控看板

Status: planned

## Story

**As a** 运维人员,
**I want** 在看板上查看所有设备的模型运行状态,
**So that** 快速发现模型异常的设备.

## Acceptance Criteria

1. **AC1: 模型状态统计**
   - **Given** 运维人员进入监控看板
   - **When** 查看"模型状态"区块
   - **Then** 显示模型运行统计（运行中X台/异常Y台/未绑定Z台）

2. **AC2: 异常设备列表**
   - **Given** 查看模型状态区块
   - **When** 显示异常设备列表
   - **Then** 包含设备名称、异常类型、持续时间

3. **AC3: 跳转设备详情**
   - **Given** 点击异常设备
   - **When** 跳转到设备详情
   - **Then** 显示设备完整信息

4. **AC4: 异常高亮显示**
   - **Given** 设备存在异常
   - **When** 在列表中显示
   - **Then** 使用红色警示

## Dev Notes

### API 端点

- `GET /api/v1/monitoring/model-status`

### 响应格式

```json
{
  "stats": {
    "running": 50,
    "error": 5,
    "unbound": 20
  },
  "errorDevices": [
    {
      "id": "device-uuid",
      "name": "液位传感器-001",
      "errorType": "MODEL_CRASH",
      "duration": "2小时30分"
    }
  ]
}
```

---

# Story 4.14: 批量模型绑定

Status: planned

## Story

**As a** 运维人员,
**I want** 批量将模型绑定到多个设备,
**So that** 提高模型部署效率.

## Acceptance Criteria

1. **AC1: 设备选择**
   - **Given** 运维人员在设备列表页面
   - **When** 勾选多个同类型设备
   - **Then** 系统记录选中设备

2. **AC2: 模型兼容性验证**
   - **Given** 选择目标模型
   - **When** 系统验证兼容性
   - **Then** 不兼容的设备被排除

3. **AC3: 批量任务创建**
   - **Given** 验证通过
   - **When** 创建批量绑定任务
   - **Then** 系统创建批量任务记录

4. **AC4: 进度更新**
   - **Given** 任务执行中
   - **When** 系统逐台下发绑定指令
   - **Then** 实时更新进度

5. **AC5: 结果展示**
   - **Given** 任务完成
   - **When** 显示结果
   - **Then** 显示成功X台，失败Y台，及失败原因

## Dev Notes

### API 端点

- `POST /api/v1/devices/batch-bind-model`

### 请求体

```json
{
  "deviceIds": ["device-uuid-1", "device-uuid-2"],
  "modelId": "model-uuid"
}
```

### 并发控制

- 每批最多50台设备

---

# Story 4.15: AI分析结果聚合统计

Status: planned

## Story

**As a** 系统,
**I want** 自动聚合AI分析结果数据,
**So that** 支持快速查询和统计分析.

## Acceptance Criteria

1. **AC1: 定时聚合任务**
   - **Given** AI分析结果持续写入
   - **When** 系统定时任务执行（每小时）
   - **Then** 更新物化视图

2. **AC2: 聚合数据内容**
   - **Given** 聚合执行
   - **When** 计算统计数据
   - **Then** 包含总分析次数、异常次数、平均置信度

3. **AC3: 多维度查询**
   - **Given** 物化视图已更新
   - **When** 查询聚合数据
   - **Then** 支持按项目、设备类型、时间维度查询

4. **AC4: 看板展示**
   - **Given** 聚合数据可用
   - **When** 加载看板
   - **Then** 快速展示统计数据

## Dev Notes

### 定时任务

```typescript
@Cron('0 * * * *')  // 每小时执行
async aggregateAnalysisResults() {
  // 聚合逻辑
}
```

### ClickHouse 物化视图

```sql
CREATE MATERIALIZED VIEW ai_results_hourly_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMMDD(hour)
ORDER BY (hour, project_id, device_type, analysis_type)
AS SELECT
  toStartOfHour(timestamp) as hour,
  project_id,
  device_type,
  analysis_type,
  count() as total_count,
  sumIf(1, analysis_result = 'abnormal') as abnormal_count,
  avg(confidence) as avg_confidence
FROM ai_analysis_results
GROUP BY hour, project_id, device_type, analysis_type
```

### API 端点

- `GET /api/v1/statistics/ai-results`

---

**Implementation Status:**
- Story 4.11: Entity created, Service methods in progress
- Stories 4.12-4.15: Planned, require additional implementation

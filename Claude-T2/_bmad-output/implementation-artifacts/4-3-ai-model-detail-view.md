# Story 4.3: AI模型详情查看

Status: completed

## Story

**As a** 运维人员,
**I want** 查看AI模型的详细信息,
**So that** 了解模型能力、适用场景和使用说明.

## Acceptance Criteria

1. **AC1: 基本信息展示**
   - **Given** 运维人员在模型列表中点击某个模型
   - **When** 进入模型详情页面
   - **Then** 系统显示模型基本信息（名称、版本、类型、描述、创建时间）

2. **AC2: 技术规格展示**
   - **Given** 运维人员查看模型详情
   - **When** 页面加载完成
   - **Then** 系统显示技术规格（模型大小、推理延迟、输入数据要求）

3. **AC3: 适用设备类型展示**
   - **Given** 运维人员查看模型详情
   - **When** 页面加载完成
   - **Then** 系统显示适用设备类型列表

4. **AC4: 已绑定设备列表**
   - **Given** 运维人员查看模型详情
   - **When** 页面加载完成
   - **Then** 系统显示已绑定设备列表（分页，最多显示20条）

## Tasks / Subtasks

- [x] **Task 1: 更新 findOneDetail 服务方法** (AC: 1-4)
  - [x] 1.1 添加 findOneDetail 方法
  - [x] 1.2 返回结构化响应（model, specs, applicableDeviceTypes, bindings）
  - [x] 1.3 支持分页查询绑定设备

- [x] **Task 2: 更新控制器端点** (AC: 1-4)
  - [x] 2.1 更新 findOne 端点返回详细响应
  - [x] 2.2 添加分页参数支持

- [x] **Task 3: 编写单元测试** (AC: 1-4)
  - [x] 3.1 测试返回模型详情和绑定列表
  - [x] 3.2 测试分页功能
  - [x] 3.3 测试模型不存在的情况

## Dev Notes

### API 端点

- `GET /api/v1/ai-models/{modelId}?page=1&pageSize=20`

### 响应格式

```json
{
  "model": {
    "id": "uuid",
    "name": "错混接检测模型",
    "code": "MIXED_CONNECTION_V2",
    "version": "v2.1.0",
    "type": "mixed_connection",
    "description": "基于深度学习的管网错混接智能检测模型",
    "status": "published",
    "createdAt": "2024-01-15T10:00:00Z"
  },
  "specs": {
    "size": "20MB",
    "latency": "≤100ms",
    "input": "水位、流量数据",
    "output": "分析结果及置信度"
  },
  "applicableDeviceTypes": ["WATER_LEVEL_SENSOR", "FLOW_METER"],
  "bindings": {
    "items": [
      {
        "id": "binding-uuid",
        "deviceId": "device-uuid",
        "device": { "id": "device-uuid", "name": "设备1" },
        "status": "running",
        "boundAt": "2024-01-15T10:00:00Z"
      }
    ],
    "total": 15,
    "page": 1,
    "pageSize": 20
  }
}
```

### References

- [Source: epics.md#Story 4.3] - 原始Story定义
- [Source: FR16] - AI模型详情查看功能需求

---

**Story Implemented:** 2026-04-08

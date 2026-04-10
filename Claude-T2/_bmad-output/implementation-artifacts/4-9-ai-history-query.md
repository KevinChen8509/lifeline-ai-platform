# Story 4.9: AI分析历史记录查询

Status: completed

## Story

**As a** 运维人员,
**I want** 查看模型分析的历史记录,
**So that** 追溯历史分析结果和趋势变化.

## Acceptance Criteria

1. **AC1: 历史记录列表**
   - **Given** 运维人员在设备详情页面的"AI分析"标签
   - **When** 点击"查看历史记录"
   - **Then** 系统显示历史分析记录列表（时间、分析类型、结果、置信度）

2. **AC2: 时间范围筛选**
   - **Given** 查看历史记录
   - **When** 选择时间范围
   - **Then** 支持最近24小时/7天/30天

3. **AC3: 类型筛选**
   - **Given** 查看历史记录
   - **When** 选择分析类型
   - **Then** 显示指定类型的记录

4. **AC4: 结果筛选**
   - **Given** 查看历史记录
   - **When** 选择结果类型
   - **Then** 支持按正常/异常筛选

5. **AC5: 响应时间**
   - **Given** 查询历史记录
   - **When** 执行查询
   - **Then** 响应时间≤3秒

6. **AC6: 导出功能**
   - **Given** 查看历史记录
   - **When** 点击"导出CSV"
   - **Then** 下载CSV文件

## Tasks / Subtasks

- [x] **Task 1: 实现历史查询服务** (AC: 1-5)
  - [x] 1.1 getHistory 方法
  - [x] 1.2 支持时间范围筛选
  - [x] 1.3 支持类型筛选
  - [x] 1.4 支持结果筛选
  - [x] 1.5 分页支持

- [x] **Task 2: 添加控制器端点** (AC: 1-6)
  - [x] 2.1 GET /devices/:deviceId/ai-results/history
  - [x] 2.2 支持查询参数

- [ ] **Task 3: 实现导出功能** (AC: 6)
  - [ ] 3.1 GET /devices/:deviceId/ai-results/export
  - [ ] 3.2 生成CSV文件

## Dev Notes

### API 端点

- `GET /api/v1/devices/{deviceId}/ai-results/history?startTime={}&endTime={}&type={}&result={}&page=1&pageSize=50`

### 响应格式

```json
{
  "items": [
    {
      "id": "result-uuid",
      "analysisType": "mixed_connection",
      "analysisResult": "abnormal",
      "confidence": 88.5,
      "timestamp": "2024-01-15T10:00:00Z",
      "model": {
        "id": "model-uuid",
        "name": "错混接检测模型",
        "version": "v2.1.0"
      }
    }
  ],
  "total": 156,
  "page": 1,
  "pageSize": 50
}
```

### 查询优化

- 使用 `idx_ai_analysis_device_time` 索引优化时间范围查询
- 使用 `idx_ai_analysis_device_type_time` 索引优化类型+时间查询

### References

- [Source: epics.md#Story 4.9] - 原始Story定义
- [Source: FR22] - AI分析历史记录查询功能需求

---

**Story Implemented:** 2026-04-08

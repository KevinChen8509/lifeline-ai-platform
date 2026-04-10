# Story 4.8: AI分析置信度展示

Status: completed

## Story

**As a** 运维人员,
**I want** 查看AI分析的置信度,
**So that** 评估分析结果的可信程度.

## Acceptance Criteria

1. **AC1: 置信度显示**
   - **Given** AI分析结果已显示
   - **When** 运维人员查看分析结果
   - **Then** 每个分析结果显示置信度百分比（如92%）

2. **AC2: 颜色区分**
   - **Given** 置信度已显示
   - **When** 根据置信度范围
   - **Then** 高≥90%绿色、中70-89%黄色、低<70%红色

3. **AC3: 人工复核提示**
   - **Given** 置信度低于80%
   - **When** 显示分析结果
   - **Then** 显示"建议人工复核"提示

4. **AC4: 置信度详情**
   - **Given** 运维人员点击置信度
   - **When** 展开详情
   - **Then** 显示影响因素（数据质量、模型版本、近期异常）

## Tasks / Subtasks

- [x] **Task 1: 实体添加置信度字段** (AC: 1-4)
  - [x] 1.1 confidence 字段 (decimal 5,2)
  - [x] 1.2 confidenceFactors JSONB字段

- [x] **Task 2: 服务层支持** (AC: 1-4)
  - [x] 2.1 创建结果时保存置信度
  - [x] 2.2 创建结果时保存影响因素

- [x] **Task 3: API响应包含置信度** (AC: 1-4)
  - [x] 3.1 最新结果API返回置信度
  - [x] 3.2 历史记录API返回置信度
  - [x] 3.3 统计API返回平均置信度

## Dev Notes

### 置信度颜色映射

| 范围 | 等级 | 颜色 | CSS变量 |
|------|------|------|---------|
| ≥90% | 高 | 绿色 | --confidence-high |
| 70-89% | 中 | 黄色 | --confidence-medium |
| <70% | 低 | 红色 | --confidence-low |

### 置信度影响因素结构

```json
{
  "confidenceFactors": {
    "dataQuality": 0.95,
    "modelVersion": "v2.1.0",
    "recentAnomalies": 2,
    "signalStrength": 0.88,
    "dataCompleteness": 0.92
  }
}
```

### 前端组件

- `ConfidenceBadge.vue` - 显示置信度徽章
- `ConfidenceDetails.vue` - 展开详情面板

### References

- [Source: epics.md#Story 4.8] - 原始Story定义
- [Source: FR21] - AI分析置信度展示功能需求

---

**Story Implemented:** 2026-04-08

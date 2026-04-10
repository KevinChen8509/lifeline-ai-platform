# Story 4.2: AI模型列表查看

Status: completed

## Story

**As a** 运维人员,
**I want** 查看可用的AI模型列表及模型说明,
**So that** 了解系统能提供哪些AI分析能力.

## Acceptance Criteria

1. **AC1: 模型列表展示**
   - **Given** 运维人员已登录系统
   - **When** 运维人员进入模型管理页面
   - **Then** 系统显示模型列表（模型名称、类型、版本、状态、适用设备类型）

2. **AC2: 类型筛选**
   - **Given** 运维人员在模型列表页面
   - **When** 选择按类型筛选
   - **Then** 系统支持按模型类型筛选（错混接/淤堵/溢流/满管）

3. **AC3: 状态筛选**
   - **Given** 运维人员在模型列表页面
   - **When** 选择按状态筛选
   - **Then** 系统支持按状态筛选（已发布/已下线）

4. **AC4: 名称搜索**
   - **Given** 运维人员在模型列表页面
   - **When** 输入搜索关键词
   - **Then** 系统支持按模型名称搜索

5. **AC5: 设备数量显示**
   - **Given** 运维人员查看模型列表
   - **When** 列表加载完成
   - **Then** 显示每个模型已绑定的设备数量

## Tasks / Subtasks

- [x] **Task 1: 更新 findAll 服务方法** (AC: 1, 5)
  - [x] 1.1 添加设备数量查询
  - [x] 1.2 返回 items 包含 deviceCount 字段

- [x] **Task 2: 更新控制器返回类型** (AC: 1)
  - [x] 2.1 更新 findAll 返回类型定义

- [x] **Task 3: 编写单元测试** (AC: 1-5)
  - [x] 3.1 测试列表返回包含设备数量
  - [x] 3.2 测试按类型筛选
  - [x] 3.3 测试按状态筛选
  - [x] 3.4 测试搜索功能

## Dev Notes

### API 端点

- `GET /api/v1/ai-models?type={type}&status={status}&search={keyword}`

### 响应格式

```json
{
  "items": [
    {
      "id": "uuid",
      "name": "错混接检测模型",
      "code": "MIXED_CONNECTION_V2",
      "type": "mixed_connection",
      "version": "v2.1.0",
      "status": "published",
      "deviceCount": 15,
      "applicableDeviceTypes": ["WATER_LEVEL_SENSOR", "FLOW_METER"]
    }
  ],
  "total": 10,
  "page": 1,
  "pageSize": 20
}
```

### 设备数量查询

使用批量查询优化性能：
```typescript
async getDeviceCounts(modelIds: string[]): Promise<Record<string, number>> {
  const counts = await this.bindingRepository
    .createQueryBuilder('binding')
    .select('binding.modelId', 'modelId')
    .addSelect('COUNT(*)', 'count')
    .where('binding.modelId IN (:...modelIds)', { modelIds })
    .groupBy('binding.modelId')
    .getRawMany();

  return counts.reduce((acc, item) => {
    acc[item.modelId] = parseInt(item.count, 10);
    return acc;
  }, {});
}
```

### References

- [Source: epics.md#Story 4.2] - 原始Story定义
- [Source: FR16] - AI模型列表查看功能需求

---

**Story Implemented:** 2026-04-08

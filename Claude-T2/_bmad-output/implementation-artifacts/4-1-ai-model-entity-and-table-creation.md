# Story 4.1: AI模型实体与数据表创建

Status: completed

## Story

**As a** 开发团队,
**I want** 创建AI模型相关的数据表和实体,
**So that** 后续所有模型管理功能有数据存储基础.

## Acceptance Criteria

1. **AC1: AI模型表创建**
   - **Given** 数据库已初始化完成
   - **When** 执行数据库迁移
   - **Then** 创建 `ai_models` 表

2. **AC2: 设备-模型绑定表创建**
   - **Given** 数据库已初始化完成
   - **When** 执行数据库迁移
   - **Then** 创建 `device_model_bindings` 表

3. **AC3: 索引和外键**
   - **Given** 表已创建
   - **When** 迁移完成
   - **Then** 创建相关索引和外键约束

## Tasks / Subtasks

- [ ] **Task 1: 创建AI模型实体** (AC: 1)
  - [ ] 1.1 创建 ai-model.entity.ts
  - [ ] 1.2 定义模型类型枚举
  - [ ] 1.3 定义模型状态枚举

- [ ] **Task 2: 创建绑定实体** (AC: 2)
  - [ ] 2.1 创建 device-model-binding.entity.ts
  - [ ] 2.2 定义绑定状态枚举

## Dev Notes

### AI模型表结构

```sql
CREATE TABLE ai_models (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(100) NOT NULL,
  code VARCHAR(50) UNIQUE NOT NULL,
  version VARCHAR(20) NOT NULL,
  type VARCHAR(50) NOT NULL,
  description TEXT,
  file_url VARCHAR(500),
  file_size INTEGER,
  checksum VARCHAR(128),
  status VARCHAR(20) DEFAULT 'draft',
  specs JSONB DEFAULT '{}',
  applicable_device_types TEXT[] DEFAULT '{}',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 设备-模型绑定表结构

```sql
CREATE TABLE device_model_bindings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id UUID NOT NULL REFERENCES devices(id),
  model_id UUID NOT NULL REFERENCES ai_models(id),
  status VARCHAR(20) DEFAULT 'pending',
  bound_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  last_sync_at TIMESTAMP,
  UNIQUE(device_id, model_id)
);
```

### 模型类型枚举

| 类型 | 代码 | 说明 |
|------|------|------|
| 错混接分析 | MIXED_CONNECTION | 检测管网错混接 |
| 淤堵分析 | SILT | 检测管道淤堵 |
| 溢流分析 | OVERFLOW | 检测溢流风险 |
| 满管分析 | FULL_PIPE | 检测满管状态 |

### 模型状态枚举

| 状态 | 代码 | 说明 |
|------|------|------|
| 草稿 | DRAFT | 模型开发中 |
| 已发布 | PUBLISHED | 模型可用 |
| 已下线 | DEPRECATED | 模型已废弃 |

### References

- [Source: epics.md#Story 4.1] - 原始Story定义
- [Source: FR16-FR23] - AI模型管理功能需求

---

**Story Context Generated:** 2026-04-08

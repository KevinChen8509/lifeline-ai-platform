# Story 2.7: 设备按项目组织与分配

Status: done

## Story

**As a** 运维人员,
**I want** 将设备分配到指定项目,
**So that** 按项目维度组织和管理设备.

## Acceptance Criteria

1. **AC1: 设备实体创建**
   - **Given** 系统初始化
   - **When** 执行数据库迁移
   - **Then** 创建 devices 表（包含 project_id 字段）

2. **AC2: 单设备分配项目**
   - **Given** 运维人员选择设备
   - **When** 调用 PUT /devices/:id/project
   - **Then** 设备分配到指定项目

3. **AC3: 批量分配项目**
   - **Given** 运维人员选择多个设备
   - **When** 调用 POST /devices/batch-assign-project
   - **Then** 批量更新设备项目

4. **AC4: 权限检查**
   - **Given** 用户分配设备到项目
   - **When** 执行分配操作
   - **Then** 系统验证用户是目标项目成员

## Tasks / Subtasks

- [ ] **Task 1: 创建 Device 实体** (AC: 1)
  - [ ] 1.1 创建 device.entity.ts
  - [ ] 1.2 创建 DeviceModule

- [ ] **Task 2: 项目分配 API** (AC: 2, 3, 4)
  - [ ] 2.1 单设备分配端点
  - [ ] 2.2 批量分配端点
  - [ ] 2.3 权限检查

## Dev Notes

### Device 实体设计

```sql
CREATE TABLE devices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(100) NOT NULL,
  serial_number VARCHAR(50) UNIQUE NOT NULL,
  device_type VARCHAR(50),
  project_id UUID REFERENCES projects(id),
  status VARCHAR(20) DEFAULT 'pending',
  config JSONB DEFAULT '{}',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_devices_project ON devices(project_id);
CREATE INDEX idx_devices_status ON devices(status);
```

### API 设计

**单设备分配:**
```http
PUT /api/devices/:id/project
{
  "projectId": "project-uuid"
}
```

**批量分配:**
```http
POST /api/devices/batch-assign-project
{
  "deviceIds": ["uuid1", "uuid2"],
  "projectId": "project-uuid"
}
```

### References

- [Source: epics.md#Story 2.7] - 原始Story定义
- [Source: FR4] - 按项目组织设备功能需求

---

**Story Context Generated:** 2026-04-07

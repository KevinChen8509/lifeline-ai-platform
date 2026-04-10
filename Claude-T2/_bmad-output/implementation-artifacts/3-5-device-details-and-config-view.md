# Story 3.5: 设备详情与配置参数查看

Status: done

## Story

**As a** 运维人员,
**I want** 查看单个设备的详细信息和当前配置参数,
**So that** 了解设备运行情况和配置状态.

## Acceptance Criteria

1. **AC1: 设备基本信息**
   - **Given** 运维人员点击某个设备
   - **When** 进入设备详情页面
   - **Then** 显示设备编号、型号、厂商、安装位置、上线时间

2. **AC2: 配置参数显示**
   - **Given** 设备详情页面已加载
   - **When** 查看配置区域
   - **Then** 显示采集频次、上传频次、报警阈值

3. **AC3: 最新遥测数据**
   - **Given** 设备详情页面已加载
   - **When** 查看数据区域
   - **Then** 显示最新遥测数据（时间戳、数值）

## Tasks / Subtasks

- [x] **Task 1: 后端API** (AC: 1, 2, 3)
  - [x] 1.1 增强 findOne 方法
  - [x] 1.2 添加配置参数返回
  - [ ] 1.3 添加遥测数据查询

## Dev Notes

### API 设计

```http
GET /api/devices/:id
Response: {
  "id": "uuid",
  "name": "液位传感器-001",
  "serialNumber": "SN20240001",
  "deviceType": "WATER_LEVEL_SENSOR",
  "model": "LK-WL-100",
  "manufacturer": "生命线科技",
  "status": "online",
  "config": {
    "collectInterval": 15,
    "uploadInterval": 60,
    "alertThresholds": { "level": 1.0 }
  },
  "project": { ... },
  "lastOnlineAt": "2024-01-01T00:00:00Z",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

### 配置参数说明

| 参数 | 类型 | 说明 |
|------|------|------|
| collectInterval | number | 采集频次（分钟） |
| uploadInterval | number | 上传频次（分钟） |
| alertThresholds | object | 报警阈值配置 |

### References

- [Source: epics.md#Story 3.5] - 原始Story定义
- [Source: FR9] - 设备详情与配置参数查看功能需求

---

**Story Context Generated:** 2026-04-07

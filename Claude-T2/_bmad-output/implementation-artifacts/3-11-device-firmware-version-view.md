# Story 3.11: 设备固件版本查看

Status: done

## Story

**As a** 管理员,
**I want** 查看设备的当前固件版本信息,
**So that** 了解设备运行版本，判断是否需要升级.

## Acceptance Criteria

1. **AC1: 固件版本显示**
   - **Given** 管理员在设备详情页面
   - **When** 查看"固件信息"区块
   - **Then** 显示当前固件版本号

2. **AC2: 可升级版本提示**
   - **Given** 有新版本可升级
   - **When** 查看固件信息
   - **Then** 显示是否有新版本可升级

## Implementation Notes

已在 Device 实体中添加 `firmwareVersion` 字段：

```typescript
@Column({
  name: 'firmware_version',
  length: 50,
  nullable: true,
  comment: '当前固件版本',
})
firmwareVersion: string | null;
```

通过 GET /devices/:id 端点即可获取设备详情，包含 firmwareVersion 字段。

### API 响应示例

```json
{
  "id": "uuid",
  "name": "液位传感器-001",
  "firmwareVersion": "v2.0.3",
  "status": "online",
  ...
}
```

## References

- [Source: epics.md#Story 3.11] - 原始Story定义
- [Source: FR15] - 设备固件版本查看功能需求

---

**Story Context Generated:** 2026-04-08

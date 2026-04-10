# Story 3.7: 设备报警阈值配置

Status: done

## Story

**As a** 运维人员,
**I want** 远程修改设备的报警阈值,
**So that** 根据现场实际情况调整预警触发条件.

## Acceptance Criteria

1. **AC1: 阈值验证**
   - **Given** 运维人员修改报警阈值
   - **When** 输入值超出范围
   - **Then** 系统显示验证错误

2. **AC2: 阈值下发**
   - **Given** 阈值验证通过，设备在线
   - **When** 提交配置
   - **Then** 系统通过MQTT下发阈值更新指令

3. **AC3: 边缘推理**
   - **Given** 设备确认接收
   - **When** 边缘AI推理
   - **Then** 使用新阈值进行推理

## Implementation Notes

已在 Story 3.6 的 `updateConfig` 方法中实现，通过 `alertThresholds` 参数支持：

```typescript
interface UpdateDeviceConfigDto {
  collectInterval?: number;
  uploadInterval?: number;
  alertThresholds?: {
    level?: number;    // 液位阈值
    flow?: number;     // 流量阈值
    pressure?: number; // 压力阈值
  };
}
```

### API 调用示例

```http
PUT /api/devices/:id/config
{
  "alertThresholds": {
    "level": 1.0,
    "flow": 50,
    "pressure": 0.5
  }
}
```

## References

- [Source: epics.md#Story 3.7] - 原始Story定义
- [Source: FR11] - 设备报警阈值配置功能需求

---

**Story Context Generated:** 2026-04-07

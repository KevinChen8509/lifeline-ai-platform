package com.project.subscription.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 设备原始数据事件 (Kafka topic: device.raw)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRawEvent {
    private Long deviceId;
    private String deviceKey;
    private Long productId;
    private String productKey;
    private Long tenantId;
    private Long timestamp;
    private Map<String, Object> dataPoints;  // identifier → value
}

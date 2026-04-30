package com.iot.platform.interfaces.http;

import com.iot.platform.domain.device.enums.DeviceType;
import com.iot.platform.domain.device.schema.DeviceSchemaConfig;
import com.iot.platform.domain.device.schema.DeviceSchemaConfig.DeviceSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备数据点 Schema 查询接口。
 * 供前端动态获取设备类型定义，驱动可视化卡片渲染。
 */
@RestController
@RequestMapping("/api/v1/device-schemas")
@RequiredArgsConstructor
public class DeviceSchemaController {

    private final DeviceSchemaConfig schemaConfig;

    @GetMapping
    public ApiResult<Map<String, Object>> listSchemas() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (DeviceSchema schema : schemaConfig.getAllSchemas()) {
            result.put(schema.getDeviceType().name(), toMap(schema));
        }
        return ApiResult.ok(result);
    }

    @GetMapping("/{deviceType}")
    public ApiResult<Map<String, Object>> getSchema(@PathVariable String deviceType) {
        DeviceType dt = DeviceType.valueOf(deviceType.toUpperCase());
        return ApiResult.ok(toMap(schemaConfig.getSchema(dt)));
    }

    private Map<String, Object> toMap(DeviceSchema schema) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceType", schema.getDeviceType().name());
        m.put("displayName", schema.getDisplayName());
        m.put("domain", schema.getDomain());
        m.put("primaryMetric", schema.getPrimaryMetric());
        m.put("dataPoints", schema.getDataPoints());
        return m;
    }
}

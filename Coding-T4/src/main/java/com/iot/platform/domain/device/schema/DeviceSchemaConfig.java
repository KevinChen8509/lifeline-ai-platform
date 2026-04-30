package com.iot.platform.domain.device.schema;

import com.iot.platform.domain.device.enums.DeviceType;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

/**
 * 从 device-schemas.yml 加载设备数据点 Schema。
 * 提供按设备类型查询 Schema 和校验数据点合法性。
 */
@Slf4j
@Component
public class DeviceSchemaConfig {

    private final Map<DeviceType, DeviceSchema> schemas = new EnumMap<>(DeviceType.class);

    @PostConstruct
    public void loadSchemas() {
        Yaml yaml = new Yaml();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("device-schemas.yml")) {
            if (is == null) {
                log.error("device-schemas.yml not found in classpath");
                return;
            }
            Map<String, Object> root = yaml.load(is);
            for (Map.Entry<String, Object> entry : root.entrySet()) {
                String typeCode = entry.getKey();
                DeviceType deviceType;
                try {
                    deviceType = DeviceType.valueOf(typeCode);
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown device type in schema: {}", typeCode);
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> typeDef = (Map<String, Object>) entry.getValue();
                DeviceSchema schema = parseSchema(deviceType, typeDef);
                schemas.put(deviceType, schema);
            }
            log.info("Loaded {} device schemas: {}", schemas.size(), schemas.keySet());
        } catch (Exception e) {
            log.error("Failed to load device schemas", e);
        }
    }

    @SuppressWarnings("unchecked")
    private DeviceSchema parseSchema(DeviceType deviceType, Map<String, Object> typeDef) {
        DeviceSchema schema = new DeviceSchema();
        schema.setDeviceType(deviceType);
        schema.setDisplayName((String) typeDef.get("displayName"));
        schema.setDomain((String) typeDef.get("domain"));
        schema.setPrimaryMetric((String) typeDef.get("primaryMetric"));

        Map<String, Object> pointsMap = (Map<String, Object>) typeDef.get("dataPoints");
        Map<String, DataPointSchema> points = new LinkedHashMap<>();
        for (Map.Entry<String, Object> pe : pointsMap.entrySet()) {
            Map<String, Object> pDef = (Map<String, Object>) pe.getValue();
            DataPointSchema dps = new DataPointSchema();
            dps.setKey(pe.getKey());
            dps.setDisplayName((String) pDef.get("displayName"));
            dps.setUnit((String) pDef.get("unit"));
            dps.setDataType((String) pDef.get("dataType"));
            dps.setPrimary(Boolean.TRUE.equals(pDef.get("primary")));
            dps.setRangeMin(((Number) pDef.get("rangeMin")).doubleValue());
            dps.setRangeMax(((Number) pDef.get("rangeMax")).doubleValue());
            points.put(pe.getKey(), dps);
        }
        schema.setDataPoints(points);
        return schema;
    }

    public DeviceSchema getSchema(DeviceType type) {
        DeviceSchema s = schemas.get(type);
        if (s == null) throw new IllegalArgumentException("No schema for device type: " + type);
        return s;
    }

    public Collection<DeviceSchema> getAllSchemas() {
        return schemas.values();
    }

    /**
     * 校验上传数据点是否符合 Schema。
     * 返回校验结果：合法数据 + 非法字段错误列表。
     */
    public ValidationResult validate(DeviceType type, Map<String, Object> rawData) {
        DeviceSchema schema = getSchema(type);
        Map<String, Object> clean = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, Object> entry : rawData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            DataPointSchema dps = schema.getDataPoints().get(key);

            if (dps == null) {
                errors.add("Unknown data point '" + key + "' for device type " + type.name());
                continue;
            }

            if (value instanceof Number num) {
                double d = num.doubleValue();
                if (d < dps.getRangeMin() || d > dps.getRangeMax()) {
                    errors.add(key + " out of range [" + dps.getRangeMin() + "," + dps.getRangeMax() + "]: " + d);
                    continue;
                }
                clean.put(key, "INTEGER".equals(dps.getDataType()) ? num.intValue() : d);
            } else {
                clean.put(key, value);
            }
        }

        // 检查主指标
        if (!clean.containsKey(schema.getPrimaryMetric())) {
            errors.add("Missing primary metric: " + schema.getPrimaryMetric());
        }

        return new ValidationResult(clean, errors, errors.isEmpty());
    }

    @Data
    public static class DeviceSchema {
        private DeviceType deviceType;
        private String displayName;
        private String domain;
        private String primaryMetric;
        private Map<String, DataPointSchema> dataPoints;
    }

    @Data
    public static class DataPointSchema {
        private String key;
        private String displayName;
        private String unit;
        private String dataType;
        private boolean primary;
        private double rangeMin;
        private double rangeMax;
    }

    @Data
    public static class ValidationResult {
        private final Map<String, Object> cleanData;
        private final List<String> errors;
        private final boolean valid;
    }
}

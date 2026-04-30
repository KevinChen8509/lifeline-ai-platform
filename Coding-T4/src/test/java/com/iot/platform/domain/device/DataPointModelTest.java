package com.iot.platform.domain.device;

import com.iot.platform.domain.device.engine.ThresholdEvaluator;
import com.iot.platform.domain.device.enums.DataPointId;
import com.iot.platform.domain.device.enums.DeviceType;
import com.iot.platform.domain.device.service.DataPointProviderRegistry;
import com.iot.platform.domain.device.service.DeviceTypeResolver;
import com.iot.platform.domain.device.spi.DataPointProvider;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 排水设备数据点建模测试套件。
 * AC-001 ~ AC-009
 */
class DataPointModelTest {

    // ── AC-001: DeviceType 枚举 ──────────────────────────

    @Nested
    class DeviceTypeTest {

        @Test
        void fromDeviceCode_LC_prefix_returns_LEVEL_CONTROLLER() {
            assertEquals(DeviceType.LC, DeviceType.fromDeviceCode("LC0101000100001"));
        }

        @Test
        void fromDeviceCode_LL_prefix_returns_FLOW_METER() {
            assertEquals(DeviceType.LL, DeviceType.fromDeviceCode("LL0101000100001"));
        }

        @Test
        void fromDeviceCode_YL_prefix_returns_RAIN_GAUGE() {
            assertEquals(DeviceType.YL, DeviceType.fromDeviceCode("YL0101000100001"));
        }

        @Test
        void fromDeviceCode_SZ_prefix_returns_WATER_QUALITY() {
            assertEquals(DeviceType.SZ, DeviceType.fromDeviceCode("SZ0101000100001"));
        }

        @Test
        void fromDeviceCode_tooShort_throws() {
            assertThrows(IllegalArgumentException.class,
                () -> DeviceType.fromDeviceCode("LC001234"));
        }

        @Test
        void fromDeviceCode_null_throws() {
            assertThrows(IllegalArgumentException.class,
                () -> DeviceType.fromDeviceCode(null));
        }

        @Test
        void fromDeviceCode_unknownPrefix_throws() {
            assertThrows(IllegalArgumentException.class,
                () -> DeviceType.fromDeviceCode("ZZ01010001000001"));
        }

        @Test
        void lc_primaryMetric_is_water_level() {
            assertEquals("water_level", DeviceType.LC.getPrimaryMetric());
        }

        @Test
        void sz_primaryMetric_is_cod() {
            assertEquals("cod", DeviceType.SZ.getPrimaryMetric());
        }
    }

    // ── AC-002: DataPointId 枚举 ──────────────────────────

    @Nested
    class DataPointIdTest {

        @Test
        void resolve_waterLevel_LC() {
            assertEquals(DataPointId.WATER_LEVEL,
                DataPointId.resolve("water_level", DeviceType.LC));
        }

        @Test
        void resolve_waterLevel_LL_returnsFlowVersion() {
            // LL 也有 water_level，但应该是 FLOW_WATER_LEVEL
            assertEquals(DataPointId.FLOW_WATER_LEVEL,
                DataPointId.resolve("water_level", DeviceType.LL));
        }

        @Test
        void resolve_battery_anyDevice_returnsShared() {
            assertEquals(DataPointId.BATTERY,
                DataPointId.resolve("battery", DeviceType.LC));
            assertEquals(DataPointId.BATTERY,
                DataPointId.resolve("battery", DeviceType.SZ));
        }

        @Test
        void resolve_unknownKey_throws() {
            assertThrows(IllegalArgumentException.class,
                () -> DataPointId.resolve("nonexistent", DeviceType.LC));
        }

        @Test
        void belongsTo_shared_trueForAll() {
            assertTrue(DataPointId.BATTERY.belongsTo(DeviceType.LC));
            assertTrue(DataPointId.BATTERY.belongsTo(DeviceType.SZ));
        }

        @Test
        void belongsTo_specific_falseForOther() {
            assertFalse(DataPointId.WATER_LEVEL.belongsTo(DeviceType.LL));
            assertTrue(DataPointId.WATER_LEVEL.belongsTo(DeviceType.LC));
        }

        @Test
        void isInRange_normalValue() {
            assertTrue(DataPointId.WATER_LEVEL.isInRange(2.35));
        }

        @Test
        void isInRange_outOfRange() {
            assertFalse(DataPointId.WATER_LEVEL.isInRange(60.0));
        }

        @Test
        void isInRange_ph_boundary() {
            assertTrue(DataPointId.PH.isInRange(0.0));
            assertTrue(DataPointId.PH.isInRange(14.0));
            assertFalse(DataPointId.PH.isInRange(14.1));
        }
    }

    // ── AC-006: DeviceTypeResolver ──────────────────────────

    @Nested
    class DeviceTypeResolverTest {

        private final DeviceTypeResolver resolver = new DeviceTypeResolver();

        @Test
        void resolve_fullParsing() {
            var result = resolver.resolve("LC0101000100001");
            assertEquals("LC010100", result.getModel());
            assertEquals("01", result.getVendorCode());
            assertEquals("00001", result.getSequence());
            assertEquals(DeviceType.LC, result.getDeviceType());
        }

        @Test
        void resolve_null_throws() {
            assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(null));
        }

        @Test
        void resolve_wrongLength_throws() {
            assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("LC001"));
        }
    }

    // ── AC-005/AC-007: DataPointProvider + Registry ──────────

    @Nested
    class DataPointProviderTest {

        private final DataPointProviderRegistry registry = new DataPointProviderRegistry();

        @Test
        void registry_LC_returnsLevelController() {
            DataPointProvider p = registry.getProvider(DeviceType.LC);
            assertEquals(DeviceType.LC, p.getDeviceType());
            assertEquals("water_level", p.getPrimaryKey());
        }

        @Test
        void registry_SZ_returnsWaterQuality() {
            DataPointProvider p = registry.getProvider(DeviceType.SZ);
            assertEquals("cod", p.getPrimaryKey());
            assertTrue(p.getSupportedDataPointKeys().contains("ph"));
        }

        @Test
        void LC_validateAndClean_normal() {
            DataPointProvider p = registry.getProvider(DeviceType.LC);
            Map<String, Object> raw = Map.of(
                "water_level", 2.35,
                "air_height", 0.65,
                "battery", 85
            );
            Map<String, Object> clean = p.validateAndClean(raw);
            assertEquals(3, clean.size());
            assertEquals(2.35, clean.get("water_level"));
        }

        @Test
        void LC_validateAndClean_outOfRange_throws() {
            DataPointProvider p = registry.getProvider(DeviceType.LC);
            Map<String, Object> raw = Map.of("water_level", 60.0);
            assertThrows(IllegalArgumentException.class,
                () -> p.validateAndClean(raw));
        }

        @Test
        void LC_validateAndClean_unknownKeyFiltered() {
            DataPointProvider p = registry.getProvider(DeviceType.LC);
            Map<String, Object> raw = Map.of(
                "water_level", 2.35,
                "unknown_key", 999
            );
            Map<String, Object> clean = p.validateAndClean(raw);
            assertFalse(clean.containsKey("unknown_key"));
            assertEquals(1, clean.size());
        }

        @Test
        void SZ_validateAndClean_phOutOfRange_throws() {
            DataPointProvider p = registry.getProvider(DeviceType.SZ);
            Map<String, Object> raw = Map.of("ph", 14.1);
            assertThrows(IllegalArgumentException.class,
                () -> p.validateAndClean(raw));
        }

        @Test
        void SZ_validateAndClean_codNormal() {
            DataPointProvider p = registry.getProvider(DeviceType.SZ);
            Map<String, Object> raw = Map.of("cod", 35.0);
            Map<String, Object> clean = p.validateAndClean(raw);
            assertEquals(35.0, clean.get("cod"));
        }

        @Test
        void getUnit_returnsCorrectUnit() {
            DataPointProvider p = registry.getProvider(DeviceType.LC);
            assertEquals("m", p.getUnit("water_level"));
            assertEquals("%", p.getUnit("battery"));
        }
    }

    // ── AC-008: ThresholdEvaluator ──────────────────────────

    @Nested
    class ThresholdEvaluatorTest {

        private final ThresholdEvaluator evaluator = new ThresholdEvaluator();

        @Test
        void evaluate_doubleGT_true() {
            assertTrue(evaluator.evaluate(
                DataPointId.WATER_LEVEL, 5.0,
                ThresholdEvaluator.Operator.GT, 3.0));
        }

        @Test
        void evaluate_doubleGT_false() {
            assertFalse(evaluator.evaluate(
                DataPointId.WATER_LEVEL, 2.0,
                ThresholdEvaluator.Operator.GT, 3.0));
        }

        @Test
        void evaluate_doubleGTE_boundary() {
            assertTrue(evaluator.evaluate(
                DataPointId.WATER_LEVEL, 2.50,
                ThresholdEvaluator.Operator.GTE, 2.50));
        }

        @Test
        void evaluate_doubleLT() {
            assertTrue(evaluator.evaluate(
                DataPointId.AIR_HEIGHT, 0.20,
                ThresholdEvaluator.Operator.LT, 0.30));
        }

        @Test
        void evaluate_intEQ() {
            assertTrue(evaluator.evaluate(
                DataPointId.RAIN_STATUS, 3,
                ThresholdEvaluator.Operator.EQ, 3));
        }

        @Test
        void evaluate_codOverThreshold() {
            assertTrue(evaluator.evaluate(
                DataPointId.COD, 45.0,
                ThresholdEvaluator.Operator.GTE, 40.0));
        }

        @Test
        void evaluate_phInRange_notTriggered() {
            assertFalse(evaluator.evaluate(
                DataPointId.PH, 7.2,
                ThresholdEvaluator.Operator.GTE, 9.0));
        }
    }
}

import { ApiProperty } from '@nestjs/swagger';
import { IsString, IsNotEmpty } from 'class-validator';

/**
 * 扫码注册请求DTO
 */
export class ScanRegisterDto {
  @ApiProperty({
    description: '二维码数据',
    example: 'LK://SN20240001:WATER_LEVEL_SENSOR:FACTORY001',
  })
  @IsString()
  @IsNotEmpty({ message: '二维码数据不能为空' })
  qrData: string;
}

/**
 * 设备类型信息
 */
export interface DeviceTypeInfo {
  code: string;
  name: string;
  description: string;
  manufacturer: string;
}

/**
 * 设备类型映射表
 */
export const DEVICE_TYPE_MAP: Record<string, DeviceTypeInfo> = {
  WATER_LEVEL_SENSOR: {
    code: 'WATER_LEVEL_SENSOR',
    name: '液位传感器',
    description: '用于水位监测',
    manufacturer: '生命线科技',
  },
  FLOW_METER: {
    code: 'FLOW_METER',
    name: '流量计',
    description: '用于流量监测',
    manufacturer: '生命线科技',
  },
  PRESSURE_SENSOR: {
    code: 'PRESSURE_SENSOR',
    name: '压力传感器',
    description: '用于压力监测',
    manufacturer: '生命线科技',
  },
  TEMPERATURE_SENSOR: {
    code: 'TEMPERATURE_SENSOR',
    name: '温度传感器',
    description: '用于温度监测',
    manufacturer: '生命线科技',
  },
  HUMIDITY_SENSOR: {
    code: 'HUMIDITY_SENSOR',
    name: '湿度传感器',
    description: '用于湿度监测',
    manufacturer: '生命线科技',
  },
};

/**
 * 获取设备类型信息
 */
export function getDeviceInfoByType(deviceType: string): DeviceTypeInfo | null {
  return DEVICE_TYPE_MAP[deviceType] || null;
}

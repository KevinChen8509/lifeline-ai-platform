import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsString,
  IsNotEmpty,
  IsOptional,
  MaxLength,
  MinLength,
  Matches,
  IsEnum,
} from 'class-validator';
import { DeviceSource, DeviceProtocol } from '../device.entity';

/**
 * 创建设备请求DTO
 */
export class CreateDeviceDto {
  @ApiProperty({
    description: '设备名称',
    example: '第三方液位计',
    maxLength: 100,
  })
  @IsString()
  @IsNotEmpty({ message: '设备名称不能为空' })
  @MaxLength(100, { message: '设备名称不能超过100个字符' })
  name: string;

  @ApiProperty({
    description: '设备序列号（全局唯一）',
    example: 'TP20240001',
    maxLength: 50,
  })
  @IsString()
  @IsNotEmpty({ message: '设备序列号不能为空' })
  @MaxLength(50, { message: '设备序列号不能超过50个字符' })
  @Matches(/^[A-Za-z0-9_-]+$/, { message: '设备序列号只能包含字母、数字、下划线和横线' })
  serialNumber: string;

  @ApiPropertyOptional({
    description: '设备类型',
    example: 'WATER_LEVEL_SENSOR',
    maxLength: 50,
  })
  @IsString()
  @IsOptional()
  @MaxLength(50)
  deviceType?: string;

  @ApiPropertyOptional({
    description: '设备型号',
    example: 'TP-WL-100',
    maxLength: 100,
  })
  @IsString()
  @IsOptional()
  @MaxLength(100)
  model?: string;

  @ApiPropertyOptional({
    description: '制造商',
    example: '第三方厂商',
    maxLength: 100,
  })
  @IsString()
  @IsOptional()
  @MaxLength(100)
  manufacturer?: string;

  @ApiPropertyOptional({
    description: '设备来源',
    enum: DeviceSource,
    default: DeviceSource.THIRD_PARTY,
  })
  @IsEnum(DeviceSource, { message: '设备来源值无效' })
  @IsOptional()
  source?: DeviceSource;

  @ApiPropertyOptional({
    description: '通信协议',
    enum: DeviceProtocol,
  })
  @IsEnum(DeviceProtocol, { message: '通信协议值无效' })
  @IsOptional()
  protocol?: DeviceProtocol;

  @ApiPropertyOptional({
    description: '设备描述',
    example: '这是一个第三方液位监测设备',
  })
  @IsString()
  @IsOptional()
  description?: string;

  @ApiPropertyOptional({
    description: '项目ID',
    example: 'uuid',
  })
  @IsString()
  @IsOptional()
  projectId?: string;
}

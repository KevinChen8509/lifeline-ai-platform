import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsNumber,
  IsOptional,
  Min,
  Max,
  IsObject,
  ValidateNested,
  IsArray,
  ArrayNotEmpty,
  ArrayMaxSize,
  IsUUID,
} from 'class-validator';
import { Type } from 'class-transformer';

/**
 * 报警阈值配置
 */
class AlertThresholdsDto {
  @ApiPropertyOptional({
    description: '液位阈值（米）',
    example: 1.0,
  })
  @IsNumber()
  @IsOptional()
  level?: number;

  @ApiPropertyOptional({
    description: '流量阈值（立方米/小时）',
    example: 50,
  })
  @IsNumber()
  @IsOptional()
  flow?: number;

  @ApiPropertyOptional({
    description: '压力阈值（MPa）',
    example: 0.5,
  })
  @IsNumber()
  @IsOptional()
  pressure?: number;
}

/**
 * 更新设备配置DTO
 */
export class UpdateDeviceConfigDto {
  @ApiPropertyOptional({
    description: '采集频次（分钟）',
    example: 15,
    minimum: 1,
    maximum: 60,
  })
  @IsNumber()
  @IsOptional()
  @Min(1, { message: '采集频次最小为1分钟' })
  @Max(60, { message: '采集频次最大为60分钟' })
  collectInterval?: number;

  @ApiPropertyOptional({
    description: '上传频次（分钟）',
    example: 60,
    minimum: 5,
    maximum: 1440,
  })
  @IsNumber()
  @IsOptional()
  @Min(5, { message: '上传频次最小为5分钟' })
  @Max(1440, { message: '上传频次最大为1440分钟（24小时）' })
  uploadInterval?: number;

  @ApiPropertyOptional({
    description: '报警阈值配置',
    type: AlertThresholdsDto,
  })
  @IsObject()
  @IsOptional()
  @ValidateNested()
  @Type(() => AlertThresholdsDto)
  alertThresholds?: AlertThresholdsDto;
}

/**
 * 批量更新设备配置DTO
 */
export class BatchUpdateConfigDto {
  @ApiProperty({
    description: '设备ID列表',
    type: [String],
    example: ['uuid1', 'uuid2'],
    maxItems: 100,
  })
  @IsArray()
  @ArrayNotEmpty({ message: '设备ID列表不能为空' })
  @ArrayMaxSize(100, { message: '最多同时批量操作100台设备' })
  @IsUUID('4', { each: true, message: '每个设备ID必须是有效的UUID' })
  deviceIds: string[];

  @ApiProperty({
    description: '设备配置',
    type: UpdateDeviceConfigDto,
  })
  @ValidateNested()
  @Type(() => UpdateDeviceConfigDto)
  config: UpdateDeviceConfigDto;
}

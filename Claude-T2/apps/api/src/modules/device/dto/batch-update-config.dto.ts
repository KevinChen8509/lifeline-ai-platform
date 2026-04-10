import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsArray,
  ArrayNotEmpty,
  ArrayMaxSize,
  IsUUID,
  ValidateNested,
} from 'class-validator';
import { Type } from 'class-transformer';
import { UpdateDeviceConfigDto } from './update-config.dto';

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

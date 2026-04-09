import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsArray,
  IsString,
  IsNotEmpty,
  IsUUID,
  ArrayMaxSize,
} from 'class-validator';

export class BatchBindModelDto {
  @ApiProperty({
    description: '目标设备ID列表',
    example: ['device-uuid-1', 'device-uuid-2'],
    type: [String],
  })
  @IsArray()
  @IsUUID('4', { each: true })
  @IsNotEmpty()
  @ArrayMaxSize(50, { message: '每批最多50台设备' })
  deviceIds: string[];

  @ApiProperty({
    description: '模型ID',
    example: 'model-uuid',
  })
  @IsUUID()
  @IsNotEmpty()
  modelId: string;
}

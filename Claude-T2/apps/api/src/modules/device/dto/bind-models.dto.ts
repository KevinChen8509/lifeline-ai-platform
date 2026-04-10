import { ApiProperty } from '@nestjs/swagger';
import { IsArray, IsString, IsNotEmpty, ArrayNotEmpty } from 'class-validator';

/**
 * 绑定AI模型到设备DTO
 */
export class BindModelsDto {
  @ApiProperty({
    description: '要绑定的模型ID列表',
    example: ['model-uuid-1', 'model-uuid-2'],
    type: [String],
  })
  @IsArray()
  @ArrayNotEmpty({ message: '模型ID列表不能为空' })
  @IsString({ each: true, message: '模型ID必须是字符串' })
  modelIds: string[];
}

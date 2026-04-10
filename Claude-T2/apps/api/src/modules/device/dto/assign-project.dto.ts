import { ApiProperty } from '@nestjs/swagger';
import { IsUUID, IsArray, ArrayNotEmpty } from 'class-validator';

export class AssignProjectDto {
  @ApiProperty({
    description: '项目ID',
    example: 'uuid',
  })
  @IsUUID()
  projectId: string | null;
}

export class BatchAssignProjectDto {
  @ApiProperty({
    description: '设备ID列表',
    example: ['uuid1', 'uuid2'],
    type: [String],
  })
  @IsArray()
  @ArrayNotEmpty({ message: '设备ID列表不能为空' })
  @IsUUID('4', { each: true, message: '每个设备ID必须是有效的UUID' })
  deviceIds: string[];

  @ApiProperty({
    description: '项目ID',
    example: 'uuid',
  })
  @IsUUID()
  projectId: string | null;
}

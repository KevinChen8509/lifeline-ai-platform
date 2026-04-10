import { ApiProperty, PartialType } from '@nestjs/swagger';
import { CreateProjectDto } from './create-project.dto';
import { IsOptional, IsEnum } from 'class-validator';
import { ProjectStatus } from '../project.entity';

export class UpdateProjectDto extends PartialType(CreateProjectDto) {
  @ApiProperty({
    description: '项目状态',
    enum: ProjectStatus,
    required: false,
  })
  @IsOptional()
  @IsEnum(ProjectStatus, { message: '无效的项目状态' })
  status?: ProjectStatus;
}

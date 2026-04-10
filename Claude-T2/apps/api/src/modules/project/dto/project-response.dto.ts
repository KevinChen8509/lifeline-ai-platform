import { ApiProperty } from '@nestjs/swagger';
import { ProjectStatus } from '../project.entity';

export class ProjectResponseDto {
  @ApiProperty({ description: '项目ID', example: 'uuid' })
  id: string;

  @ApiProperty({ description: '项目名称', example: '智慧水务监测项目' })
  name: string;

  @ApiProperty({ description: '项目编码', example: 'WATER2024' })
  code: string;

  @ApiProperty({ description: '项目描述', example: '城市供水管网智能监测系统', nullable: true })
  description: string | null;

  @ApiProperty({ description: '项目配置', example: {} })
  settings: Record<string, any>;

  @ApiProperty({ description: '项目状态', enum: ProjectStatus, example: ProjectStatus.ACTIVE })
  status: ProjectStatus;

  @ApiProperty({ description: '创建时间', example: '2026-04-03T10:00:00Z' })
  createdAt: Date;

  @ApiProperty({ description: '更新时间', example: '2026-04-03T10:00:00Z' })
  updatedAt: Date;
}

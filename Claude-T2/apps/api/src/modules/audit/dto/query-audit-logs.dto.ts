import { IsOptional, IsDateString, IsUUID, IsString, IsInt, Min, Max } from 'class-validator';
import { Type } from 'class-transformer';
import { ApiPropertyOptional } from '@nestjs/swagger';

export class QueryAuditLogsDto {
  @ApiPropertyOptional({ description: '开始时间 (ISO 8601格式)' })
  @IsOptional()
  @IsDateString()
  startTime?: string;

  @ApiPropertyOptional({ description: '结束时间 (ISO 8601格式)' })
  @IsOptional()
  @IsDateString()
  endTime?: string;

  @ApiPropertyOptional({ description: '操作者用户ID' })
  @IsOptional()
  @IsUUID()
  userId?: string;

  @ApiPropertyOptional({ description: '操作类型 (LOGIN, LOGOUT, CREATE_USER, etc.)' })
  @IsOptional()
  @IsString()
  action?: string;

  @ApiPropertyOptional({ description: '目标类型 (User, Role, Project, etc.)' })
  @IsOptional()
  @IsString()
  targetType?: string;

  @ApiPropertyOptional({ description: '目标ID' })
  @IsOptional()
  @IsUUID()
  targetId?: string;

  @ApiPropertyOptional({ description: '页码', default: 1 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page?: number = 1;

  @ApiPropertyOptional({ description: '每页数量', default: 20 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(100)
  pageSize?: number = 20;
}

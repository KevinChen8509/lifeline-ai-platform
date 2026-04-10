import { ApiProperty } from '@nestjs/swagger';

class OperatorDto {
  @ApiProperty({ description: '用户ID' })
  id: string;

  @ApiProperty({ description: '用户名' })
  username: string;

  @ApiProperty({ description: '姓名' })
  name: string;
}

export class AuditLogResponseDto {
  @ApiProperty({ description: '日志ID' })
  id: string;

  @ApiProperty({ description: '操作类型' })
  action: string;

  @ApiProperty({ description: '目标类型' })
  targetType: string;

  @ApiProperty({ description: '目标ID', nullable: true })
  targetId: string | null;

  @ApiProperty({ description: '操作者ID' })
  operatorId: string;

  @ApiProperty({ description: '操作者信息', nullable: true })
  operator: OperatorDto | null;

  @ApiProperty({ description: '变更前的值', nullable: true })
  oldValue: Record<string, any> | null;

  @ApiProperty({ description: '变更后的值', nullable: true })
  newValue: Record<string, any> | null;

  @ApiProperty({ description: '操作描述', nullable: true })
  description: string | null;

  @ApiProperty({ description: 'IP地址', nullable: true })
  ipAddress: string | null;

  @ApiProperty({ description: 'User-Agent', nullable: true })
  userAgent: string | null;

  @ApiProperty({ description: '创建时间' })
  createdAt: Date;
}

export class AuditLogListResponseDto {
  @ApiProperty({ description: '日志列表', type: [AuditLogResponseDto] })
  items: AuditLogResponseDto[];

  @ApiProperty({ description: '总数' })
  total: number;

  @ApiProperty({ description: '当前页码' })
  page: number;

  @ApiProperty({ description: '每页数量' })
  pageSize: number;
}

import { ApiProperty } from '@nestjs/swagger';

/**
 * 权限规则DTO
 */
export class PermissionRuleDto {
  @ApiProperty({ description: '操作类型', example: 'read' })
  action: string;

  @ApiProperty({ description: '资源类型', example: 'Device' })
  subject: string;
}

/**
 * 权限响应DTO
 */
export class PermissionsResponseDto {
  @ApiProperty({
    description: '用户权限规则列表',
    type: [PermissionRuleDto],
  })
  permissions: PermissionRuleDto[];
}

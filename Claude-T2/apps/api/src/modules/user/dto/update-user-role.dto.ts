import { IsUUID, IsNotEmpty } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';

/**
 * 更新用户角色DTO
 */
export class UpdateUserRoleDto {
  @ApiProperty({
    description: '角色ID',
    example: '123e4567-e89b-12d3-a456-426614174000',
  })
  @IsUUID()
  @IsNotEmpty({ message: '角色ID不能为空' })
  roleId: string;
}

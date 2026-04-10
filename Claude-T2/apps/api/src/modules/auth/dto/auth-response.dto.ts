import { Exclude, Expose, Type } from 'class-transformer';
import { ApiProperty } from '@nestjs/swagger';
import { UserStatus } from '../../user/user.entity';
import { PermissionRuleDto } from './permissions.dto';

@Exclude()
export class UserInfoDto {
  @ApiProperty({ description: '用户ID' })
  @Expose()
  id: string;

  @ApiProperty({ description: '用户名' })
  @Expose()
  username: string;

  @ApiProperty({ description: '姓名' })
  @Expose()
  name: string;

  @ApiProperty({ description: '邮箱', required: false })
  @Expose()
  email: string;

  @ApiProperty({ description: '用户状态', enum: UserStatus })
  @Expose()
  status: UserStatus;

  @ApiProperty({ description: '角色ID', required: false })
  @Expose()
  roleId: string | null;

  @ApiProperty({
    description: '角色信息',
    required: false,
    type: () => RoleInfoDto,
  })
  @Expose()
  @Type(() => RoleInfoDto)
  role: RoleInfoDto | null;
}

@Exclude()
export class RoleInfoDto {
  @ApiProperty({ description: '角色ID' })
  @Expose()
  id: string;

  @ApiProperty({ description: '角色名称' })
  @Expose()
  name: string;

  @ApiProperty({ description: '角色代码' })
  @Expose()
  code: string;
}

@Exclude()
export class AuthResponseDto {
  @ApiProperty({ description: 'Access Token (有效期15分钟)' })
  @Expose()
  accessToken: string;

  @ApiProperty({ description: 'Refresh Token (有效期7天)' })
  @Expose()
  refreshToken: string;

  @ApiProperty({ description: '用户信息', type: UserInfoDto })
  @Expose()
  @Type(() => UserInfoDto)
  user: UserInfoDto;

  @ApiProperty({
    description: '用户权限规则列表',
    type: [PermissionRuleDto],
  })
  @Expose()
  permissions: PermissionRuleDto[];
}

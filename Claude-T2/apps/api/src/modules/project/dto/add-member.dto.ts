import { ApiProperty } from '@nestjs/swagger';
import { IsUUID, IsEnum, IsNotEmpty } from 'class-validator';
import { ProjectRole } from '../project-user.entity';

export class AddMemberDto {
  @ApiProperty({
    description: '用户ID',
    example: 'uuid',
  })
  @IsUUID()
  @IsNotEmpty({ message: '用户ID不能为空' })
  userId: string;

  @ApiProperty({
    description: '项目角色',
    enum: ProjectRole,
    example: ProjectRole.MEMBER,
  })
  @IsEnum(ProjectRole, { message: '无效的项目角色' })
  @IsNotEmpty({ message: '角色不能为空' })
  role: ProjectRole;
}

export class UpdateMemberRoleDto {
  @ApiProperty({
    description: '项目角色',
    enum: ProjectRole,
    example: ProjectRole.MEMBER,
  })
  @IsEnum(ProjectRole, { message: '无效的项目角色' })
  @IsNotEmpty({ message: '角色不能为空' })
  role: ProjectRole;
}

import { Exclude, Expose, Type } from 'class-transformer';
import { UserStatus } from '../user.entity';

class RoleDto {
  id: string;
  name: string;
  code: string;
}

@Exclude()
export class UserResponseDto {
  @Expose()
  id: string;

  @Expose()
  username: string;

  @Expose()
  name: string;

  @Expose()
  email: string;

  @Expose()
  phone: string;

  @Expose()
  status: UserStatus;

  @Expose({ name: 'roleId' })
  roleId: string;

  @Expose()
  @Type(() => RoleDto)
  role: RoleDto;

  @Expose({ name: 'createdAt' })
  createdAt: Date;

  @Expose({ name: 'updatedAt' })
  updatedAt: Date;
}

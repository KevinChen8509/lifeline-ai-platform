import { PartialType, OmitType } from '@nestjs/mapped-types';
import { CreateRoleDto } from './create-role.dto';

export class UpdateRoleDto extends PartialType(
  OmitType(CreateRoleDto, ['code'] as const),
) {}

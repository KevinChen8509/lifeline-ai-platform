import { SetMetadata } from '@nestjs/common';
import { Actions, Subjects } from '../../modules/auth/ability/ability.types';

export const PERMISSIONS_KEY = 'permissions';

/**
 * 权限装饰器
 * 用于标记API端点所需的权限
 *
 * @example
 * @RequirePermissions('read', 'Device')
 * @Get('devices')
 * getDevices() {}
 *
 * @example
 * @RequirePermissions('manage', 'User')
 * @Post('users')
 * createUser() {}
 */
export const RequirePermissions = (action: Actions, subject: Subjects) =>
  SetMetadata(PERMISSIONS_KEY, { action, subject });

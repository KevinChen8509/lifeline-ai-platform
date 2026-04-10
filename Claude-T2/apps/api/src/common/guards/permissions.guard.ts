import {
  Injectable,
  CanActivate,
  ExecutionContext,
  ForbiddenException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { AbilityFactory } from '../../modules/auth/ability/ability.factory';
import { PERMISSIONS_KEY } from '../decorators/permissions.decorator';
import { User } from '../../modules/user/user.entity';

/**
 * 权限守卫
 * 检查用户是否有执行当前操作的权限
 */
@Injectable()
export class PermissionsGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly abilityFactory: AbilityFactory,
  ) {}

  canActivate(context: ExecutionContext): boolean {
    // 获取路由所需的权限
    const requiredPermission = this.reflector.getAllAndOverride<{
      action: string;
      subject: string;
    }>(PERMISSIONS_KEY, [context.getHandler(), context.getClass()]);

    // 如果没有设置权限要求，则允许访问
    if (!requiredPermission) {
      return true;
    }

    // 获取当前用户
    const request = context.switchToHttp().getRequest();
    const user = request.user as User;

    if (!user) {
      throw new ForbiddenException('用户未登录');
    }

    // 创建用户的 Ability 实例
    const ability = this.abilityFactory.createForUser(user);

    // 检查权限
    const { action, subject } = requiredPermission;
    const hasPermission = ability.can(action as any, subject as any);

    if (!hasPermission) {
      throw new ForbiddenException(`没有权限执行此操作: ${action} ${subject}`);
    }

    return true;
  }
}

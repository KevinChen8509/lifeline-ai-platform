import {
  Injectable,
  CanActivate,
  ExecutionContext,
  ForbiddenException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { ProjectService } from '../../modules/project/project.service';
import { AbilityFactory } from '../../modules/auth/ability/ability.factory';
import { User } from '../../modules/user/user.entity';

export const PROJECT_ACCESS_KEY = 'projectAccess';

/**
 * 项目访问守卫
 * 验证用户是否有权访问特定项目
 *
 * 使用方式:
 * @UseGuards(ProjectAccessGuard)
 * @RequireProjectAccess('projectId')
 */
@Injectable()
export class ProjectAccessGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly projectService: ProjectService,
    private readonly abilityFactory: AbilityFactory,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    // 获取配置的项目ID参数名
    const config = this.reflector.getAllAndOverride<{ paramName: string }>(
      PROJECT_ACCESS_KEY,
      [context.getHandler(), context.getClass()],
    );

    // 如果没有配置，允许访问（由其他守卫处理）
    if (!config) {
      return true;
    }

    const request = context.switchToHttp().getRequest();
    const user = request.user as User;

    if (!user) {
      throw new ForbiddenException('用户未登录');
    }

    // 管理员可以访问所有项目
    const ability = this.abilityFactory.createForUser(user);
    if (ability.can('manage', 'all')) {
      return true;
    }

    // 获取项目ID
    const projectId = request.params[config.paramName] || request.query[config.paramName];

    if (!projectId) {
      // 如果没有提供项目ID，允许访问（可能是列表查询，由服务层过滤）
      return true;
    }

    // 检查用户是否是项目成员
    const isMember = await this.projectService.isProjectMember(projectId, user.id);

    if (!isMember) {
      throw new ForbiddenException('您没有权限访问此项目');
    }

    return true;
  }
}

/**
 * 装饰器：标记需要项目访问检查
 * @param paramName 项目ID的参数名（默认为 'projectId'）
 */
import { SetMetadata } from '@nestjs/common';

export const RequireProjectAccess = (paramName: string = 'projectId') =>
  SetMetadata(PROJECT_ACCESS_KEY, { paramName });

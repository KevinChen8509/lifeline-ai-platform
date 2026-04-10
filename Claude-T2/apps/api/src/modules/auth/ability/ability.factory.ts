import { Injectable } from '@nestjs/common';
import {
  Ability,
  AbilityBuilder,
  AbilityClass,
  ExtractSubjectType,
  InferSubjects,
} from '@casl/ability';
import { User } from '../../user/user.entity';
import { RoleCode } from '../../role/role.entity';

/**
 * 可执行的操作
 */
type Actions = 'create' | 'read' | 'update' | 'delete' | 'manage';

/**
 * 可操作的资源
 */
type Subjects = InferSubjects<
  | 'User'
  | 'Role'
  | 'Project'
  | 'Device'
  | 'Model'
  | 'Alert'
  | 'ApiKey'
  | 'Telemetry'
  | 'Webhook'
  | 'AuditLog'
  | 'all'
>;

/**
 * 应用 Ability 类型
 */
export type AppAbility = Ability<[Actions, Subjects]>;
export const AppAbility = Ability as AbilityClass<AppAbility>;

/**
 * CASL Ability 工厂
 * 根据用户角色定义权限规则
 */
@Injectable()
export class AbilityFactory {
  /**
   * 为用户创建 Ability 实例
   */
  createForUser(user: User): AppAbility {
    const { can, cannot, build } = new AbilityBuilder<AppAbility>(AppAbility);

    const roleCode = user.role?.code as RoleCode | undefined;

    switch (roleCode) {
      case RoleCode.ADMIN:
        this.defineAdminAbilities(can);
        break;
      case RoleCode.OPERATOR:
        this.defineOperatorAbilities(can);
        break;
      case RoleCode.OBSERVER:
        this.defineObserverAbilities(can);
        break;
      default:
        // 无角色用户没有任何权限
        break;
    }

    return build({
      detectSubjectType: (item: any) =>
        item.constructor as ExtractSubjectType<Subjects>,
    });
  }

  /**
   * 管理员权限：拥有所有权限
   */
  private defineAdminAbilities(
    can: AbilityBuilder<AppAbility>['can'],
  ): void {
    can('manage', 'all');
  }

  /**
   * 运维员权限：
   * - 项目：查看
   * - 设备：全部操作
   * - 模型：全部操作（可绑定模型到设备）
   * - 预警：全部操作（可处置预警）
   * - 遥测数据：查看
   */
  private defineOperatorAbilities(
    can: AbilityBuilder<AppAbility>['can'],
  ): void {
    // 项目管理
    can('read', 'Project');

    // 设备管理 - 全部权限
    can('create', 'Device');
    can('read', 'Device');
    can('update', 'Device');
    can('delete', 'Device');

    // 模型管理 - 全部权限（可绑定模型）
    can('create', 'Model');
    can('read', 'Model');
    can('update', 'Model');
    can('delete', 'Model');

    // 预警管理 - 全部权限（可处置预警）
    can('create', 'Alert');
    can('read', 'Alert');
    can('update', 'Alert');
    can('delete', 'Alert');

    // 遥测数据 - 只读
    can('read', 'Telemetry');

    // API Key - 只读
    can('read', 'ApiKey');
  }

  /**
   * 观察员权限：全部只读
   */
  private defineObserverAbilities(
    can: AbilityBuilder<AppAbility>['can'],
  ): void {
    can('read', 'Project');
    can('read', 'Device');
    can('read', 'Model');
    can('read', 'Alert');
    can('read', 'ApiKey');
    can('read', 'Telemetry');
    can('read', 'Webhook');
    can('read', 'AuditLog');
  }

  /**
   * 将 Ability 规则转换为简单的权限数组
   * 用于API返回给前端
   */
  getPermissionsForRole(roleCode: RoleCode): Array<{ action: string; subject: string }> {
    const permissions: Array<{ action: string; subject: string }> = [];

    switch (roleCode) {
      case RoleCode.ADMIN:
        permissions.push({ action: 'manage', subject: 'all' });
        break;
      case RoleCode.OPERATOR:
        permissions.push(
          { action: 'read', subject: 'Project' },
          { action: 'manage', subject: 'Device' },
          { action: 'manage', subject: 'Model' },
          { action: 'manage', subject: 'Alert' },
          { action: 'read', subject: 'Telemetry' },
          { action: 'read', subject: 'ApiKey' },
        );
        break;
      case RoleCode.OBSERVER:
        permissions.push(
          { action: 'read', subject: 'Project' },
          { action: 'read', subject: 'Device' },
          { action: 'read', subject: 'Model' },
          { action: 'read', subject: 'Alert' },
          { action: 'read', subject: 'ApiKey' },
          { action: 'read', subject: 'Telemetry' },
          { action: 'read', subject: 'Webhook' },
          { action: 'read', subject: 'AuditLog' },
        );
        break;
    }

    return permissions;
  }
}

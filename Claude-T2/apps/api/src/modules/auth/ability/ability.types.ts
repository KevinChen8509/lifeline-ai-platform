import { Ability, AbilityClass } from '@casl/ability';

// Re-export RoleCode from role entity for convenience
export { RoleCode } from '../../role/role.entity';

/**
 * 可执行的操作类型
 */
export type Actions = 'create' | 'read' | 'update' | 'delete' | 'manage';

/**
 * 可操作的资源类型
 */
export type Subjects =
  | 'User'
  | 'Role'
  | 'Project'
  | 'Device'
  | 'AiModel'
  | 'Model'
  | 'Alert'
  | 'ApiKey'
  | 'Telemetry'
  | 'Webhook'
  | 'AuditLog'
  | 'Report'
  | 'System'
  | 'all';

/**
 * CASL Ability 类型定义
 */
export type AppAbility = Ability<[Actions, Subjects]>;
export const AppAbility = Ability as AbilityClass<AppAbility>;

/**
 * 权限规则接口
 */
export interface PermissionRule {
  action: Actions;
  subject: Subjects;
}

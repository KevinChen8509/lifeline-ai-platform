import { Ability, AbilityClass } from '@casl/ability';

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
  | 'Model'
  | 'Alert'
  | 'ApiKey'
  | 'Telemetry'
  | 'Webhook'
  | 'AuditLog'
  | 'all';

/**
 * 应用 Ability 类型
 */
export type AppAbility = Ability<[Actions, Subjects]>;
export const AppAbility = Ability as AbilityClass<AppAbility>;

/**
 * 权限规则接口
 */
export interface PermissionRule {
  action: string;
  subject: string;
}

/**
 * 创建默认的空权限 Ability
 */
export function createEmptyAbility(): AppAbility {
  return new AppAbility([]);
}

/**
 * 根据 API 返回的权限规则创建 Ability
 */
export function createAbilityFromRules(rules: PermissionRule[]): AppAbility {
  // 将简单的权限规则转换为 CASL 格式
  const caslRules = rules.map((rule) => ({
    action: rule.action as Actions,
    subject: rule.subject as Subjects,
  }));

  return new AppAbility(caslRules);
}

// 全局 Ability 实例
let abilityInstance: AppAbility | null = null;

/**
 * 获取全局 Ability 实例
 */
export function getAbility(): AppAbility {
  if (!abilityInstance) {
    abilityInstance = createEmptyAbility();
  }
  return abilityInstance;
}

/**
 * 更新全局 Ability 实例的权限规则
 */
export function updateAbility(rules: PermissionRule[]): void {
  const ability = getAbility();
  ability.update(
    rules.map((rule) => ({
      action: rule.action as Actions,
      subject: rule.subject as Subjects,
    })),
  );
}

/**
 * 清除全局 Ability 实例的权限规则
 */
export function clearAbility(): void {
  const ability = getAbility();
  ability.update([]);
}

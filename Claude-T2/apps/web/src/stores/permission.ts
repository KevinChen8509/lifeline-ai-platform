import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { getAbility, updateAbility, clearAbility, PermissionRule } from '@/utils/ability';

export const usePermissionStore = defineStore('permission', () => {
  // 权限规则列表
  const permissions = ref<PermissionRule[]>([]);

  // 是否已加载权限
  const isLoaded = ref(false);

  // CASL Ability 实例
  const ability = computed(() => getAbility());

  /**
   * 设置权限规则
   */
  function setPermissions(perms: PermissionRule[]): void {
    permissions.value = perms;
    updateAbility(perms);
    isLoaded.value = true;
  }

  /**
   * 检查是否有权限
   * @param action 操作类型
   * @param subject 资源类型
   */
  function can(action: string, subject: string): boolean {
    return ability.value.can(action as any, subject as any);
  }

  /**
   * 检查是否没有权限
   * @param action 操作类型
   * @param subject 资源类型
   */
  function cannot(action: string, subject: string): boolean {
    return ability.value.cannot(action as any, subject as any);
  }

  /**
   * 检查是否有任意权限（管理员）
   */
  function hasFullAccess(): boolean {
    return can('manage', 'all');
  }

  /**
   * 检查是否有特定资源的读取权限
   */
  function canRead(subject: string): boolean {
    return can('read', subject) || can('manage', subject) || hasFullAccess();
  }

  /**
   * 检查是否有特定资源的写入权限
   */
  function canWrite(subject: string): boolean {
    return can('create', subject) || can('update', subject) || can('manage', subject) || hasFullAccess();
  }

  /**
   * 检查是否有特定资源的删除权限
   */
  function canDelete(subject: string): boolean {
    return can('delete', subject) || can('manage', subject) || hasFullAccess();
  }

  /**
   * 清除权限
   */
  function clear(): void {
    permissions.value = [];
    clearAbility();
    isLoaded.value = false;
  }

  return {
    permissions,
    isLoaded,
    ability,
    setPermissions,
    can,
    cannot,
    hasFullAccess,
    canRead,
    canWrite,
    canDelete,
    clear,
  };
});

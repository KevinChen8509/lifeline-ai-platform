import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { logout as logoutApi, getPermissions } from '@/api/auth';
import { usePermissionStore } from './permission';

interface User {
  id: string;
  username: string;
  name: string;
  email?: string;
  phone?: string;
  status: string;
  roleId?: string | null;
  role?: {
    id: string;
    name: string;
    code: string;
  } | null;
}

export const useUserStore = defineStore('user', () => {
  const user = ref<User | null>(null);
  const token = ref<string | null>(localStorage.getItem('access_token'));
  const refreshToken = ref<string | null>(localStorage.getItem('refresh_token'));
  const permissions = ref<string[]>([]);

  // 计算属性：是否已登录
  const isAuthenticated = computed(() => !!token.value);

  // 设置用户信息
  function setUser(userData: User) {
    user.value = userData;
  }

  // 设置 Tokens
  function setTokens(accessToken: string, refresh: string) {
    token.value = accessToken;
    refreshToken.value = refresh;
    localStorage.setItem('access_token', accessToken);
    localStorage.setItem('refresh_token', refresh);
  }

  // 设置 Token (向后兼容)
  function setToken(newToken: string) {
    token.value = newToken;
    localStorage.setItem('access_token', newToken);
  }

  // 设置权限
  function setPermissions(perms: string[]) {
    permissions.value = perms;
  }

  // 检查是否有权限
  function hasPermission(permission: string): boolean {
    if (permissions.value.includes('*')) return true;
    return permissions.value.includes(permission);
  }

  // 清除本地状态
  function clearState() {
    user.value = null;
    token.value = null;
    refreshToken.value = null;
    permissions.value = [];
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');

    // 同时清除权限store
    const permissionStore = usePermissionStore();
    permissionStore.clear();
  }

  // 登出（仅清除本地状态）
  function logout() {
    clearState();
  }

  // 登出（调用API并清除本地状态）
  async function logoutUser() {
    try {
      await logoutApi();
    } catch (error) {
      // 忽略登出API错误，仍然清除本地状态
      console.warn('Logout API call failed:', error);
    } finally {
      clearState();
    }
  }

  // 加载用户权限
  async function loadPermissions(): Promise<void> {
    try {
      const response = await getPermissions();
      const permissionStore = usePermissionStore();
      permissionStore.setPermissions(response.permissions);
    } catch (error) {
      console.error('Failed to load permissions:', error);
    }
  }

  return {
    user,
    token,
    refreshToken,
    permissions,
    isAuthenticated,
    setUser,
    setTokens,
    setToken,
    setPermissions,
    hasPermission,
    logout,
    logoutUser,
    clearState,
    loadPermissions,
  };
});

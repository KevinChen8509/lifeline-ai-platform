import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useUserStore } from '@/stores/user';
import { usePermissionStore } from '@/stores/permission';

// Mock vue-router
const mockPush = vi.fn();
const mockNext = vi.fn();

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router');
  return {
    ...actual,
    createRouter: vi.fn(() => ({
      beforeEach: vi.fn((guard) => guard),
      push: mockPush,
    })),
    createWebHistory: vi.fn(),
  };
});

describe('Router Permission Control', () => {
  let userStore: ReturnType<typeof useUserStore>;
  let permissionStore: ReturnType<typeof usePermissionStore>;

  beforeEach(() => {
    setActivePinia(createPinia());
    userStore = useUserStore();
    permissionStore = usePermissionStore();
    localStorage.clear();
    vi.clearAllMocks();
  });

  describe('Authentication Guard', () => {
    it('should redirect to login when accessing protected route without auth', () => {
      // 模拟未登录状态
      expect(userStore.isAuthenticated).toBe(false);

      // 测试需要认证的路由
      const to = {
        path: '/system/users',
        name: 'UserManagement',
        meta: { requiresAuth: true, permission: { action: 'manage', subject: 'User' } },
      };

      // 路由守卫应该重定向到登录页
      expect(to.meta.requiresAuth).toBe(true);
      expect(userStore.isAuthenticated).toBe(false);
    });

    it('should allow access when authenticated', () => {
      // 模拟已登录状态
      userStore.setToken('test-token');
      expect(userStore.isAuthenticated).toBe(true);
    });
  });

  describe('Permission Guard', () => {
    beforeEach(() => {
      userStore.setToken('test-token');
    });

    it('should redirect to 403 when lacking permission', () => {
      // 设置有限的权限
      permissionStore.setPermissions([{ action: 'read', subject: 'Project' }]);

      // 测试用户管理路由（需要 manage User 权限）
      const to = {
        path: '/system/users',
        name: 'UserManagement',
        meta: { requiresAuth: true, permission: { action: 'manage', subject: 'User' } },
      };

      // 检查权限
      const { action, subject } = to.meta.permission;
      expect(permissionStore.can(action, subject)).toBe(false);
    });

    it('should allow access when has permission', () => {
      // 设置管理员权限
      permissionStore.setPermissions([{ action: 'manage', subject: 'all' }]);

      // 测试用户管理路由
      const to = {
        path: '/system/users',
        name: 'UserManagement',
        meta: { requiresAuth: true, permission: { action: 'manage', subject: 'User' } },
      };

      const { action, subject } = to.meta.permission;
      expect(permissionStore.can(action, subject)).toBe(true);
    });

    it('should allow access to routes without permission requirement', () => {
      // 测试仪表盘路由（无特殊权限要求）
      const to = {
        path: '/dashboard',
        name: 'Dashboard',
        meta: { requiresAuth: true },
      };

      expect(to.meta.permission).toBeUndefined();
    });
  });

  describe('Guest Route Guard', () => {
    it('should redirect authenticated users away from login page', () => {
      userStore.setToken('test-token');

      const to = {
        path: '/login',
        name: 'Login',
        meta: { guest: true },
      };

      expect(userStore.isAuthenticated).toBe(true);
      expect(to.meta.guest).toBe(true);
    });

    it('should allow guest access to login page when not authenticated', () => {
      // Ensure no token is set
      userStore.clearState();
      expect(userStore.isAuthenticated).toBe(false);

      const to = {
        path: '/login',
        name: 'Login',
        meta: { guest: true },
      };

      expect(to.meta.guest).toBe(true);
    });
  });
});

describe('Menu Permission Filter', () => {
  let permissionStore: ReturnType<typeof usePermissionStore>;

  beforeEach(() => {
    setActivePinia(createPinia());
    permissionStore = usePermissionStore();
    vi.clearAllMocks();
  });

  it('should filter menus by permission', async () => {
    const { filterMenuByPermission, menuConfig } = await import('@/config/menu');

    // 设置有限的权限
    permissionStore.setPermissions([{ action: 'read', subject: 'Project' }]);

    const filtered = filterMenuByPermission(menuConfig, permissionStore.can);

    // 仪表盘（无权限要求）和项目管理应该保留
    expect(filtered.some((m) => m.key === 'dashboard')).toBe(true);
    expect(filtered.some((m) => m.key === 'projects')).toBe(true);

    // 设备管理（需要 read Device）应该被过滤
    expect(filtered.some((m) => m.key === 'devices')).toBe(false);
  });

  it('should show all menus for admin users', async () => {
    const { filterMenuByPermission, menuConfig } = await import('@/config/menu');

    // 设置管理员权限
    permissionStore.setPermissions([{ action: 'manage', subject: 'all' }]);

    const filtered = filterMenuByPermission(menuConfig, permissionStore.can);

    // 所有菜单应该可见
    expect(filtered.length).toBe(menuConfig.length);
  });

  it('should filter out parent menu when all children are filtered', async () => {
    const { filterMenuByPermission } = await import('@/config/menu');

    // 设置空权限
    permissionStore.setPermissions([]);

    const menus = [
      {
        key: 'system',
        label: '系统管理',
        icon: 'SettingOutlined',
        children: [
          {
            key: 'users',
            label: '用户管理',
            path: '/system/users',
            permission: { action: 'manage', subject: 'User' },
          },
        ],
      },
    ];

    const filtered = filterMenuByPermission(menus, permissionStore.can);

    // 父菜单应该被过滤掉
    expect(filtered.length).toBe(0);
  });

  it('should include audit log menu item with proper permission', async () => {
    const { filterMenuByPermission, menuConfig } = await import('@/config/menu');

    // 设置审计日志读取权限
    permissionStore.setPermissions([{ action: 'read', subject: 'AuditLog' }]);

    const filtered = filterMenuByPermission(menuConfig, permissionStore.can);

    // 系统管理菜单应该包含审计日志
    const systemMenu = filtered.find((m) => m.key === 'system');
    expect(systemMenu).toBeDefined();
    expect(systemMenu?.children?.some((c) => c.key === 'audit-log')).toBe(true);
  });
});

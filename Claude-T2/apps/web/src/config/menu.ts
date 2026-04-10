import { RouteLocationNormalized } from 'vue-router';

export interface MenuItem {
  key: string;
  label: string;
  icon?: string;
  path?: string;
  permission?: { action: string; subject: string };
  children?: MenuItem[];
}

/**
 * 菜单配置
 * 每个菜单项可以配置 permission 来控制显示权限
 */
export const menuConfig: MenuItem[] = [
  {
    key: 'dashboard',
    label: '仪表盘',
    icon: 'DashboardOutlined',
    path: '/dashboard',
    // 仪表盘不需要特殊权限，只需登录即可
  },
  {
    key: 'projects',
    label: '项目管理',
    icon: 'FolderOutlined',
    path: '/projects',
    permission: { action: 'read', subject: 'Project' },
  },
  {
    key: 'devices',
    label: '设备管理',
    icon: 'ApiOutlined',
    path: '/devices',
    permission: { action: 'read', subject: 'Device' },
  },
  {
    key: 'models',
    label: '模型管理',
    icon: 'AppstoreOutlined',
    path: '/models',
    permission: { action: 'read', subject: 'Model' },
  },
  {
    key: 'alerts',
    label: '预警管理',
    icon: 'AlertOutlined',
    path: '/alerts',
    permission: { action: 'read', subject: 'Alert' },
  },
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
      {
        key: 'roles',
        label: '角色管理',
        path: '/system/roles',
        permission: { action: 'manage', subject: 'Role' },
      },
      {
        key: 'audit-log',
        label: '审计日志',
        path: '/system/audit-log',
        permission: { action: 'read', subject: 'AuditLog' },
      },
    ],
  },
];

/**
 * 根据权限过滤菜单
 */
export function filterMenuByPermission(
  menus: MenuItem[],
  can: (action: string, subject: string) => boolean
): MenuItem[] {
  return menus
    .filter((menu) => {
      // 如果没有权限要求，显示菜单
      if (!menu.permission) {
        return true;
      }
      // 检查权限
      return can(menu.permission.action, menu.permission.subject);
    })
    .map((menu) => {
      // 递归过滤子菜单
      if (menu.children) {
        const filteredChildren = filterMenuByPermission(menu.children, can);
        // 如果子菜单全部被过滤掉，则不显示父菜单
        if (filteredChildren.length === 0) {
          return null;
        }
        return { ...menu, children: filteredChildren };
      }
      return menu;
    })
    .filter((menu): menu is MenuItem => menu !== null);
}

/**
 * 获取菜单的图标组件名称
 */
export function getMenuIcon(iconName: string | undefined): string {
  return iconName || 'MenuOutlined';
}

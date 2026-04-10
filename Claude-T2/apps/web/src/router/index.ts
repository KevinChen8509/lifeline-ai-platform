import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { usePermissionStore } from '@/stores/permission';

declare module 'vue-router' {
  interface RouteMeta {
    title?: string;
    requiresAuth?: boolean;
    guest?: boolean;
    permission?: { action: string; subject: string };
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: {
      title: '登录',
      guest: true,
    },
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: {
          title: '仪表盘',
          requiresAuth: true,
        },
      },
      {
        path: 'projects',
        name: 'ProjectList',
        component: () => import('@/views/projects/index.vue'),
        meta: {
          title: '项目管理',
          requiresAuth: true,
          permission: { action: 'read', subject: 'Project' },
        },
      },
      {
        path: 'projects/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/projects/[id]/index.vue'),
        meta: {
          title: '项目详情',
          requiresAuth: true,
          permission: { action: 'read', subject: 'Project' },
        },
      },
      {
        path: 'devices',
        name: 'DeviceList',
        component: () => import('@/views/devices/index.vue'),
        meta: {
          title: '设备管理',
          requiresAuth: true,
          permission: { action: 'read', subject: 'Device' },
        },
      },
      {
        path: 'models',
        name: 'ModelList',
        component: () => import('@/views/models/index.vue'),
        meta: {
          title: '模型管理',
          requiresAuth: true,
          permission: { action: 'read', subject: 'Model' },
        },
      },
      {
        path: 'alerts',
        name: 'AlertList',
        component: () => import('@/views/alerts/index.vue'),
        meta: {
          title: '预警管理',
          requiresAuth: true,
          permission: { action: 'read', subject: 'Alert' },
        },
      },
      {
        path: 'system/users',
        name: 'UserManagement',
        component: () => import('@/views/system/users/index.vue'),
        meta: {
          title: '用户管理',
          requiresAuth: true,
          permission: { action: 'manage', subject: 'User' },
        },
      },
      {
        path: 'system/roles',
        name: 'RoleManagement',
        component: () => import('@/views/system/roles/index.vue'),
        meta: {
          title: '角色管理',
          requiresAuth: true,
          permission: { action: 'manage', subject: 'Role' },
        },
      },
      {
        path: 'system/audit-log',
        name: 'AuditLog',
        component: () => import('@/views/system/audit-log/index.vue'),
        meta: {
          title: '审计日志',
          requiresAuth: true,
          permission: { action: 'read', subject: 'AuditLog' },
        },
      },
    ],
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: {
      title: '无权限访问',
    },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: {
      title: '页面未找到',
    },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || '生命线AI感知云平台'} - 生命线AI`;

  const userStore = useUserStore();
  const permissionStore = usePermissionStore();

  if (to.meta.requiresAuth && !userStore.isAuthenticated) {
    next({
      name: 'Login',
      query: { redirect: to.fullPath },
    });
    return;
  }

  if (to.meta.guest && userStore.isAuthenticated) {
    next({ name: 'Dashboard' });
    return;
  }

  if (to.meta.permission) {
    const { action, subject } = to.meta.permission;
    if (!permissionStore.can(action, subject)) {
      next({ name: 'Forbidden' });
      return;
    }
  }

  next();
});

export default router;

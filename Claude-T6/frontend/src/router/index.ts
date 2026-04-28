import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '@/services/http'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/auth/LoginPage.vue'),
      meta: { title: '登录', public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      redirect: '/webhook/endpoints',
      children: [
        {
          path: 'webhook/endpoints',
          name: 'EndpointManage',
          component: () => import('@/views/webhook/EndpointManagePage.vue'),
          meta: { title: 'Webhook 端点管理' },
        },
        {
          path: 'webhook/logs',
          name: 'WebhookLog',
          component: () => import('@/views/webhook/WebhookLogPage.vue'),
          meta: { title: '推送日志' },
        },
        {
          path: 'devices',
          name: 'DeviceManage',
          component: () => import('@/views/device/DeviceManagePage.vue'),
          meta: { title: '设备管理' },
        },
        {
          path: 'devices/:id/datapoints',
          name: 'DataPointBrowse',
          component: () => import('@/views/device/DataPointBrowsePage.vue'),
          meta: { title: '数据点浏览' },
        },
        {
          path: 'subscriptions',
          name: 'SubscriptionList',
          component: () => import('@/views/subscription/SubscriptionListPage.vue'),
          meta: { title: '订阅管理' },
        },
        {
          path: 'subscriptions/create',
          name: 'SubscriptionCreate',
          component: () => import('@/views/subscription/SubscriptionCreatePage.vue'),
          meta: { title: '创建订阅' },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  document.title = `${to.meta.title || 'IoT'} - 数据订阅平台`

  if (!to.meta.public && !isAuthenticated()) {
    return { name: 'Login' }
  }
})

export default router

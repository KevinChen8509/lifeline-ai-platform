<template>
  <a-layout class="layout">
    <a-layout-header class="header">
      <div class="logo">
        <img src="/favicon.svg" alt="Logo" />
        <span>生命线AI感知云平台</span>
      </div>
      <div class="header-right">
        <a-dropdown>
          <a-button type="text">
            <UserOutlined />
            <span>{{ userStore.user?.name || '用户' }}</span>
          </a-button>
          <template #overlay>
            <a-menu>
              <a-menu-item key="profile">个人设置</a-menu-item>
              <a-menu-divider />
              <a-menu-item key="logout" @click="handleLogout">退出登录</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </a-layout-header>
    <a-layout>
      <a-layout-sider v-model:collapsed="collapsed" collapsible :width="220">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="inline"
          theme="dark"
        >
          <template v-for="menu in filteredMenus" :key="menu.key">
            <a-sub-menu v-if="menu.children && menu.children.length > 0" :key="menu.key">
              <template #icon>
                <component :is="getIconComponent(menu.icon)" />
              </template>
              <template #title>{{ menu.label }}</template>
              <a-menu-item
                v-for="child in menu.children"
                :key="child.key"
                @click="handleMenuClick(child)"
              >
                {{ child.label }}
              </a-menu-item>
            </a-sub-menu>
            <a-menu-item v-else :key="menu.key" @click="handleMenuClick(menu)">
              <component :is="getIconComponent(menu.icon)" />
              <span>{{ menu.label }}</span>
            </a-menu-item>
          </template>
        </a-menu>
      </a-layout-sider>
      <a-layout-content class="content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { usePermissionStore } from '@/stores/permission';
import { menuConfig, filterMenuByPermission, MenuItem } from '@/config/menu';
import {
  UserOutlined,
  DashboardOutlined,
  ApiOutlined,
  AlertOutlined,
  FolderOutlined,
  FileTextOutlined,
  SettingOutlined,
  AppstoreOutlined,
  MenuOutlined,
  BarChartOutlined,
  DesktopOutlined,
} from '@ant-design/icons-vue';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const permissionStore = usePermissionStore();

const collapsed = ref(false);
const selectedKeys = ref<string[]>(['dashboard']);

const filteredMenus = computed(() => {
  return filterMenuByPermission(menuConfig, permissionStore.can);
});

const iconMap: Record<string, any> = {
  DashboardOutlined,
  ApiOutlined,
  AlertOutlined,
  FolderOutlined,
  FileTextOutlined,
  SettingOutlined,
  AppstoreOutlined,
  MenuOutlined,
  BarChartOutlined,
  DesktopOutlined,
};

function getIconComponent(iconName?: string) {
  if (!iconName) return MenuOutlined;
  return iconMap[iconName] || MenuOutlined;
}

function handleMenuClick(menu: MenuItem) {
  if (menu.path) {
    router.push(menu.path);
  }
}

function handleLogout() {
  userStore.logoutUser();
  router.push('/login');
}

// Sync selected menu key with current route
watch(
  () => route.path,
  (path) => {
    // Find matching menu key from menuConfig
    for (const menu of menuConfig) {
      if (menu.children) {
        for (const child of menu.children) {
          if (child.path && path.startsWith(child.path)) {
            selectedKeys.value = [child.key];
            return;
          }
        }
      }
      if (menu.path && path.startsWith(menu.path)) {
        selectedKeys.value = [menu.key];
        return;
      }
    }
  },
  { immediate: true },
);
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #001529;
  padding: 0 24px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
}

.logo img {
  width: 32px;
  height: 32px;
}

.header-right {
  color: #fff;
}

.content {
  margin: 16px;
  background: #f0f2f5;
  border-radius: 8px;
  overflow: auto;
}
</style>

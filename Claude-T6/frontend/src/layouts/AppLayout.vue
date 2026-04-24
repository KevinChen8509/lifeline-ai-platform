<template>
  <el-container class="app-layout">
    <el-aside width="220px" class="app-sidebar">
      <div class="logo">
        <h3>IoT 数据订阅</h3>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/webhook/endpoints">
          <el-icon><Connection /></el-icon>
          <span>端点管理</span>
        </el-menu-item>
        <el-menu-item index="/subscriptions">
          <el-icon><Bell /></el-icon>
          <span>订阅管理</span>
        </el-menu-item>
        <el-menu-item index="/devices">
          <el-icon><Monitor /></el-icon>
          <span>设备管理</span>
        </el-menu-item>
        <el-menu-item index="/webhook/logs">
          <el-icon><Document /></el-icon>
          <span>推送日志</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <span class="page-title">{{ currentTitle }}</span>
        <div class="header-right">
          <NotificationPanel />
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Connection, Bell, Monitor, Document } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/stores/notification'
import NotificationPanel from '@/components/NotificationPanel.vue'

const route = useRoute()
const notificationStore = useNotificationStore()

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => (route.meta.title as string) || 'IoT 数据订阅')

onMounted(() => {
  notificationStore.fetchUnreadCount()
})
</script>

<style scoped>
.app-layout {
  height: 100vh;
}

.app-sidebar {
  background-color: #001529;
  overflow-y: auto;
}

.logo {
  padding: 16px;
  text-align: center;
}

.logo h3 {
  color: #fff;
  margin: 0;
  font-size: 16px;
}

.sidebar-menu {
  border-right: none;
  background-color: #001529;
}

.sidebar-menu .el-menu-item {
  color: rgba(255, 255, 255, 0.65);
}

.sidebar-menu .el-menu-item:hover,
.sidebar-menu .el-menu-item.is-active {
  background-color: #1890ff;
  color: #fff;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
}

.page-title {
  font-size: 18px;
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.notification-icon {
  cursor: pointer;
}

.app-main {
  background-color: #f5f5f5;
  min-height: 0;
}
</style>

<template>
  <el-popover placement="bottom-end" :width="380" trigger="click" @show="onShow">
    <template #reference>
      <el-badge :value="notificationStore.unreadCount" :hidden="notificationStore.unreadCount === 0">
        <el-icon :size="20" class="notification-icon"><Bell /></el-icon>
      </el-badge>
    </template>

    <div class="notification-panel">
      <div class="panel-header">
        <span>通知</span>
        <el-button v-if="notificationStore.unreadCount > 0" link type="primary" size="small" @click="markAllRead">
          全部已读
        </el-button>
      </div>

      <div class="panel-body" v-loading="loading">
        <div v-if="notifications.length === 0" class="no-notifications">暂无通知</div>
        <div
          v-for="n in notifications"
          :key="n.id"
          class="notification-item"
          :class="{ unread: !n.isRead }"
          @click="markRead(n)"
        >
          <div class="notification-title">{{ n.title }}</div>
          <div v-if="n.content" class="notification-content">{{ n.content }}</div>
          <div class="notification-time">{{ formatTime(n.createdAt) }}</div>
        </div>
      </div>

      <div class="panel-footer">
        <el-button link size="small">查看全部</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/stores/notification'
import type { Notification } from '@/types/notification'

const notificationStore = useNotificationStore()
const notifications = ref<Notification[]>([])
const loading = ref(false)

async function onShow() {
  loading.value = true
  try {
    await notificationStore.fetchNotifications({ page: 0, size: 10 })
    notifications.value = notificationStore.notifications
  } finally {
    loading.value = false
  }
}

async function markRead(n: Notification) {
  if (n.isRead) return
  await notificationStore.markAsRead(n.id)
  n.isRead = true
}

async function markAllRead() {
  await notificationStore.markAllAsRead()
  notifications.value.forEach(n => { n.isRead = true })
}

function formatTime(t: string) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  if (diffMs < 60000) return '刚刚'
  if (diffMs < 3600000) return Math.floor(diffMs / 60000) + ' 分钟前'
  if (diffMs < 86400000) return Math.floor(diffMs / 3600000) + ' 小时前'
  return d.toLocaleDateString()
}
</script>

<style scoped>
.notification-panel {
  max-height: 400px;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
  font-weight: 500;
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  max-height: 320px;
}

.no-notifications {
  text-align: center;
  padding: 30px 0;
  color: #c0c4cc;
}

.notification-item {
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}

.notification-item:hover { background: #f5f7fa; margin: 0 -12px; padding: 10px 12px; }

.notification-item.unread .notification-title { font-weight: 600; }

.notification-title { font-size: 14px; color: #303133; }

.notification-content { font-size: 12px; color: #909399; margin-top: 4px; }

.notification-time { font-size: 11px; color: #c0c4cc; margin-top: 4px; }

.panel-footer {
  text-align: center;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
}

.notification-icon { cursor: pointer; }
</style>

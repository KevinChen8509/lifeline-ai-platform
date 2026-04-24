import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Notification, NotificationPreference } from '@/types/notification'
import notificationService from '@/services/notificationService'

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref<Notification[]>([])
  const unreadCount = ref(0)
  const preference = ref<NotificationPreference | null>(null)
  const loading = ref(false)

  async function fetchUnreadCount() {
    const res = await notificationService.unreadCount()
    unreadCount.value = res.data || 0
  }

  async function fetchNotifications(params?: { page?: number; size?: number }) {
    loading.value = true
    try {
      const res = await notificationService.list(params)
      notifications.value = res.data?.content || []
    } finally { loading.value = false }
  }

  async function markAsRead(id: number) {
    await notificationService.markAsRead(id)
    await fetchUnreadCount()
  }

  async function markAllAsRead() {
    await notificationService.markAllAsRead()
    unreadCount.value = 0
  }

  async function fetchPreference() {
    const res = await notificationService.getPreference()
    preference.value = res.data
  }

  async function updatePreference(data: Partial<NotificationPreference>) {
    const res = await notificationService.updatePreference(data)
    preference.value = res.data
  }

  return { notifications, unreadCount, preference, loading, fetchUnreadCount, fetchNotifications, markAsRead, markAllAsRead, fetchPreference, updatePreference }
})

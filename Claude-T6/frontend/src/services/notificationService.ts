import http from './http'
import type { ApiResponse, PageResponse } from '@/types'
import type { Notification, NotificationPreference } from '@/types/notification'

export default {
  list(params?: { page?: number; size?: number }): Promise<ApiResponse<PageResponse<Notification>>> {
    return http.get('/notifications', { params }).then(r => r.data)
  },
  unreadCount(): Promise<ApiResponse<number>> {
    return http.get('/notifications/unread-count').then(r => r.data)
  },
  markAsRead(id: number): Promise<ApiResponse<void>> {
    return http.post(`/notifications/${id}/read`).then(r => r.data)
  },
  markAllAsRead(): Promise<ApiResponse<void>> {
    return http.post('/notifications/read-all').then(r => r.data)
  },
  getPreference(): Promise<ApiResponse<NotificationPreference>> {
    return http.get('/notifications/preferences').then(r => r.data)
  },
  updatePreference(data: Partial<NotificationPreference>): Promise<ApiResponse<NotificationPreference>> {
    return http.put('/notifications/preferences', data).then(r => r.data)
  },
}

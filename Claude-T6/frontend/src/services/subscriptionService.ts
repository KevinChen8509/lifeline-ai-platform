import http from './http'
import type { ApiResponse, PageResponse } from '@/types'
import type { Subscription, SubscriptionCreateRequest, Rule } from '@/types/subscription'

export default {
  list(params?: { page?: number; size?: number }): Promise<ApiResponse<PageResponse<Subscription>>> {
    return http.get('/subscriptions', { params }).then(r => r.data)
  },
  getDetail(id: number): Promise<ApiResponse<Subscription>> {
    return http.get(`/subscriptions/${id}`).then(r => r.data)
  },
  create(data: SubscriptionCreateRequest): Promise<ApiResponse<Subscription>> {
    return http.post('/subscriptions', data).then(r => r.data)
  },
  update(id: number, data: Partial<Subscription>): Promise<ApiResponse<Subscription>> {
    return http.put(`/subscriptions/${id}`, data).then(r => r.data)
  },
  toggleStatus(id: number, status: number): Promise<ApiResponse<void>> {
    return http.patch(`/subscriptions/${id}/status`, null, { params: { status } }).then(r => r.data)
  },
  delete(id: number): Promise<ApiResponse<void>> {
    return http.delete(`/subscriptions/${id}`).then(r => r.data)
  },
  batchToggleStatus(ids: number[], status: number): Promise<ApiResponse<void>> {
    return http.post('/subscriptions/batch-status', { ids, status }).then(r => r.data)
  },
  checkConflicts(deviceId: number, dataPointIds?: number[], excludeId?: number): Promise<ApiResponse<Rule[]>> {
    return http.get('/subscriptions/conflicts', { params: { deviceId, dataPointIds, excludeSubscriptionId: excludeId } }).then(r => r.data)
  },
}

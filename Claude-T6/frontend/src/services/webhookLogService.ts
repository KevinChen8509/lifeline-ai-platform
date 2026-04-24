import http from './http'
import type { ApiResponse, PageResponse } from '@/types'
import type { DeliveryLog, PushStats, LogQueryParams } from '@/types/webhook-log'

export default {
  list(params?: LogQueryParams): Promise<ApiResponse<PageResponse<DeliveryLog>>> {
    return http.get('/webhook-logs', { params }).then(r => r.data)
  },
  getDetail(id: number): Promise<ApiResponse<DeliveryLog>> {
    return http.get(`/webhook-logs/${id}`).then(r => r.data)
  },
  retry(id: number): Promise<ApiResponse<void>> {
    return http.post(`/webhook-logs/${id}/retry`).then(r => r.data)
  },
  getStats(endpointId: number): Promise<ApiResponse<PushStats>> {
    return http.get('/webhook-logs/stats', { params: { endpointId } }).then(r => r.data)
  },
}

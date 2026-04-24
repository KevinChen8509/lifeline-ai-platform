import http from './http'
import type { ApiResponse, WebhookEndpoint, EndpointCreateRequest, EndpointUpdateRequest, SecretResponse, QuotaInfo } from '@/types'

export default {
  list(): Promise<ApiResponse<WebhookEndpoint[]>> {
    return http.get('/webhook-endpoints').then(r => r.data)
  },
  getDetail(id: number): Promise<ApiResponse<WebhookEndpoint>> {
    return http.get(`/webhook-endpoints/${id}`).then(r => r.data)
  },
  create(data: EndpointCreateRequest): Promise<ApiResponse<SecretResponse>> {
    return http.post('/webhook-endpoints', data).then(r => r.data)
  },
  update(id: number, data: EndpointUpdateRequest): Promise<ApiResponse<WebhookEndpoint>> {
    return http.put(`/webhook-endpoints/${id}`, data).then(r => r.data)
  },
  delete(id: number): Promise<ApiResponse<void>> {
    return http.delete(`/webhook-endpoints/${id}`).then(r => r.data)
  },
  getQuota(): Promise<ApiResponse<QuotaInfo>> {
    return http.get('/webhook-endpoints/quota').then(r => r.data)
  },
  test(id: number, payload?: object): Promise<ApiResponse<any>> {
    return http.post(`/webhook-endpoints/${id}/test`, payload || {}).then(r => r.data)
  },
}

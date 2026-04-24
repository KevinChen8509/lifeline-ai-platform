import http from './http'
import type { ApiResponse, PageResponse } from '@/types'
import type { Device, DeviceDataPoint, TreeNode } from '@/types/device'

export default {
  list(params?: { productId?: number; page?: number; size?: number }): Promise<ApiResponse<PageResponse<Device>>> {
    return http.get('/devices', { params }).then(r => r.data)
  },
  getDetail(id: number): Promise<ApiResponse<Device>> {
    return http.get(`/devices/${id}`).then(r => r.data)
  },
  getDataPoints(deviceId: number): Promise<ApiResponse<DeviceDataPoint[]>> {
    return http.get(`/devices/${deviceId}/datapoints`).then(r => r.data)
  },
  getGroupTree(): Promise<ApiResponse<TreeNode[]>> {
    return http.get('/device-groups/tree').then(r => r.data)
  },
}

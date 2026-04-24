export interface Device {
  id: number
  tenantId: number
  productId: number
  deviceName: string
  deviceKey: string
  status: number  // 0=未激活 1=在线 2=离线 3=禁用
  lastActiveAt: string | null
  tags: Record<string, string> | null
  createdAt: string
}

export interface DeviceDataPoint {
  id: number
  deviceId: number
  identifier: string
  dataType: string
  lastValue: string | null
  lastReportAt: string | null
  quality: number
}

export interface TreeNode {
  id: number
  name: string
  groupType: number
  deviceCount: number
  children?: TreeNode[]
}

export type DeviceStatus = 'inactive' | 'online' | 'offline' | 'disabled'

import { request } from './request';

// Types
export interface Device {
  id: string;
  name: string;
  sn: string;
  status: 'pending' | 'activating' | 'online' | 'offline' | 'alert' | 'maintenance' | 'failed';
  source: 'self_developed' | 'third_party';
  protocol: 'mqtt' | 'modbus_tcp' | 'modbus_rtu' | 'http';
  projectId: string | null;
  project?: { id: string; name: string };
  config?: DeviceConfig;
  lastSeenAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DeviceConfig {
  collectInterval?: number;
  uploadInterval?: number;
  alertThresholds?: Record<string, number>;
}

export interface DeviceListResponse {
  items: Device[];
  total: number;
  page: number;
  pageSize: number;
}

export interface DeviceQueryParams {
  page?: number;
  pageSize?: number;
  search?: string;
  status?: string;
  projectId?: string;
  protocol?: string;
}

export interface CreateDeviceDto {
  name: string;
  sn: string;
  source: 'self_developed' | 'third_party';
  protocol: 'mqtt' | 'modbus_tcp' | 'modbus_rtu' | 'http';
  projectId?: string;
}

export interface StatusHistory {
  id: string;
  deviceId: string;
  fromStatus: string | null;
  toStatus: string;
  reason: string | null;
  createdAt: string;
}

// API functions
export function getDeviceList(params?: DeviceQueryParams): Promise<DeviceListResponse> {
  return request.get('/devices', { params });
}

export function getDevice(id: string): Promise<Device> {
  return request.get(`/devices/${id}`);
}

export function createDevice(data: CreateDeviceDto): Promise<Device> {
  return request.post('/devices', data);
}

export function updateDevice(id: string, data: Partial<CreateDeviceDto>): Promise<Device> {
  return request.put(`/devices/${id}`, data);
}

export function updateDeviceConfig(id: string, config: DeviceConfig): Promise<Device> {
  return request.put(`/devices/${id}/config`, config);
}

export function getStatusHistory(id: string, params?: { page?: number; pageSize?: number }): Promise<{ items: StatusHistory[]; total: number }> {
  return request.get(`/devices/${id}/status-history`, { params });
}

export function assignProject(deviceId: string, projectId: string): Promise<Device> {
  return request.post(`/devices/${deviceId}/assign-project`, { projectId });
}

export function batchUpdateConfig(data: { deviceIds: string[]; config: DeviceConfig }): Promise<void> {
  return request.post('/devices/batch-config', data);
}

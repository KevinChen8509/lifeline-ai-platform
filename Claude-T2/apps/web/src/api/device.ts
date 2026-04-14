import { request } from './request';

// Types
export interface Device {
  id: string;
  name: string;
  serialNumber: string;
  status: 'pending' | 'activating' | 'online' | 'offline' | 'alert' | 'maintenance' | 'failed';
  source: 'self_developed' | 'third_party';
  protocol: 'mqtt' | 'modbus_tcp' | 'modbus_rtu' | 'http';
  projectId: string | null;
  project?: { id: string; name: string };
  config?: string;
  deviceType?: string;
  firmwareVersion?: string;
  lastOnlineAt: string | null;
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
  serialNumber: string;
  source?: string;
  protocol?: string;
  projectId?: string;
  deviceType?: string;
  description?: string;
}

export interface StatusHistory {
  id: string;
  deviceId: string;
  fromStatus: string | null;
  toStatus: string;
  reason: string | null;
  operatorId: string | null;
  timestamp: string;
  createdAt: string;
}

export interface DeviceModelBinding {
  id: string;
  deviceId: string;
  modelId: string;
  model?: any;
  status: string;
  boundVersion: string | null;
  boundAt: string;
  lastSyncAt: string | null;
  error: string | null;
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

export function deleteDevice(id: string): Promise<void> {
  return request.delete(`/devices/${id}`);
}

export function updateDeviceConfig(id: string, config: any): Promise<Device> {
  return request.put(`/devices/${id}/config`, config);
}

export function getStatusHistory(id: string, params?: { page?: number; pageSize?: number; startDate?: string; endDate?: string }): Promise<{ items: StatusHistory[]; total: number }> {
  return request.get(`/devices/${id}/status-history`, { params });
}

export function activateDevice(id: string): Promise<any> {
  return request.post(`/devices/${id}/activate`);
}

export function otaUpgrade(id: string, targetVersion: string): Promise<any> {
  return request.post(`/devices/${id}/ota`, { targetVersion });
}

export function scanRegister(qrData: string): Promise<any> {
  return request.post('/devices/scan-register', { qrData });
}

export function assignProject(deviceId: string, projectId: string | null): Promise<Device> {
  return request.put(`/devices/${deviceId}/project`, { projectId });
}

export function batchAssignProject(deviceIds: string[], projectId: string | null): Promise<any> {
  return request.post('/devices/batch-assign-project', { deviceIds, projectId });
}

export function batchUpdateConfig(data: { deviceIds: string[]; config: any }): Promise<any> {
  return request.post('/devices/batch-config', data);
}

export function bindModels(deviceId: string, modelIds: string[]): Promise<any> {
  return request.post(`/devices/${deviceId}/models`, { modelIds });
}

export function unbindModel(deviceId: string, modelId: string): Promise<any> {
  return request.delete(`/devices/${deviceId}/models/${modelId}`);
}

export function getBoundModels(deviceId: string, params?: { page?: number; pageSize?: number }): Promise<{ items: DeviceModelBinding[]; total: number }> {
  return request.get(`/devices/${deviceId}/models`, { params });
}

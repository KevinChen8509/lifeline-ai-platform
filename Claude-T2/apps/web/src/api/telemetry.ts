import { request } from './request';

export interface TelemetryData {
  id: string;
  deviceId: string;
  timestamp: string;
  metrics: string;
  createdAt: string;
}

export interface TelemetryListResponse {
  items: TelemetryData[];
  total: number;
  page: number;
  pageSize: number;
}

export interface BackupConfig {
  id: string;
  type: 'incremental' | 'full';
  schedule: string;
  retentionDays: number;
  storagePath: string | null;
  isEnabled: boolean;
  createdAt: string;
}

export interface BackupLog {
  id: string;
  configId: string | null;
  type: 'incremental' | 'full';
  status: 'pending' | 'running' | 'completed' | 'failed';
  filePath: string | null;
  fileSize: number | null;
  duration: number | null;
  error: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export function getTelemetryList(params?: { deviceId?: string; page?: number; pageSize?: number; startTime?: string; endTime?: string }): Promise<TelemetryListResponse> {
  return request.get('/telemetry', { params });
}

export function getDeviceTelemetry(deviceId: string, params?: { page?: number; pageSize?: number; startTime?: string; endTime?: string }): Promise<TelemetryListResponse> {
  return request.get(`/devices/${deviceId}/telemetry`, { params });
}

export function writeTelemetry(deviceId: string, data: { metrics: any; timestamp?: string }): Promise<TelemetryData> {
  return request.post(`/devices/${deviceId}/telemetry`, data);
}

export function getTelemetryChart(deviceId: string, params?: { metrics?: string; interval?: string; startTime?: string; endTime?: string }): Promise<any> {
  return request.get(`/devices/${deviceId}/telemetry/chart`, { params });
}

// Backup
export function createBackupConfig(data: { type: string; schedule?: string; retentionDays?: number; storagePath?: string }): Promise<BackupConfig> {
  return request.post('/backup/configs', data);
}

export function listBackupConfigs(): Promise<BackupConfig[]> {
  return request.get('/backup/configs');
}

export function executeBackup(configId: string): Promise<BackupLog> {
  return request.post(`/backup/configs/${configId}/execute`);
}

export function listBackupLogs(params?: { type?: string; status?: string }): Promise<BackupLog[]> {
  return request.get('/backup/logs', { params });
}

export function restoreBackup(backupId: string): Promise<BackupLog> {
  return request.post(`/backup/${backupId}/restore`);
}

import { request } from './request';

export interface DashboardStats {
  totalDevices: number;
  onlineDevices: number;
  offlineDevices: number;
  alertDevices: number;
  todayAlerts: number;
  onlineRate: number;
}

export interface AlertDistribution {
  stats: Record<string, number>;
}

export interface SystemStatus {
  cpu: number;
  memory: number;
  uptime: number;
  version: string;
}

export function getDashboardStats(projectId?: string): Promise<DashboardStats> {
  return request.get('/dashboard/stats', { params: { projectId } });
}

export function getAlertDistribution(projectId?: string): Promise<AlertDistribution> {
  return request.get('/dashboard/alert-distribution', { params: { projectId } });
}

export function getSystemStatus(): Promise<SystemStatus> {
  return request.get('/system/status');
}

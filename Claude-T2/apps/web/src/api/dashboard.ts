import { request } from './request';

export interface DashboardStats {
  totalProjects: number;
  totalDevices: number;
  totalUsers: number;
  pendingAlerts: number;
}

export interface AlertDistribution {
  [key: string]: number;
}

export function getDashboardStats(): Promise<DashboardStats> {
  return request.get('/dashboard/stats');
}

export function getAlertDistribution(): Promise<AlertDistribution> {
  return request.get('/dashboard/alert-distribution');
}

export function getSystemStatus(): Promise<any> {
  return request.get('/dashboard/system-status');
}

export function getProjectDeviceStats(projectId: string): Promise<any> {
  return request.get(`/dashboard/projects/${projectId}/device-stats`);
}

export function getProjectAlertStats(projectId: string): Promise<any> {
  return request.get(`/dashboard/projects/${projectId}/alert-stats`);
}

export function getProjectKpi(projectId: string): Promise<any> {
  return request.get(`/dashboard/projects/${projectId}/kpi`);
}

// Statistics
export function getAlertTypeDistribution(params?: { projectId?: string; startDate?: string; endDate?: string }): Promise<any> {
  return request.get('/statistics/alert-type-distribution', { params });
}

export function getAlertHandlingEfficiency(params?: { projectId?: string; startDate?: string; endDate?: string }): Promise<any> {
  return request.get('/statistics/alert-handling-efficiency', { params });
}

// System
export function getSystemResources(): Promise<any> {
  return request.get('/system/resources');
}

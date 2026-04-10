import { request } from './request';

// Types
export type AlertType = 'mixed_connection' | 'silt' | 'overflow' | 'full_pipe' | 'threshold_exceeded';
export type AlertLevel = 'critical' | 'high' | 'medium' | 'low';
export type AlertStatus = 'pending' | 'acknowledged' | 'in_progress' | 'resolved' | 'closed';

export interface Alert {
  id: string;
  type: AlertType;
  title: string;
  content: string;
  level: AlertLevel;
  status: AlertStatus;
  confidence: number;
  deviceId: string;
  device?: { id: string; name: string };
  projectId: string | null;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  closedBy: string | null;
  closedAt: string | null;
  resolution: string | null;
  rootCause: string | null;
  isEscalated: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AlertListResponse {
  items: Alert[];
  total: number;
  page: number;
  pageSize: number;
  stats: Record<string, number>;
}

export interface AlertQueryParams {
  page?: number;
  pageSize?: number;
  type?: AlertType;
  level?: AlertLevel;
  status?: AlertStatus;
  deviceId?: string;
  projectId?: string;
  startTime?: string;
  endTime?: string;
  search?: string;
}

export interface AlertStats {
  critical: number;
  high: number;
  medium: number;
  low: number;
  unacknowledged: number;
}

// API functions
export function getAlertList(params?: AlertQueryParams): Promise<AlertListResponse> {
  return request.get('/alerts', { params });
}

export function getAlert(id: string): Promise<Alert> {
  return request.get(`/alerts/${id}`);
}

export function getAlertStats(projectId?: string): Promise<AlertStats> {
  return request.get('/alerts/stats/summary', { params: { projectId } });
}

export function acknowledgeAlert(id: string, note?: string): Promise<Alert> {
  return request.post(`/alerts/${id}/acknowledge`, { note });
}

export function processAlert(id: string, description: string): Promise<Alert> {
  return request.post(`/alerts/${id}/process`, { description });
}

export function closeAlert(id: string, resolution: string, rootCause?: string): Promise<Alert> {
  return request.post(`/alerts/${id}/close`, { resolution, rootCause });
}

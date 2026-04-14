import { request } from './request';

export interface ReportTemplate {
  id: string;
  name: string;
  type: 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'CUSTOM';
  sections: string | null;
  isDefault: boolean;
  projectId: string | null;
  createdAt: string;
}

export interface Report {
  id: string;
  title: string;
  type: 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'CUSTOM';
  projectId: string;
  templateId: string;
  status: 'GENERATING' | 'COMPLETED' | 'FAILED';
  startDate: string;
  endDate: string;
  data: string | null;
  filePath: string | null;
  fileSize: number | null;
  generatedAt: string | null;
  generatedBy: string;
  createdAt: string;
}

export interface ScheduledReport {
  id: string;
  name: string;
  type: 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'CUSTOM';
  cron: string;
  projectIds: string;
  recipients: string;
  templateId: string;
  status: 'ACTIVE' | 'PAUSED';
  lastRunAt: string | null;
  nextRunAt: string | null;
  createdBy: string;
  createdAt: string;
}

// Report Templates
export function listTemplates(projectId?: string): Promise<ReportTemplate[]> {
  return request.get('/report-templates', { params: { projectId } });
}

export function getTemplate(id: string): Promise<ReportTemplate> {
  return request.get(`/report-templates/${id}`);
}

export function createTemplate(data: { name: string; type: string; sections?: string; isDefault?: boolean; projectId?: string }): Promise<ReportTemplate> {
  return request.post('/report-templates', data);
}

export function updateTemplate(id: string, data: { name?: string; sections?: string }): Promise<ReportTemplate> {
  return request.put(`/report-templates/${id}`, data);
}

export function deleteTemplate(id: string): Promise<void> {
  return request.delete(`/report-templates/${id}`);
}

// Reports
export function generateReport(data: { type: string; projectId: string; startDate: string; endDate: string; templateId: string }): Promise<Report> {
  return request.post('/reports/generate', data);
}

export function listReports(params?: { projectId?: string; type?: string; page?: number; pageSize?: number }): Promise<{ items: Report[]; total: number; page: number; pageSize: number }> {
  return request.get('/reports', { params });
}

export function getReport(id: string): Promise<Report> {
  return request.get(`/reports/${id}`);
}

export function deleteReport(id: string): Promise<void> {
  return request.delete(`/reports/${id}`);
}

export function exportPdf(id: string): Promise<{ filePath: string; fileSize: number }> {
  return request.post(`/reports/${id}/export-pdf`);
}

// Scheduled Reports
export function listScheduledReports(): Promise<ScheduledReport[]> {
  return request.get('/scheduled-reports');
}

export function createScheduledReport(data: { name: string; type: string; projectIds: string; recipients: string; templateId: string }): Promise<ScheduledReport> {
  return request.post('/scheduled-reports', data);
}

export function updateScheduledReport(id: string, data: { name?: string; projectIds?: string; recipients?: string; status?: string }): Promise<ScheduledReport> {
  return request.put(`/scheduled-reports/${id}`, data);
}

export function deleteScheduledReport(id: string): Promise<void> {
  return request.delete(`/scheduled-reports/${id}`);
}

export function executeScheduledReport(id: string): Promise<Report> {
  return request.post(`/scheduled-reports/${id}/execute`);
}

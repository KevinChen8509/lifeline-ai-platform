import { request } from '../request';

export interface Project {
  id: string;
  name: string;
  code: string;
  description: string | null;
  settings: Record<string, any>;
  status: 'active' | 'archived';
  createdAt: string;
  updatedAt: string;
}

export interface ProjectListResponse {
  items: Project[];
  total: number;
  page: number;
  pageSize: number;
}

export interface CreateProjectDto {
  name: string;
  code: string;
  description?: string;
}

export interface UpdateProjectDto {
  name?: string;
  code?: string;
  description?: string;
  status?: 'active' | 'archived';
}

export interface ProjectQueryParams {
  page?: number;
  pageSize?: number;
  search?: string;
  status?: 'active' | 'archived';
}

/**
 * 获取项目列表
 */
export function getProjectList(params?: ProjectQueryParams): Promise<ProjectListResponse> {
  return request.get('/projects', { params });
}

/**
 * 获取项目详情
 */
export function getProject(id: string): Promise<Project> {
  return request.get(`/projects/${id}`);
}

/**
 * 获取项目概览
 */
export function getProjectOverview(id: string): Promise<{
  stats: {
    totalDevices: number;
    onlineDevices: number;
    offlineDevices: number;
    alertDevices: number;
  };
  recentActivities: Array<{
    id: string;
    type: string;
    description: string;
    timestamp: string;
  }>;
}> {
  return request.get(`/projects/${id}/overview`);
}

/**
 * 创建项目
 */
export function createProject(data: CreateProjectDto): Promise<Project> {
  return request.post('/projects', data);
}

/**
 * 更新项目
 */
export function updateProject(id: string, data: UpdateProjectDto): Promise<Project> {
  return request.put(`/projects/${id}`, data);
}

/**
 * 归档项目
 */
export function archiveProject(id: string): Promise<Project> {
  return request.put(`/projects/${id}/archive`);
}

/**
 * 恢复项目
 */
export function restoreProject(id: string): Promise<Project> {
  return request.put(`/projects/${id}/restore`);
}

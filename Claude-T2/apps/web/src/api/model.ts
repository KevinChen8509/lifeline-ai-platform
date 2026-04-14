import { request } from './request';

// Types
export interface AiModel {
  id: string;
  name: string;
  code: string;
  type: string;
  version: string;
  description: string | null;
  fileUrl: string | null;
  fileSize: number | null;
  checksum: string | null;
  status: 'draft' | 'published' | 'deprecated';
  specs: string | null;
  applicableDeviceTypes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AiModelVersion {
  id: string;
  modelId: string;
  version: string;
  status: 'draft' | 'published' | 'deprecated';
  fileUrl: string | null;
  fileSize: number | null;
  checksum: string | null;
  signature: string | null;
  changeLog: string | null;
  specs: string | null;
  publishedAt: string | null;
  publishedBy: string | null;
  isCurrent: boolean;
  createdAt: string;
}

export interface ModelDeployment {
  id: string;
  modelId: string;
  targetVersion: string;
  status: string;
  totalDevices: number;
  successCount: number;
  failedCount: number;
  inProgressCount: number;
  pendingCount: number;
  createdBy: string;
  description: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export interface DeviceDeployment {
  id: string;
  deploymentId: string;
  deviceId: string;
  status: string;
  failureReason: string | null;
  error: string | null;
  progress: number;
  startedAt: string | null;
  completedAt: string | null;
  retryCount: number;
}

export interface ModelListResponse {
  items: AiModel[];
  total: number;
  page: number;
  pageSize: number;
}

export interface CreateModelDto {
  name: string;
  code: string;
  version: string;
  type: string;
  description?: string;
  fileUrl?: string;
  fileSize?: number;
  checksum?: string;
  specs?: string;
  applicableDeviceTypes?: string;
}

// Model CRUD
export function getModelList(params?: { page?: number; pageSize?: number; search?: string; status?: string; type?: string }): Promise<ModelListResponse> {
  return request.get('/ai-models', { params });
}

export function getModel(id: string): Promise<AiModel> {
  return request.get(`/ai-models/${id}`);
}

export function createModel(data: CreateModelDto): Promise<AiModel> {
  return request.post('/ai-models', data);
}

export function updateModel(id: string, data: Partial<CreateModelDto>): Promise<AiModel> {
  return request.put(`/ai-models/${id}`, data);
}

export function publishModel(id: string): Promise<AiModel> {
  return request.put(`/ai-models/${id}/publish`);
}

export function deprecateModel(id: string): Promise<AiModel> {
  return request.put(`/ai-models/${id}/deprecate`);
}

export function deleteModel(id: string): Promise<void> {
  return request.delete(`/ai-models/${id}`);
}

// Version Management
export function listVersions(modelId: string, status?: string): Promise<AiModelVersion[]> {
  return request.get(`/ai-models/${modelId}/versions`, { params: { status } });
}

export function getCurrentVersion(modelId: string): Promise<AiModelVersion | null> {
  return request.get(`/ai-models/${modelId}/versions/current`);
}

export function getVersion(modelId: string, versionId: string): Promise<AiModelVersion> {
  return request.get(`/ai-models/${modelId}/versions/${versionId}`);
}

export function createVersion(modelId: string, data: { version: string; fileUrl?: string; fileSize?: number; checksum?: string; changeLog?: string }): Promise<AiModelVersion> {
  return request.post(`/ai-models/${modelId}/versions`, data);
}

export function publishVersion(modelId: string, versionId: string): Promise<AiModelVersion> {
  return request.put(`/ai-models/${modelId}/versions/${versionId}/publish`);
}

export function deprecateVersion(modelId: string, versionId: string): Promise<AiModelVersion> {
  return request.put(`/ai-models/${modelId}/versions/${versionId}/deprecate`);
}

// Deployment Management
export function createDeployment(modelId: string, data: { deviceIds: string[]; version?: string; description?: string }): Promise<ModelDeployment> {
  return request.post(`/ai-models/${modelId}/deploy`, data);
}

export function listDeployments(modelId: string, params?: { status?: string; page?: number; pageSize?: number }): Promise<{ items: ModelDeployment[]; total: number }> {
  return request.get(`/ai-models/${modelId}/deployments`, { params });
}

export function getDeploymentDetail(modelId: string, deploymentId: string): Promise<{ deployment: ModelDeployment; deviceDeployments: DeviceDeployment[]; progress: any }> {
  return request.get(`/ai-models/${modelId}/deployments/${deploymentId}`);
}

export function retryDeployment(modelId: string, deploymentId: string): Promise<any> {
  return request.post(`/ai-models/${modelId}/deployments/${deploymentId}/retry`);
}

export function cancelDeployment(modelId: string, deploymentId: string): Promise<ModelDeployment> {
  return request.post(`/ai-models/${modelId}/deployments/${deploymentId}/cancel`);
}

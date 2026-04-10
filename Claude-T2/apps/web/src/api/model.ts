import { request } from './request';

// Types
export interface AiModel {
  id: string;
  name: string;
  type: string;
  description: string | null;
  framework: string | null;
  inputSchema: Record<string, any> | null;
  outputSchema: Record<string, any> | null;
  status: string;
  versions?: AiModelVersion[];
  createdAt: string;
  updatedAt: string;
}

export interface AiModelVersion {
  id: string;
  modelId: string;
  version: string;
  filePath: string | null;
  fileSize: number | null;
  metrics: Record<string, any> | null;
  isLatest: boolean;
  createdAt: string;
}

export interface ModelListResponse {
  items: AiModel[];
  total: number;
  page: number;
  pageSize: number;
}

export interface CreateModelDto {
  name: string;
  type: string;
  description?: string;
  framework?: string;
}

// API functions
export function getModelList(params?: { page?: number; pageSize?: number; search?: string }): Promise<ModelListResponse> {
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

export function deleteModel(id: string): Promise<void> {
  return request.delete(`/ai-models/${id}`);
}

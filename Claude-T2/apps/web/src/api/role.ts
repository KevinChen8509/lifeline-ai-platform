import { request } from './request';

export interface Role {
  id: string;
  name: string;
  code: string;
  description?: string;
  permissions?: Array<{ action: string; subject: string }>;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * 获取角色列表
 */
export function getRoles(): Promise<Role[]> {
  return request.get('/roles');
}

/**
 * 获取角色详情
 */
export function getRole(id: string): Promise<Role> {
  return request.get(`/roles/${id}`);
}

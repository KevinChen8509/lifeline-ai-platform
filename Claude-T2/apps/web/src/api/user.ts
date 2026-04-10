import { request } from './request';

export interface User {
  id: string;
  username: string;
  name: string;
  email?: string;
  phone?: string;
  status: 'ACTIVE' | 'DISABLED' | 'PENDING';
  roleId: string | null;
  role: {
    id: string;
    name: string;
    code: string;
  } | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface UserListParams {
  page?: number;
  pageSize?: number;
  status?: string;
  search?: string;
}

export interface UserListResponse {
  items: User[];
  total: number;
  page: number;
  pageSize: number;
}

export interface CreateUserParams {
  username: string;
  name: string;
  email?: string;
  phone?: string;
  password: string;
  roleId?: string;
}

export interface UpdateUserParams {
  name?: string;
  email?: string;
  phone?: string;
}

export interface UpdateUserRoleParams {
  roleId: string;
}

/**
 * 获取用户列表
 */
export function getUsers(params: UserListParams): Promise<UserListResponse> {
  return request.get('/users', { params });
}

/**
 * 获取用户详情
 */
export function getUser(id: string): Promise<User> {
  return request.get(`/users/${id}`);
}

/**
 * 创建用户
 */
export function createUser(data: CreateUserParams): Promise<User> {
  return request.post('/users', data);
}

/**
 * 更新用户信息
 */
export function updateUser(id: string, data: UpdateUserParams): Promise<User> {
  return request.put(`/users/${id}`, data);
}

/**
 * 更新用户角色
 */
export function updateUserRole(id: string, data: UpdateUserRoleParams): Promise<User> {
  return request.put(`/users/${id}/role`, data);
}

/**
 * 更新用户状态
 */
export function updateUserStatus(id: string, status: string): Promise<User> {
  return request.put(`/users/${id}/status`, { status });
}

/**
 * 删除用户
 */
export function deleteUser(id: string): Promise<void> {
  return request.delete(`/users/${id}`);
}

/**
 * 检查用户名是否可用
 */
export function checkUsername(username: string): Promise<{ available: boolean }> {
  return request.get('/users/check-username', { params: { username } });
}

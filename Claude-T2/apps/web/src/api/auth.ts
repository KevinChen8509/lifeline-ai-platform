import { request } from './request';

export interface LoginParams {
  username: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: {
    id: string;
    username: string;
    name: string;
    email?: string;
    status: string;
    roleId: string | null;
    role: { id: string; name: string; code: string } | null;
  };
  permissions: PermissionRule[];
}

export interface RefreshTokenParams {
  refreshToken: string;
}

export interface PermissionRule {
  action: string;
  subject: string;
}

export interface PermissionsResponse {
  permissions: PermissionRule[];
}

/**
 * 用户登录
 */
export function login(data: LoginParams): Promise<AuthResponse> {
  return request.post('/auth/login', data);
}

/**
 * 刷新Token
 */
export function refreshToken(data: RefreshTokenParams): Promise<AuthResponse> {
  return request.post('/auth/refresh', data);
}

/**
 * 用户登出
 */
export function logout(): Promise<{ message: string }> {
  return request.post('/auth/logout');
}

/**
 * 获取当前用户权限
 */
export function getPermissions(): Promise<PermissionsResponse> {
  return request.get('/auth/permissions');
}

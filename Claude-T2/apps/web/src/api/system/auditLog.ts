import { request } from '../request';

export interface AuditLogItem {
  id: string;
  action: string;
  targetType: string;
  targetId: string | null;
  operatorId: string;
  operator: {
    id: string;
    username: string;
    name: string;
  } | null;
  oldValue: Record<string, any> | null;
  newValue: Record<string, any> | null;
  description: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
}

export interface AuditLogListResponse {
  items: AuditLogItem[];
  total: number;
  page: number;
  pageSize: number;
}

export interface QueryAuditLogsParams {
  startTime?: string;
  endTime?: string;
  userId?: string;
  action?: string;
  targetType?: string;
  targetId?: string;
  page?: number;
  pageSize?: number;
}

/**
 * 查询审计日志
 */
export function getAuditLogs(params: QueryAuditLogsParams) {
  return request.get<AuditLogListResponse>('/api/v1/audit-logs', { params });
}

/**
 * 导出审计日志
 */
export function exportAuditLogs(params: QueryAuditLogsParams) {
  return request.get('/api/v1/audit-logs/export', {
    params,
    responseType: 'blob',
  });
}

/**
 * 操作类型选项
 */
export const actionOptions = [
  { label: '全部', value: '' },
  { label: '登录', value: 'LOGIN' },
  { label: '登出', value: 'LOGOUT' },
  { label: '登录失败', value: 'LOGIN_FAILED' },
  { label: '创建用户', value: 'CREATE_USER' },
  { label: '更新用户', value: 'UPDATE_USER' },
  { label: '删除用户', value: 'DELETE_USER' },
  { label: '分配角色', value: 'ASSIGN_ROLE' },
  { label: '更新状态', value: 'UPDATE_STATUS' },
  { label: '创建角色', value: 'CREATE_ROLE' },
  { label: '更新角色', value: 'UPDATE_ROLE' },
  { label: '删除角色', value: 'DELETE_ROLE' },
];

/**
 * 目标类型选项
 */
export const targetTypeOptions = [
  { label: '全部', value: '' },
  { label: '用户', value: 'User' },
  { label: '角色', value: 'Role' },
  { label: '项目', value: 'Project' },
  { label: '设备', value: 'Device' },
  { label: '模型', value: 'Model' },
  { label: '预警', value: 'Alert' },
  { label: 'API密钥', value: 'ApiKey' },
  { label: '系统', value: 'System' },
];

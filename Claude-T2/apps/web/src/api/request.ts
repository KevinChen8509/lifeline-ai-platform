import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { message } from 'ant-design-vue';

// API 响应格式
export interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

// Token刷新状态管理
let isRefreshing = false;
let refreshSubscribers: Array<{
  resolve: (token: string) => void;
  reject: (error: Error) => void;
}> = [];

// 安全的 localStorage 操作
const safeStorage = {
  get(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      console.warn('localStorage not available');
      return null;
    }
  },
  set(key: string, value: string): boolean {
    try {
      localStorage.setItem(key, value);
      return true;
    } catch {
      console.warn('localStorage not available');
      return false;
    }
  },
  remove(key: string): void {
    try {
      localStorage.removeItem(key);
    } catch {
      console.warn('localStorage not available');
    }
  },
};

// 解析 JWT 获取过期时间
function parseJwtExpiration(token: string): number | null {
  try {
    const base64Url = token.split('.')[1];
    if (!base64Url) return null;
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    );
    const payload = JSON.parse(jsonPayload);
    return payload.exp ? payload.exp * 1000 : null; // 转换为毫秒
  } catch {
    return null;
  }
}

// 检查 Token 是否即将过期（2分钟内）
function isTokenExpiringSoon(token: string | null): boolean {
  if (!token) return true;
  const expTime = parseJwtExpiration(token);
  if (!expTime) return true;
  const now = Date.now();
  const twoMinutes = 2 * 60 * 1000;
  return expTime - now < twoMinutes;
}

// 订阅Token刷新
function subscribeTokenRefresh(
  resolve: (token: string) => void,
  reject: (error: Error) => void,
): void {
  refreshSubscribers.push({ resolve, reject });
}

// 通知所有订阅者Token已刷新
function onTokenRefreshed(token: string): void {
  refreshSubscribers.forEach(({ resolve }) => resolve(token));
  refreshSubscribers = [];
}

// Token刷新失败，拒绝所有订阅者
function onTokenRefreshFailed(error: Error): void {
  // 重要：拒绝所有等待的 Promise，防止内存泄漏
  refreshSubscribers.forEach(({ reject }) => reject(error));
  refreshSubscribers = [];
  safeStorage.remove('access_token');
  safeStorage.remove('refresh_token');
  window.location.href = '/login';
}

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器
service.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    // 从 localStorage 获取 token
    const token = safeStorage.get('access_token');

    // 主动检查 Token 是否即将过期（非刷新请求）
    if (
      token &&
      isTokenExpiringSoon(token) &&
      !config.url?.includes('/auth/refresh') &&
      !config.url?.includes('/auth/login')
    ) {
      const refreshToken = safeStorage.get('refresh_token');

      if (refreshToken && !isRefreshing) {
        isRefreshing = true;

        try {
          const response = await axios.post<ApiResponse>('/api/auth/refresh', {
            refreshToken,
          });

          const { accessToken, refreshToken: newRefreshToken } = response.data.data;

          safeStorage.set('access_token', accessToken);
          safeStorage.set('refresh_token', newRefreshToken);

          isRefreshing = false;
          onTokenRefreshed(accessToken);

          // 使用新 Token
          if (config.headers) {
            config.headers.Authorization = `Bearer ${accessToken}`;
          }
          return config;
        } catch {
          isRefreshing = false;
          // 主动刷新失败，继续使用旧 Token，让 401 处理
        }
      }
    }

    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    console.error('Request error:', error);
    return Promise.reject(error);
  },
);

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data;

    // 业务成功
    if (res.code === 0) {
      return res.data;
    }

    // 业务失败
    message.error(res.message || '请求失败');
    return Promise.reject(new Error(res.message || 'Error'));
  },
  async (error) => {
    console.error('Response error:', error);

    const originalRequest = error.config;

    // 处理401错误 - Token过期自动刷新
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // 正在刷新Token，将请求加入队列等待
        return new Promise((resolve, reject) => {
          subscribeTokenRefresh(
            (token: string) => {
              originalRequest.headers.Authorization = `Bearer ${token}`;
              resolve(service(originalRequest));
            },
            (err: Error) => {
              reject(err);
            },
          );
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const refreshToken = safeStorage.get('refresh_token');

        if (!refreshToken) {
          throw new Error('No refresh token');
        }

        // 调用刷新Token接口
        const response = await axios.post<ApiResponse>('/api/auth/refresh', {
          refreshToken,
        });

        const { accessToken, refreshToken: newRefreshToken } = response.data.data;

        // 更新本地存储的Token
        safeStorage.set('access_token', accessToken);
        safeStorage.set('refresh_token', newRefreshToken);

        isRefreshing = false;
        onTokenRefreshed(accessToken);

        // 使用新Token重试原请求
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return service(originalRequest);
      } catch (refreshError) {
        isRefreshing = false;
        const error = refreshError instanceof Error ? refreshError : new Error('Token refresh failed');
        onTokenRefreshFailed(error);
        message.error('登录已过期，请重新登录');
        return Promise.reject(error);
      }
    }

    // 其他错误处理
    if (error.response) {
      const { status, data } = error.response;

      switch (status) {
        case 401:
          // Token刷新失败的情况（已经处理过）
          message.error('登录已过期，请重新登录');
          break;
        case 403:
          message.error(data?.message || '没有权限访问');
          break;
        case 404:
          message.error(data?.message || '请求的资源不存在');
          break;
        case 500:
          message.error(data?.message || '服务器错误');
          break;
        default:
          message.error(data?.message || error.message || '请求失败');
      }
    } else {
      message.error('网络错误，请检查网络连接');
    }

    return Promise.reject(error);
  },
);

// 封装请求方法
export const request = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, config);
  },

  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config);
  },

  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config);
  },

  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, config);
  },

  patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.patch(url, data, config);
  },
};

export default service;

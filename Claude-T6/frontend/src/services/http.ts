import axios from 'axios'
import type { AxiosInstance } from 'axios'

const http: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Response interceptor —统一错误处理
http.interceptors.response.use(
  (response) => response,
  (error) => {
    const msg = error.response?.data?.message || error.message || '请求失败'
    console.error(`[API Error] ${error.config?.method?.toUpperCase()} ${error.config?.url}: ${msg}`)
    return Promise.reject(error)
  }
)

export default http

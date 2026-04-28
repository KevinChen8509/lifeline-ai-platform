import axios from 'axios'
import type { AxiosInstance } from 'axios'

const http: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor — attach JWT token
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('jwt_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Response interceptor — unified error handling + 401 redirect
http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('jwt_token')
      // Redirect to login if not already there
      if (window.location.hash !== '#/login') {
        window.location.hash = '#/login'
      }
    }
    const msg = error.response?.data?.message || error.message || '请求失败'
    console.error(`[API Error] ${error.config?.method?.toUpperCase()} ${error.config?.url}: ${msg}`)
    return Promise.reject(error)
  }
)

// ---- Auth helpers ----

export function setToken(token: string) {
  localStorage.setItem('jwt_token', token)
}

export function getToken(): string | null {
  return localStorage.getItem('jwt_token')
}

export function clearToken() {
  localStorage.removeItem('jwt_token')
}

export function isAuthenticated(): boolean {
  return !!getToken()
}

export default http

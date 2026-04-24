export interface WebhookEndpoint {
  id: number
  name: string
  url: string
  customHeaders: Record<string, string> | null
  status: number  // 0=启用 1=禁用
  consecutiveFailures: number
  lastPushAt: string | null
  lastSuccessAt: string | null
  createdAt: string
}

export interface EndpointCreateRequest {
  name: string
  url: string
  customHeaders?: Record<string, string>
}

export interface EndpointUpdateRequest {
  name?: string
  url?: string
  customHeaders?: Record<string, string>
}

export interface SecretResponse {
  endpointId: number
  secret: string  // whsec_... 仅展示一次
}

export interface QuotaInfo {
  used: number
  limit: number
  percentage: number
}

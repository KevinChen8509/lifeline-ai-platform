export interface DeliveryLog {
  id: number
  eventId: string
  event: string
  subscriptionId: number
  subscriptionName?: string
  configId: number
  endpointUrl?: string
  deviceId: number
  status: string  // PENDING/SUCCESS/FAILED/RETRYING/DEAD
  attemptCount: number
  maxRetries: number
  responseCode: number | null
  responseBody: string | null
  errorMsg: string | null
  source: string | null
  stormGuardDegraded: boolean
  ruleMatchSkipped: boolean
  nextRetryAt: string | null
  deliveredAt: string | null
  createdAt: string
}

export interface PushStats {
  totalLast24h: number
  successRate: number
  avgLatencyMs?: number
  p95LatencyMs?: number
}

export interface LogQueryParams {
  subscriptionId?: number
  endpointId?: number
  status?: string
  page?: number
  size?: number
}

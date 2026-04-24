export interface Subscription {
  id: number
  name: string
  endpointId: number
  endpointName?: string
  subscriptionType: number  // 0=设备级 1=设备类型级 2=分组级
  targetId: number
  targetName?: string
  dataPointIds: number[] | null
  status: number  // 0=启用 1=暂停 2=已删除
  statusLabel: string
  rules: Rule[]
  createdAt: string
  updatedAt: string
}

export interface Rule {
  id?: number
  dataPointId?: number | null
  ruleType: number  // 0=阈值 1=变化率 2=离线检测
  conditionJson: string
  cooldownSeconds: number
  priority: number  // 0=Info 1=Warning 2=Critical
  enabled: boolean
}

export interface SubscriptionCreateRequest {
  name: string
  endpointId: number
  subscriptionType: number
  targetId: number
  dataPointIds?: number[]
  rules?: Rule[]
}

export type Operator = 'gt' | 'gte' | 'lt' | 'lte' | 'eq' | 'neq'

export interface ThresholdCondition {
  operator: Operator
  threshold: number
}

export interface OfflineCondition {
  type: 'offline'
  timeout: number  // 秒
}

import http from '../services/http'
import type { ApiResponse, PageResponse } from '../types'
import type { WebhookEndpoint, SecretResponse, QuotaInfo } from '../types/endpoint'
import type { Device, DeviceDataPoint, TreeNode } from '../types/device'
import type { Subscription, Rule } from '../types/subscription'
import type { DeliveryLog, PushStats } from '../types/webhook-log'
import type { Notification, NotificationPreference } from '../types/notification'

// ---- Mock Data ----

const endpoints: WebhookEndpoint[] = [
  { id: 1, name: '生产环境 Webhook', url: 'https://api.example.com/webhook/prod', customHeaders: { 'X-Custom': 'value' }, status: 0, consecutiveFailures: 0, lastPushAt: '2026-04-24T08:30:00', lastSuccessAt: '2026-04-24T08:30:00', createdAt: '2026-04-20T10:00:00' },
  { id: 2, name: '测试环境 Webhook', url: 'https://staging.example.com/hooks', customHeaders: null, status: 0, consecutiveFailures: 3, lastPushAt: '2026-04-24T07:15:00', lastSuccessAt: '2026-04-24T06:00:00', createdAt: '2026-04-21T14:00:00' },
  { id: 3, name: '告警通知服务', url: 'https://alerts.example.com/notify', customHeaders: { 'Authorization': 'Bearer token123' }, status: 1, consecutiveFailures: 0, lastPushAt: null, lastSuccessAt: null, createdAt: '2026-04-22T09:00:00' },
]

const devices: Device[] = [
  { id: 1, tenantId: 1, productId: 1, deviceName: '温度传感器-A01', deviceKey: 'temp_sensor_a01', status: 1, lastActiveAt: '2026-04-24T08:25:00', tags: { location: '车间1' }, createdAt: '2026-04-10T08:00:00' },
  { id: 2, tenantId: 1, productId: 1, deviceName: '温度传感器-A02', deviceKey: 'temp_sensor_a02', status: 1, lastActiveAt: '2026-04-24T08:20:00', tags: { location: '车间1' }, createdAt: '2026-04-10T08:00:00' },
  { id: 3, tenantId: 1, productId: 2, deviceName: '湿度传感器-B01', deviceKey: 'hum_sensor_b01', status: 2, lastActiveAt: '2026-04-23T15:00:00', tags: { location: '车间2' }, createdAt: '2026-04-11T10:00:00' },
  { id: 4, tenantId: 1, productId: 2, deviceName: '压力传感器-C01', deviceKey: 'press_sensor_c01', status: 1, lastActiveAt: '2026-04-24T08:30:00', tags: { location: '车间3' }, createdAt: '2026-04-12T09:00:00' },
  { id: 5, tenantId: 1, productId: 1, deviceName: '温度传感器-A03', deviceKey: 'temp_sensor_a03', status: 3, lastActiveAt: null, tags: null, createdAt: '2026-04-13T11:00:00' },
]

const dataPoints: DeviceDataPoint[] = [
  { id: 1, deviceId: 1, identifier: 'temperature', dataType: 'DOUBLE', lastValue: '25.6', lastReportAt: '2026-04-24T08:25:00', quality: 95 },
  { id: 2, deviceId: 1, identifier: 'humidity', dataType: 'DOUBLE', lastValue: '65.2', lastReportAt: '2026-04-24T08:25:00', quality: 88 },
  { id: 3, deviceId: 1, identifier: 'battery', dataType: 'INT', lastValue: '85', lastReportAt: '2026-04-24T08:20:00', quality: 100 },
  { id: 4, deviceId: 2, identifier: 'temperature', dataType: 'DOUBLE', lastValue: '26.1', lastReportAt: '2026-04-24T08:20:00', quality: 92 },
  { id: 5, deviceId: 2, identifier: 'humidity', dataType: 'DOUBLE', lastValue: '58.7', lastReportAt: '2026-04-24T08:20:00', quality: 90 },
]

const tree: TreeNode[] = [
  { id: 1, name: '温度传感器', groupType: 0, deviceCount: 3, children: [] },
  { id: 2, name: '湿度传感器', groupType: 0, deviceCount: 1, children: [] },
  { id: 3, name: '压力传感器', groupType: 0, deviceCount: 1, children: [] },
]

const rules: Rule[] = [
  { id: 1, dataPointId: 1, ruleType: 0, conditionJson: '{"operator":"gt","threshold":30}', cooldownSeconds: 300, priority: 2, enabled: true },
  { id: 2, dataPointId: 2, ruleType: 0, conditionJson: '{"operator":"gt","threshold":80}', cooldownSeconds: 600, priority: 1, enabled: true },
  { id: 3, ruleType: 2, conditionJson: '{"type":"offline","timeout":600}', cooldownSeconds: 300, priority: 1, enabled: true },
]

const subscriptions: Subscription[] = [
  { id: 1, name: '高温告警订阅', endpointId: 1, endpointName: '生产环境 Webhook', subscriptionType: 0, targetId: 1, targetName: '温度传感器-A01', dataPointIds: [1, 2], status: 0, statusLabel: '启用', rules: [rules[0], rules[1]], createdAt: '2026-04-15T10:00:00', updatedAt: '2026-04-20T14:00:00' },
  { id: 2, name: '设备离线监控', endpointId: 2, endpointName: '测试环境 Webhook', subscriptionType: 1, targetId: 1, targetName: '温度传感器', dataPointIds: null, status: 0, statusLabel: '启用', rules: [rules[2]], createdAt: '2026-04-16T08:00:00', updatedAt: '2026-04-18T09:00:00' },
  { id: 3, name: '压力异常告警', endpointId: 1, endpointName: '生产环境 Webhook', subscriptionType: 0, targetId: 4, targetName: '压力传感器-C01', dataPointIds: [3], status: 1, statusLabel: '暂停', rules: [rules[0]], createdAt: '2026-04-17T11:00:00', updatedAt: '2026-04-22T16:00:00' },
]

const logs: DeliveryLog[] = [
  { id: 1, eventId: 'evt-001', event: 'THRESHOLD_ALERT', subscriptionId: 1, subscriptionName: '高温告警订阅', configId: 1, endpointUrl: 'https://api.example.com/webhook/prod', deviceId: 1, status: 'SUCCESS', attemptCount: 1, maxRetries: 3, responseCode: 200, responseBody: '{"status":"ok"}', errorMsg: null, source: 'KAFKA', stormGuardDegraded: false, ruleMatchSkipped: false, nextRetryAt: null, deliveredAt: '2026-04-24T08:30:00', createdAt: '2026-04-24T08:30:00' },
  { id: 2, eventId: 'evt-002', event: 'THRESHOLD_ALERT', subscriptionId: 1, subscriptionName: '高温告警订阅', configId: 1, endpointUrl: 'https://api.example.com/webhook/prod', deviceId: 1, status: 'FAILED', attemptCount: 3, maxRetries: 3, responseCode: 503, responseBody: null, errorMsg: 'Service Unavailable', source: 'KAFKA', stormGuardDegraded: true, ruleMatchSkipped: false, nextRetryAt: '2026-04-24T09:00:00', deliveredAt: null, createdAt: '2026-04-24T07:30:00' },
  { id: 3, eventId: 'evt-003', event: 'DEVICE_OFFLINE', subscriptionId: 2, subscriptionName: '设备离线监控', configId: 2, endpointUrl: 'https://staging.example.com/hooks', deviceId: 3, status: 'RETRYING', attemptCount: 2, maxRetries: 3, responseCode: null, responseBody: null, errorMsg: 'Connection timeout', source: 'KAFKA', stormGuardDegraded: false, ruleMatchSkipped: false, nextRetryAt: '2026-04-24T08:45:00', deliveredAt: null, createdAt: '2026-04-24T07:45:00' },
  { id: 4, eventId: 'evt-004', event: 'THRESHOLD_ALERT', subscriptionId: 3, subscriptionName: '压力异常告警', configId: 1, endpointUrl: 'https://api.example.com/webhook/prod', deviceId: 4, status: 'DEAD', attemptCount: 3, maxRetries: 3, responseCode: null, responseBody: null, errorMsg: 'Max retries exceeded', source: 'KAFKA', stormGuardDegraded: false, ruleMatchSkipped: true, nextRetryAt: null, deliveredAt: null, createdAt: '2026-04-24T06:00:00' },
  { id: 5, eventId: 'evt-005', event: 'THRESHOLD_ALERT', subscriptionId: 1, subscriptionName: '高温告警订阅', configId: 1, endpointUrl: 'https://api.example.com/webhook/prod', deviceId: 2, status: 'SUCCESS', attemptCount: 1, maxRetries: 3, responseCode: 200, responseBody: '{"status":"received"}', errorMsg: null, source: 'KAFKA', stormGuardDegraded: false, ruleMatchSkipped: false, nextRetryAt: null, deliveredAt: '2026-04-24T08:25:00', createdAt: '2026-04-24T08:24:00' },
]

const notifications: Notification[] = [
  { id: 1, type: 'ENDPOINT_CIRCUIT_OPEN', title: '端点熔断器开启', content: '端点 #2 熔断器已开启，推送暂停。', relatedId: 2, relatedType: 'WEBHOOK_ENDPOINT', isRead: false, createdAt: '2026-04-24T08:00:00' },
  { id: 2, type: 'PUSH_DEAD', title: '推送进入死亡队列', content: '事件 evt-004 已超过最大重试次数。', relatedId: 4, relatedType: 'DELIVERY_LOG', isRead: false, createdAt: '2026-04-24T07:30:00' },
  { id: 3, type: 'ENDPOINT_CIRCUIT_CLOSED', title: '端点熔断器恢复', content: '端点 #1 熔断器已关闭，推送恢复。', relatedId: 1, relatedType: 'WEBHOOK_ENDPOINT', isRead: true, createdAt: '2026-04-24T06:00:00' },
]

// ---- Mock Adapter ----

let nextId = 100

const mockAdapter = {
  get(url: string, params?: any): any {
    if (url === '/webhook-endpoints') return mockOk(endpoints)
    if (url.match(/\/webhook-endpoints\/\d+$/)) {
      const id = Number(url.split('/').pop())
      return mockOk(endpoints.find(e => e.id === id) || null)
    }
    if (url === '/webhook-endpoints/quota') return mockOk({ used: endpoints.length, limit: 20, percentage: (endpoints.length / 20 * 100) })
    if (url === '/devices') return mockPage(devices, params?.page || 0, params?.size || 20)
    if (url.match(/\/devices\/\d+\/datapoints$/)) {
      const did = Number(url.split('/')[2])
      return mockOk(dataPoints.filter(dp => dp.deviceId === did))
    }
    if (url === '/device-groups/tree') return mockOk(tree)
    if (url === '/subscriptions') return mockPage(subscriptions, params?.page || 0, params?.size || 20)
    if (url.match(/\/subscriptions\/\d+$/)) {
      const id = Number(url.split('/').pop())
      return mockOk(subscriptions.find(s => s.id === id) || null)
    }
    if (url === '/subscriptions/conflicts') return mockOk([])
    if (url === '/webhook-logs') return mockPage(logs, params?.page || 0, params?.size || 20)
    if (url.match(/\/webhook-logs\/\d+$/)) {
      const id = Number(url.split('/').pop())
      return mockOk(logs.find(l => l.id === id) || null)
    }
    if (url === '/webhook-logs/stats') return mockOk({ totalLast24h: 156, successRate: 0.923, avgLatencyMs: 245, p95LatencyMs: 890 })
    if (url === '/notifications') return mockPage(notifications, params?.page || 0, params?.size || 20)
    if (url === '/notifications/unread-count') return mockOk(notifications.filter(n => !n.isRead).length)
    if (url === '/notifications/preferences') return mockOk({ endpointFailureEnabled: true, endpointRecoveredEnabled: true, pushDeadEnabled: true, failureFrequency: 'IMMEDIATE' })
    if (url.match(/\/endpoints\/\d+\/health$/)) {
      const id = Number(url.split('/')[2])
      return mockOk({ endpointId: id, circuitBreakerOpen: id === 2, failureRate: id === 2 ? 75.0 : 0, slowCallRate: 0, bufferedCalls: 10, failedCalls: id === 2 ? 8 : 0, successfulCalls: id === 2 ? 2 : 10, availablePermits: 10 })
    }
    return mockOk(null)
  },

  post(url: string, data?: any): any {
    if (url === '/webhook-endpoints') {
      const secret = 'whsec_' + Math.random().toString(36).substring(2, 18)
      return mockOk({ endpointId: ++nextId, secret })
    }
    if (url.match(/\/webhook-logs\/\d+\/retry$/)) return mockOk(null)
    if (url === '/subscriptions') return mockOk({ ...data, id: ++nextId })
    if (url === '/subscriptions/batch-status') return mockOk(null)
    if (url.match(/\/notifications\/\d+\/read$/)) return mockOk(null)
    if (url === '/notifications/read-all') return mockOk(null)
    if (url.match(/\/webhook-endpoints\/\d+\/test$/)) return mockOk({ success: true, latency: 120 })
    return mockOk(null)
  },

  put(url: string, data?: any): any {
    if (url.match(/\/webhook-endpoints\/\d+$/)) return mockOk(data)
    if (url.match(/\/subscriptions\/\d+$/)) return mockOk(data)
    if (url === '/notifications/preferences') return mockOk(data)
    return mockOk(null)
  },

  patch(url: string, data?: any): any {
    if (url.match(/\/subscriptions\/\d+\/status$/)) return mockOk(null)
    return mockOk(null)
  },

  delete(url: string): any {
    if (url.match(/\/webhook-endpoints\/\d+$/)) return mockOk(null)
    if (url.match(/\/subscriptions\/\d+$/)) return mockOk(null)
    return mockOk(null)
  }
}

function mockOk(data: any) {
  return Promise.resolve({ data: { code: 200, message: 'ok', data } })
}

function mockPage(items: any[], page: number, size: number) {
  const start = page * size
  const content = items.slice(start, start + size)
  return Promise.resolve({
    data: {
      code: 200,
      message: 'ok',
      data: { content, totalElements: items.length, totalPages: Math.ceil(items.length / size), page, size }
    }
  })
}

// Install mock interceptor on the axios instance
const originalRequest = http.request.bind(http)

http.interceptors.request.clear()
http.interceptors.response.clear()

http.interceptors.request.use((config: any) => {
  const url: string = config.url || ''
  const method: string = (config.method || 'get').toLowerCase()

  console.log(`[Mock API] ${method.toUpperCase()} ${url}`, config.params || config.data || '')

  const handler = (mockAdapter as any)[method]
  if (handler) {
    const result = handler(url, config.params || config.data)
    // Return a cancelled request with the mock data in the adapter
    config.adapter = () => result.then(r => ({ data: r.data, status: 200, statusText: 'OK', headers: {}, config }))
  }
  return config
})

console.log('[Mock API] Mock interceptor installed')

export default mockAdapter

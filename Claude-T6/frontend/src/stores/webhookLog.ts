import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { DeliveryLog, PushStats, LogQueryParams } from '@/types/webhook-log'
import webhookLogService from '@/services/webhookLogService'

export const useWebhookLogStore = defineStore('webhookLog', () => {
  const logs = ref<DeliveryLog[]>([])
  const logDetail = ref<DeliveryLog | null>(null)
  const stats = ref<PushStats | null>(null)
  const loading = ref(false)
  const filters = ref<LogQueryParams>({})
  const pagination = ref({ page: 0, totalPages: 0, totalElements: 0, size: 20 })

  async function fetchLogs(params?: LogQueryParams) {
    loading.value = true
    try {
      const p = { ...filters.value, ...params }
      const res = await webhookLogService.list(p)
      logs.value = res.data?.content || []
      if (res.data) {
        pagination.value.page = res.data.page
        pagination.value.totalPages = res.data.totalPages
        pagination.value.totalElements = res.data.totalElements
      }
    } finally { loading.value = false }
  }

  async function fetchDetail(id: number) {
    const res = await webhookLogService.getDetail(id)
    logDetail.value = res.data
    return res.data
  }

  async function retryLog(id: number) {
    await webhookLogService.retry(id)
    await fetchLogs()
  }

  async function fetchStats(endpointId: number) {
    const res = await webhookLogService.getStats(endpointId)
    stats.value = res.data
  }

  return { logs, logDetail, stats, loading, filters, pagination, fetchLogs, fetchDetail, retryLog, fetchStats }
})

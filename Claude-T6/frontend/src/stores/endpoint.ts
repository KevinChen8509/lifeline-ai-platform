import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { WebhookEndpoint, SecretResponse, QuotaInfo } from '@/types'
import endpointService from '@/services/endpointService'

export const useEndpointStore = defineStore('endpoint', () => {
  const endpoints = ref<WebhookEndpoint[]>([])
  const currentEndpoint = ref<WebhookEndpoint | null>(null)
  const secretOnceVisible = ref<string | null>(null)
  const quota = ref<QuotaInfo | null>(null)
  const loading = ref(false)

  async function fetchEndpoints() {
    loading.value = true
    try {
      const res = await endpointService.list()
      endpoints.value = res.data || []
    } finally { loading.value = false }
  }

  async function fetchQuota() {
    const res = await endpointService.getQuota()
    quota.value = res.data
  }

  async function createEndpoint(data: { name: string; url: string; customHeaders?: Record<string, string> }) {
    const res = await endpointService.create(data)
    secretOnceVisible.value = res.data.secret
    await fetchEndpoints()
    await fetchQuota()
    return res.data
  }

  async function updateEndpoint(id: number, data: { name?: string; url?: string; customHeaders?: Record<string, string> }) {
    const res = await endpointService.update(id, data)
    await fetchEndpoints()
    return res.data
  }

  async function deleteEndpoint(id: number) {
    await endpointService.delete(id)
    await fetchEndpoints()
    await fetchQuota()
  }

  function clearSecret() { secretOnceVisible.value = null }

  return { endpoints, currentEndpoint, secretOnceVisible, quota, loading, fetchEndpoints, fetchQuota, createEndpoint, updateEndpoint, deleteEndpoint, clearSecret }
})

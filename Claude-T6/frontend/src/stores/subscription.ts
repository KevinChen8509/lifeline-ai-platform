import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Subscription, Rule } from '@/types/subscription'
import type { PageResponse } from '@/types'
import subscriptionService from '@/services/subscriptionService'

export const useSubscriptionStore = defineStore('subscription', () => {
  const subscriptions = ref<Subscription[]>([])
  const subscriptionDetail = ref<Subscription | null>(null)
  const selectedIds = ref<number[]>([])
  const pagination = ref({ page: 0, totalPages: 0, totalElements: 0, size: 20 })
  const loading = ref(false)
  const filters = ref<{ status?: number; type?: string }>({})

  async function fetchSubscriptions(params?: { page?: number; size?: number }) {
    loading.value = true
    try {
      const res = await subscriptionService.list({ ...params, ...filters.value })
      subscriptions.value = res.data?.content || []
      if (res.data) {
        pagination.value.page = res.data.page
        pagination.value.totalPages = res.data.totalPages
        pagination.value.totalElements = res.data.totalElements
      }
    } finally { loading.value = false }
  }

  async function fetchDetail(id: number) {
    const res = await subscriptionService.getDetail(id)
    subscriptionDetail.value = res.data
    return res.data
  }

  async function createSubscription(data: any) {
    const res = await subscriptionService.create(data)
    await fetchSubscriptions()
    return res.data
  }

  async function updateSubscription(id: number, data: any) {
    const res = await subscriptionService.update(id, data)
    await fetchSubscriptions()
    return res.data
  }

  async function toggleStatus(id: number, status: number) {
    await subscriptionService.toggleStatus(id, status)
    await fetchSubscriptions()
  }

  async function deleteSubscription(id: number) {
    await subscriptionService.delete(id)
    await fetchSubscriptions()
  }

  async function batchToggleStatus(status: number) {
    if (selectedIds.value.length === 0) return
    await subscriptionService.batchToggleStatus(selectedIds.value, status)
    selectedIds.value = []
    await fetchSubscriptions()
  }

  async function checkConflicts(deviceId: number, dataPointIds?: number[], excludeId?: number): Promise<Rule[]> {
    const res = await subscriptionService.checkConflicts(deviceId, dataPointIds, excludeId)
    return res.data || []
  }

  function toggleSelect(id: number) {
    const idx = selectedIds.value.indexOf(id)
    if (idx >= 0) selectedIds.value.splice(idx, 1)
    else selectedIds.value.push(id)
  }

  function clearSelection() { selectedIds.value = [] }

  return {
    subscriptions, subscriptionDetail, selectedIds, pagination, loading, filters,
    fetchSubscriptions, fetchDetail, createSubscription, updateSubscription,
    toggleStatus, deleteSubscription, batchToggleStatus, checkConflicts,
    toggleSelect, clearSelection
  }
})

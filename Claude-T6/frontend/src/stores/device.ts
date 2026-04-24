import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Device, TreeNode } from '@/types/device'
import type { PageResponse } from '@/types'
import deviceService from '@/services/deviceService'

export const useDeviceStore = defineStore('device', () => {
  const deviceList = ref<Device[]>([])
  const deviceTree = ref<TreeNode[]>([])
  const selectedDeviceId = ref<number | null>(null)
  const pagination = ref({ page: 0, totalPages: 0, totalElements: 0, size: 20 })
  const loading = ref(false)

  async function fetchDevices(params?: { productId?: number; page?: number; size?: number }) {
    loading.value = true
    try {
      const res = await deviceService.list(params)
      deviceList.value = res.data?.content || []
      if (res.data) {
        pagination.value.page = res.data.page
        pagination.value.totalPages = res.data.totalPages
        pagination.value.totalElements = res.data.totalElements
      }
    } finally { loading.value = false }
  }

  async function fetchGroupTree() {
    const res = await deviceService.getGroupTree()
    deviceTree.value = res.data || []
  }

  function selectDevice(id: number) { selectedDeviceId.value = id }

  return { deviceList, deviceTree, selectedDeviceId, pagination, loading, fetchDevices, fetchGroupTree, selectDevice }
})

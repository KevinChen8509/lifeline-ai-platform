import { ref, computed } from 'vue'

export function useOffsetPagination(opts: { pageSize?: number } = {}) {
  const page = ref(0)
  const size = ref(opts.pageSize || 20)
  const totalElements = ref(0)
  const totalPages = ref(0)

  const hasNext = computed(() => page.value < totalPages.value - 1)
  const hasPrev = computed(() => page.value > 0)

  function goTo(p: number) { page.value = Math.max(0, Math.min(p, totalPages.value - 1)) }
  function next() { if (hasNext.value) page.value++ }
  function prev() { if (hasPrev.value) page.value-- }
  function reset() { page.value = 0 }

  function syncFromServer(data: { page: number; totalPages: number; totalElements: number }) {
    page.value = data.page
    totalPages.value = data.totalPages
    totalElements.value = data.totalElements
  }

  return { page, size, totalElements, totalPages, hasNext, hasPrev, goTo, next, prev, reset, syncFromServer }
}

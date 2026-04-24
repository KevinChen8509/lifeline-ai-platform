import { describe, it, expect } from 'vitest'
import { useOffsetPagination } from '../useOffsetPagination'

describe('useOffsetPagination', () => {
  it('starts at page 0', () => {
    const { page } = useOffsetPagination()
    expect(page.value).toBe(0)
  })

  it('syncFromServer updates pagination state', () => {
    const { page, totalPages, totalElements, syncFromServer } = useOffsetPagination()
    syncFromServer({ page: 2, totalPages: 5, totalElements: 100 })
    expect(page.value).toBe(2)
    expect(totalPages.value).toBe(5)
    expect(totalElements.value).toBe(100)
  })

  it('goTo clamps to valid range', () => {
    const { page, goTo, syncFromServer } = useOffsetPagination()
    syncFromServer({ page: 0, totalPages: 3, totalElements: 60 })
    goTo(10)
    expect(page.value).toBe(2) // clamped to totalPages - 1
    goTo(-1)
    expect(page.value).toBe(0) // clamped to 0
  })
})

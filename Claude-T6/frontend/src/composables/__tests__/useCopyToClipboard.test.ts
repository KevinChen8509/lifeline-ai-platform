import { describe, it, expect } from 'vitest'
import { useCopyToClipboard } from '../useCopyToClipboard'

describe('useCopyToClipboard', () => {
  it('initial state: copied is false', () => {
    const { copied } = useCopyToClipboard()
    expect(copied.value).toBe(false)
  })
})

import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EmptyState from '../EmptyState.vue'

describe('EmptyState', () => {
  it('renders description text', () => {
    const wrapper = mount(EmptyState, {
      props: { description: '暂无数据' }
    })
    // el-empty passes description as prop, rendered inside its component
    const html = wrapper.html()
    expect(html).toContain('暂无数据')
  })

  it('renders action button when actionText is provided', () => {
    const wrapper = mount(EmptyState, {
      props: { description: '暂无数据', actionText: '创建' }
    })
    expect(wrapper.html()).toContain('创建')
  })

  it('does not render action button when actionText is not provided', () => {
    const wrapper = mount(EmptyState, {
      props: { description: '暂无数据' }
    })
    // Only el-empty renders, no action button slot
    const buttons = wrapper.findAllComponents({ name: 'ElButton' })
    expect(buttons.length).toBe(0)
  })
})

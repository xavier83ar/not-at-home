import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SiteFooter from '~/components/SiteFooter.vue'

describe('SiteFooter', () => {
  it('shows the name and slogan', () => {
    const wrapper = mount(SiteFooter)
    expect(wrapper.text()).toContain('Not at Home')
    expect(wrapper.text()).toContain('Stories from the trail')
  })
})

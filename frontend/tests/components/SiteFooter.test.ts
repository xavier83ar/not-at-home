import { describe, expect, it } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import SiteFooter from '~/components/SiteFooter.vue'

describe('SiteFooter', () => {
  it('shows the name and slogan', async () => {
    const wrapper = await mountSuspended(SiteFooter)
    expect(wrapper.text()).toContain('Not at Home')
    expect(wrapper.text()).toContain('Stories from the trail')
  })
})

import { mount, flushPromises } from '@vue/test-utils';
import { afterEach, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import HelpView from './HelpView.vue';
import TermsView from './TermsView.vue';
import { redirectLegacyHelp } from '../router';
afterEach(() => vi.unstubAllGlobals());
async function render() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/help', component: HelpView },
      { path: '/terms', component: TermsView },
    ],
  });
  router.beforeEach(redirectLegacyHelp);
  await router.push('/help');
  await router.isReady();
  const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}
it('keeps the product workflow on its own page and links to the independent glossary', async () => {
  const fetch = vi.fn();
  vi.stubGlobal('fetch', fetch);
  const { wrapper, router } = await render();
  try {
    expect(wrapper.get('[data-testid="product-guide"]').isVisible()).toBe(true);
    expect(wrapper.findAll('.guide-steps > li')).toHaveLength(6);
    expect(wrapper.find('.research-help-search').exists()).toBe(false);
    expect(wrapper.find('[role="tablist"]').exists()).toBe(false);
    await wrapper.get('a[href="/terms"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/terms');
    expect(wrapper.find('[data-testid="product-guide"]').exists()).toBe(false);
    expect(wrapper.find('.research-help-search').exists()).toBe(true);
    expect(fetch).not.toHaveBeenCalled();
  } finally {
    wrapper.unmount();
  }
});
it('redirects a legacy glossary query even when navigation starts on the guide page', async () => {
  const { wrapper, router } = await render();
  try {
    await router.push('/help?view=terms');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/terms');
    expect(wrapper.find('.research-help-search').exists()).toBe(true);
  } finally {
    wrapper.unmount();
  }
});

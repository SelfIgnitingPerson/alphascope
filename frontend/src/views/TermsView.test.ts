import { mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import { nextTick } from 'vue';
import TermsView from './TermsView.vue';
import HelpView from './HelpView.vue';
import { redirectLegacyHelp } from '../router';
import { researchTerms } from '../content/research-terms';
const wrappers: VueWrapper[] = [];
afterEach(() => {
  wrappers.splice(0).forEach((item) => {
    if (item.exists()) item.unmount();
  });
  vi.unstubAllGlobals();
});
async function render(path = '/terms') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/help', component: HelpView },
      { path: '/terms', component: TermsView },
    ],
  });
  router.beforeEach(redirectLegacyHelp);
  await router.push(path);
  await router.isReady();
  const wrapper = mount(TermsView, { attachTo: document.body, global: { plugins: [router] } });
  wrappers.push(wrapper);
  return wrapper;
}

it('opens a deep-linked term with the actual simple-average versus Wilder distinction', async () => {
  const wrapper = await render('/terms?term=atr-reference');
  expect((wrapper.get('input').element as HTMLInputElement).value).toContain('前14期ATR');
  expect(wrapper.get('#term-atr-reference').attributes('open')).toBeDefined();
  expect(wrapper.text()).toContain('简单平均');
  expect(wrapper.text()).toContain('Wilder');
});

it('searches and resets terminology locally without requesting private research data', async () => {
  const fetch = vi.fn();
  vi.stubGlobal('fetch', fetch);
  const wrapper = await render();
  await wrapper.get('input').setValue('现金流');
  expect(wrapper.text()).toContain('现金流恶化规则');
  await wrapper.get('select').setValue('价格与量能');
  expect(wrapper.findAll('.research-help-terms details')).toHaveLength(0);
  const reset = wrapper.findAll('button').find((button) => button.text() === '重置筛选');
  await reset!.trigger('click');
  await nextTick();
  expect(wrapper.findAll('.research-help-terms details')).toHaveLength(
    researchTerms.filter((term) => term.category === '风险规则').length,
  );
  expect(fetch).not.toHaveBeenCalled();
});

it('always explains the four rule states and why a high severity rule can be untriggered', async () => {
  const wrapper = await render();
  expect(wrapper.text()).toContain('未触发｜高');
  expect(wrapper.findAll('.rule-status-guide dt')).toHaveLength(4);
  expect(wrapper.text()).toContain('不是发生概率');
  expect(wrapper.findAll('.help-category-nav button')).toHaveLength(6);
  expect(wrapper.find('#term-ma').exists()).toBe(false);
  await wrapper
    .findAll('.help-category-nav button')
    .find((b) => b.text() === '价格与量能')!
    .trigger('click');
  expect(wrapper.find('#term-ma').exists()).toBe(true);
  expect(wrapper.find('#term-cashflow').exists()).toBe(false);
  expect(wrapper.find('.rule-status-guide').exists()).toBe(false);
  await wrapper
    .findAll('.help-category-nav button')
    .find((b) => b.text() === '数据与任务状态')!
    .trigger('click');
  expect(wrapper.findAll('.research-help-terms details')).toHaveLength(0);
  expect(wrapper.findAll('.status-group')).toHaveLength(3);
  expect(
    wrapper.findAll('.status-group').every((group) => group.attributes('open') === undefined),
  ).toBe(true);
});

it('opens the state groups directly from the contextual state link', async () => {
  const wrapper = await render('/terms?section=states');
  expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('数据与任务状态');
  expect(wrapper.findAll('.status-group')).toHaveLength(3);
  expect(
    wrapper.findAll('.status-group').every((group) => group.attributes('open') !== undefined),
  ).toBe(true);
  expect(wrapper.find('[data-testid="product-guide"]').exists()).toBe(false);
});
it('preserves legacy term and state links without retaining the obsolete view parameter', async () => {
  const wrapper = await render('/help?view=terms&term=atr-reference#term-atr-reference');
  expect(wrapper.vm.$route.path).toBe('/terms');
  expect(wrapper.vm.$route.query).toEqual({ term: 'atr-reference' });
  expect(wrapper.vm.$route.hash).toBe('#term-atr-reference');
  expect(wrapper.get('#term-atr-reference').attributes('open')).toBeDefined();
  await wrapper.vm.$router.push('/help?section=states');
  expect(wrapper.vm.$route.path).toBe('/terms');
  expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('数据与任务状态');
});

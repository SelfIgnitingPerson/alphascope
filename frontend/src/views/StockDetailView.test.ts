import { expect, it, vi } from 'vitest';
import { shallowMount, flushPromises } from '@vue/test-utils';
import StockDetailView from './StockDetailView.vue';
import { getBars, getInstrument } from '../api/market';
import type { DailyBar } from '../types/models';
vi.mock('../api/market', () => ({
  getInstrument: vi.fn().mockResolvedValue({ symbol: '300476.SZ', name: '胜宏科技' }),
  getBars: vi
    .fn()
    .mockResolvedValue({ items: [{ trade_date: '2026-11-12', close: '123', source: 'BAOSTOCK' }] }),
  getIndicators: vi.fn().mockRejectedValue(new Error('指标暂不可用')),
  getFinancials: vi.fn().mockRejectedValue(new Error('财报暂不可用')),
  getSignals: vi.fn().mockRejectedValue(new Error('规则暂不可用')),
}));
it('keeps prices and holding research accessible when optional data fails and clears old prices on date changes', async () => {
  const wrapper = shallowMount(StockDetailView, {
    props: { symbol: '300476.SZ', date: '2026-11-12', refreshToken: 0 },
    global: {
      stubs: {
        RouterLink: true,
        AsyncState: false,
        SectionCard: { template: '<section><slot/><slot name="actions"/></section>' },
        SectionTabs: {
          template:
            '<div><slot name="plans"/><slot name="price"/><slot name="rules"/><slot name="financials"/></div>',
        },
      },
    },
  });
  await flushPromises();
  expect(wrapper.text()).toContain('胜宏科技');
  expect(wrapper.text()).toContain('123.00');
  expect(wrapper.text()).toContain('财报暂不可用');
  expect(wrapper.text()).toContain('指标暂不可用');
  expect(wrapper.findComponent({ name: 'HoldingResearchPanel' }).exists()).toBe(true);
  expect(wrapper.findComponent({ name: 'PriceChart' }).props('bars')[0].trade_date).toBe(
    '2026-11-12',
  );
  vi.mocked(getBars).mockRejectedValueOnce(new Error('新日期行情失败'));
  await wrapper.setProps({ date: '2026-11-11' });
  await flushPromises();
  expect(wrapper.text()).not.toContain('123.00');
  expect(wrapper.text()).toContain('新日期行情失败');
  expect(wrapper.findComponent({ name: 'PriceChart' }).exists()).toBe(false);
  expect(wrapper.findComponent({ name: 'HoldingResearchPanel' }).props('date')).toBe('2026-11-11');
  expect(getInstrument).toHaveBeenCalledTimes(1);
  vi.mocked(getBars).mockResolvedValueOnce({
    items: [{ trade_date: '2026-11-10', close: '110' } as DailyBar],
    priceUnit: 'CNY',
    volumeUnit: 'SHARE',
    amountUnit: 'CNY',
  });
  await wrapper.setProps({ refreshToken: 1 });
  await flushPromises();
  expect(wrapper.text()).toContain('最近已保存行情为 2026-11-10');
  wrapper.unmount();
});

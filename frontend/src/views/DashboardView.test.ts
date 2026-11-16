import { it, expect, vi } from 'vitest';
import { shallowMount, flushPromises } from '@vue/test-utils';
import { getOverview } from '../api/market';
import type { MarketOverview } from '../types/models';
import DashboardView from './DashboardView.vue';
vi.mock('../api/market', () => ({
  getOverview: vi.fn().mockRejectedValue(new Error('市场观察暂不可用')),
  getProviders: vi.fn().mockResolvedValue({ latestData: [], channels: [] }),
  getBars: vi.fn(async (symbol: string) => {
    if (symbol === '300476.SZ') throw new Error('此标的暂时失败');
    return { items: [{ trade_date: '2026-10-30', close: '12', factor: '1', source: 'BAOSTOCK' }] };
  }),
}));
vi.mock('../api/portfolio', () => ({
  getWatchlist: vi.fn().mockResolvedValue([
    { symbol: '300476.SZ', name: '异常股票' },
    { symbol: '002837.SZ', name: '正常股票' },
  ]),
}));
vi.mock('../api/reports', () => ({
  getReports: vi.fn().mockResolvedValue({
    items: [
      {
        id: 19,
        revision: 1,
        business_date: '2026-10-30',
        model: 'RULE_FALLBACK',
        status: 'SUCCESS',
      },
    ],
  }),
}));
vi.mock('../api/product', () => ({
  getWorkspaceStatus: vi.fn().mockResolvedValue({ issues: ['近期日历不足'] }),
}));
it('keeps reports and other stocks visible when overview and one quote fail', async () => {
  const wrapper = shallowMount(DashboardView, {
    props: { date: '2026-10-30', refreshToken: 0 },
    global: {
      stubs: {
        SectionCard: { template: '<section><slot name="actions"/><slot/></section>' },
        AsyncState: {
          props: ['error'],
          template: '<div><p v-if="error">{{ error }}</p><slot/></div>',
        },
        RouterLink: { template: '<a><slot/></a>' },
        MarketScorePanel: true,
      },
    },
  });
  await flushPromises();
  expect(wrapper.text()).toContain('市场观察暂不可用');
  expect(wrapper.text()).toContain('正常股票');
  expect(wrapper.text()).toContain('此标的暂时失败');
  expect(wrapper.text()).toContain('2026-10-30 日报');
  expect(wrapper.text()).toContain('近期日历不足');
  wrapper.unmount();
});

it('does not present an older market snapshot as the selected date judgment', async () => {
  vi.mocked(getOverview).mockResolvedValueOnce({
    businessDate: '2026-11-12',
    state: 'UNKNOWN',
    provider: 'MULTI',
    isSynthetic: false,
    dataStatus: 'PARTIAL',
    observation: {
      status: 'AVAILABLE',
      snapshotId: 29,
      fetchedAt: null,
      collection: {},
      analysis: { businessDate: '2026-11-10', trend: { judgment: '趋势偏弱' } },
    },
  } as MarketOverview);
  const wrapper = shallowMount(DashboardView, {
    props: { date: '2026-11-12', refreshToken: 0 },
    global: {
      stubs: {
        SectionCard: { template: '<section><slot/></section>' },
        AsyncState: { template: '<div><slot/></div>' },
        RouterLink: { template: '<a><slot/></a>' },
      },
    },
  });
  await flushPromises();
  expect(wrapper.find('.metric-card strong').text()).toBe('本日观察待补');
  expect(wrapper.text()).toContain('最近 2026-11-10：趋势偏弱');
  expect(wrapper.text()).toContain('不能当作所选日的全市场判断');
  wrapper.unmount();
});

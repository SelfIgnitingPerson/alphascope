import { shallowMount, flushPromises } from '@vue/test-utils';
import { beforeEach, it, expect, vi } from 'vitest';
import ChannelsView from './ChannelsView.vue';
import { getProviders, getBaoStockPolicy } from '../api/market';
import type { ProviderStatus } from '../types/market';
vi.mock('../api/market', () => ({ getProviders: vi.fn(), getBaoStockPolicy: vi.fn() }));
const options = {
  props: { date: '2026-11-13', refreshToken: 0 },
  global: {
    stubs: {
      AsyncState: false,
      SectionCard: false,
      SectionTabs: false,
      StateBadge: true,
      FailureExplanation: false,
      RouterLink: true,
      'el-table': true,
      'el-table-column': true,
    },
  },
};
function providers(): ProviderStatus {
  return {
    mode: 'MULTI',
    dataScope: 'REAL:BAOSTOCK,SINA',
    isSynthetic: false,
    calendarSource: 'NONE',
    channels: [
      {
        channel: 'BAOSTOCK',
        state: 'ACCESS_RESTRICTED',
        lastSuccess: null,
        lastError: 'BAOSTOCK_ACCESS_RESTRICTED_10001011',
        consecutiveFailures: 0,
        retryAt: null,
        capabilities: [],
      },
      {
        channel: 'SINA',
        state: 'NOT_CHECKED',
        lastSuccess: null,
        lastError: '',
        consecutiveFailures: 0,
        retryAt: null,
        capabilities: [
          { capability: 'STOCK_DAILY', supported: true, state: 'NOT_CHECKED' },
          { capability: 'INDEX_DAILY', supported: false, state: 'UNSUPPORTED' },
        ],
      },
    ],
    latestData: [
      {
        symbol: '002837.SZ',
        source: 'SINA',
        trade_date: '2026-11-12',
        fetched_at: '2026-11-12T17:07:29',
      },
    ],
    inactiveChannels: [
      {
        channel: 'TUSHARE',
        state: 'NOT_CONFIGURED',
        reason: '未配置TUSHARE_TOKEN，启动时跳过',
        action: '需要时在本机配置并重启，分别核验权限',
      },
    ],
  };
}
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getProviders).mockResolvedValue(providers());
  vi.mocked(getBaoStockPolicy).mockResolvedValue({
    date: '2026-11-13',
    limit: 40000,
    used: 2,
    remaining: 39998,
    accessProtected: true,
    accessCode: '10001011',
    accessError: {
      provider: 'BAOSTOCK',
      code: '10001011',
      message: null,
      meaning: '黑名单用户',
      messageState: 'NOT_RECORDED',
    },
    accessObservedAt: '2026-11-01T12:44:54.821Z',
    error: null,
  });
});
it('shows the original protection time and concrete recovery steps without probing', async () => {
  const wrapper = shallowMount(ChannelsView, options);
  await flushPromises();
  expect(wrapper.text()).toContain('39998');
  expect(wrapper.get('.channel-evidence').text()).toContain('10001011 · 黑名单用户');
  expect(wrapper.text()).toContain('这不是会员权限不足');
  expect(wrapper.text()).toContain('不证明当前上游仍受限');
  expect(wrapper.text()).toContain('2026-11-01 20:44:54');
  expect(wrapper.text()).toContain('累计次数×6小时');
  expect(wrapper.text()).toContain('确认依据');
  expect(wrapper.text()).toContain('Tushare · 配置未完成');
  expect(wrapper.find('a[href="https://www.baostock.com/blacklist"]').exists()).toBe(true);
  expect(getProviders).toHaveBeenCalledTimes(1);
  expect(getBaoStockPolicy).toHaveBeenCalledTimes(1);
  wrapper.unmount();
});
it('isolates budget diagnostics failure from the saved data section', async () => {
  vi.mocked(getBaoStockPolicy).mockRejectedValue(new Error('暂不可用'));
  const wrapper = shallowMount(ChannelsView, options);
  await flushPromises();
  expect(wrapper.text()).toContain('预算状态读取失败');
  await wrapper
    .findAll('[role="tab"]')
    .find((item) => item.text() === '已入库数据')!
    .trigger('click');
  expect(wrapper.text()).toContain('已入库数据来源');
  expect(wrapper.text()).not.toContain('剩余 0');
  wrapper.unmount();
});
it('separates saved successful data from current-process observations and unsupported capabilities', async () => {
  const wrapper = shallowMount(ChannelsView, options);
  await flushPromises();
  await wrapper
    .findAll('.channel-selector button')
    .find((item) => item.text().includes('新浪财经'))!
    .trigger('click');
  expect(wrapper.find('.channel-detail').text()).toContain('1个标的 · 最新2026-11-12');
  expect(wrapper.find('.channel-detail').text()).toContain('不表示受限，也不表示已经连通');
  expect(wrapper.find('.channel-detail').text()).toContain('指数接口缺少可靠成交额');
  expect(wrapper.find('.channel-detail').text()).toContain('正常跳过：指数日线');
  expect(wrapper.find('.channel-detail').text()).not.toContain('10001011');
  expect(getProviders).toHaveBeenCalledTimes(1);
  wrapper.unmount();
});

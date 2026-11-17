import { expect, it, vi } from 'vitest';
import { shallowMount, flushPromises } from '@vue/test-utils';
import { ref } from 'vue';
import MarketHistoryPanel from './MarketHistoryPanel.vue';
import { getOverview, getResearchScore, getUniverseCoverage } from '../../api/market';
import { fillMarketHistory } from '../../api/jobs';
vi.mock('../../api/market', () => ({
  getOverview: vi.fn(),
  getResearchScore: vi.fn(),
  getUniverseCoverage: vi.fn(),
}));
vi.mock('../../api/jobs', () => ({ fillMarketHistory: vi.fn() }));
vi.mock('../../composables/useAuth', () => ({ useAuth: () => ({ user: ref({ role: 'ADMIN' }) }) }));
it('shows the real coverage gap and only queues history after an explicit click', async () => {
  vi.mocked(getOverview).mockResolvedValue({
    businessDate: '2026-11-16',
    state: 'UNKNOWN',
    provider: 'BAOSTOCK',
    isSynthetic: false,
    dataStatus: 'PARTIAL',
    observation: {
      status: 'PARTIAL',
      snapshotId: 1,
      fetchedAt: '2026-11-17T10:00:00',
      analysis: {
        version: 'TEST',
        businessDate: '2026-11-16',
        scope: 'SH_SZ_A',
        status: 'PARTIAL',
        parameters: { minimumCoverage: 0.98 },
        limitations: [],
        trend: {
          status: 'INSUFFICIENT_DATA',
          judgment: '待核对',
          close: null,
          ma20: null,
          ma60: null,
          ma20Slope: null,
          dayReturn: null,
          explanation: '测试未提供指数',
        },
        turnover: {
          status: 'INSUFFICIENT_DATA',
          judgment: '待核对',
          amount: null,
          average5: null,
          average20: null,
          ratio5: null,
          ratio20: null,
          completeDays: 0,
          explanation: '测试未提供成交额',
        },
        breadth: {
          status: 'INSUFFICIENT_DATA',
          judgment: '待核对',
          trading: 100,
          suspended: 0,
          valid20: 7,
          above20: 2,
          coverage20: 0.07,
          ratio20: null,
          valid60: 7,
          above60: 1,
          coverage60: 0.07,
          ratio60: null,
          advancingRatio: null,
          explanation: '已发布7只有效',
        },
      },
      collection: {
        business_date: '2026-11-16',
        evidence_json: JSON.stringify({ completedFactors: 40, totalFactors: 100 }),
      },
    },
  });
  vi.mocked(getResearchScore).mockResolvedValue({
    modelVersion: 'TEST',
    scope: 'SH_SZ_A',
    businessDate: '2026-11-16',
    score: null,
    state: 'UNKNOWN',
    validationStatus: 'EXPERIMENTAL',
    parameters: {},
    limitations: [],
    dimensions: {
      breadth: {
        score: null,
        status: 'INSUFFICIENT_DATA',
        sourceScope: 'SH_SZ_A',
        formula: '覆盖门槛',
        evidence: { observed: { trading: 100, valid60: 7 } },
      },
    },
  } as Awaited<ReturnType<typeof getResearchScore>>);
  vi.mocked(getUniverseCoverage).mockResolvedValue({ missing: ['未提供北交所清单'] } as Awaited<
    ReturnType<typeof getUniverseCoverage>
  >);
  vi.mocked(fillMarketHistory).mockResolvedValue({ id: 12 } as Awaited<
    ReturnType<typeof fillMarketHistory>
  >);
  const w = shallowMount(MarketHistoryPanel, {
    props: { date: '2026-11-16', refreshToken: 0 },
    global: {
      stubs: {
        SectionCard: { template: '<section><slot/></section>' },
        AsyncState: false,
        ElButton: { template: '<button @click="$emit(\'click\')"><slot/></button>' },
        RouterLink: { template: '<a><slot/></a>' },
      },
    },
  });
  await flushPromises();
  expect(w.text()).toContain('91');
  expect(w.text()).toContain('98%');
  expect(w.text()).toContain('40 / 100');
  expect(w.text()).toContain('不能当作有效广度');
  expect(w.text()).toContain('未提供北交所清单');
  expect(fillMarketHistory).not.toHaveBeenCalled();
  await w.get('button').trigger('click');
  await flushPromises();
  expect(fillMarketHistory).not.toHaveBeenCalled();
  await w.findAll('button').at(-1)!.trigger('click');
  await flushPromises();
  expect(fillMarketHistory).toHaveBeenCalledWith('2026-11-16');
  expect(w.text()).toContain('#12');
  await w.setProps({ date: '2026-11-17' });
  await flushPromises();
  expect(w.text()).not.toContain('40 / 100');
  w.unmount();
});

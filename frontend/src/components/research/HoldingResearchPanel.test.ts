import { mount, flushPromises } from '@vue/test-utils';
import { beforeEach, expect, it, vi } from 'vitest';
import HoldingResearchPanel from './HoldingResearchPanel.vue';
import { getHoldingResearch } from '../../api/market';
import type { HoldingResearch } from '../../types/holding-research';
vi.mock('../../api/market', () => ({ getHoldingResearch: vi.fn() }));
beforeEach(() => vi.clearAllMocks());
function fixture(): HoldingResearch {
  return {
    version: 'HOLDING_RESEARCH_V1',
    symbol: '300476.SZ',
    name: 'fixture',
    asOf: '2026-11-12',
    quoteDate: '2026-11-12',
    source: 'SINA',
    close: 120,
    notes: [],
    priceVolume: {
      version: 'PRICE_VOLUME_2M_V3',
      symbol: '300476.SZ',
      name: 'fixture',
      status: 'INSUFFICIENT_DATA',
      windowStart: '2026-09-12',
      asOf: '2026-11-12',
      observedDays: 0,
      expectedDays: 0,
      actualStart: null,
      source: null,
      close: null,
      return5: null,
      volumeRatio: null,
      volumeRatio5: null,
      atr14: null,
      ma20: null,
      support: null,
      stopReference: null,
      stopStatus: 'UNKNOWN',
      resistances: [],
      decision: 'WAIT',
      conclusion: '等待证据',
      evidence: [],
      missing: [],
      methodNotes: [],
    },
    horizons: [
      {
        code: 'SHORT',
        title: '短期交易',
        period: '1–20个交易日',
        status: 'WAIT',
        conclusion: '等待回踩',
        missing: [],
        evidence: [],
        actions: [
          {
            code: 'OPEN',
            title: '回踩建仓',
            status: 'WAIT',
            conclusion: '核对量能',
            observationZone: {
              lower: 110,
              upper: 112,
              basis: 'SUPPORT',
              dates: [],
              volumeShare: null,
            },
            referencePrice: 112,
            referenceLabel: '原上沿',
            stopReference: 105,
            conditionLogic: 'ALL',
            conditions: [{ label: '量能', observed: '未知', required: '量比≤1', met: null }],
            invalidation: [],
            assumptions: [],
          },
        ],
      },
      {
        code: 'MEDIUM',
        title: '中期趋势持有',
        period: '1–6个月',
        status: 'WAIT',
        conclusion: '等待趋势',
        missing: [],
        evidence: [],
        actions: [],
      },
      {
        code: 'LONG',
        title: '长期经营持有',
        period: '6–24个月',
        status: 'WAIT',
        conclusion: '等待估值核验',
        missing: ['估值合理区间尚未接入'],
        evidence: [],
        actions: [],
      },
    ],
  } as HoldingResearch;
}
it('switches holding periods without new requests and keeps unverified long-term prices empty', async () => {
  vi.mocked(getHoldingResearch).mockResolvedValue(fixture());
  const wrapper = mount(HoldingResearchPanel, {
    attachTo: document.body,
    props: { symbol: '300476.SZ', date: '2026-11-12', refreshToken: 0 },
    global: {
      stubs: {
        RouterLink: { template: '<a><slot/></a>' },
        StateBadge: true,
        TermHelp: true,
        ElButton: true,
        ElSkeleton: true,
      },
    },
  });
  await flushPromises();
  expect(wrapper.get('.reference-price').text()).toContain('120.00');
  expect(wrapper.text()).toContain('110.00–112.00');
  expect(wrapper.text()).toContain('待核验 · 量能');
  await wrapper.findAll('.holding-horizon-selector button')[2].trigger('click');
  expect(wrapper.get('[data-testid="holding-horizon"]').text()).toContain('估值合理区间尚未接入');
  expect(wrapper.find('.holding-action__prices').exists()).toBe(false);
  expect(getHoldingResearch).toHaveBeenCalledTimes(1);
  wrapper.unmount();
});

it('explains the selected period with the real date gap, missing items and next step', async () => {
  const data = fixture();
  data.name = '英维克';
  data.asOf = '2026-11-13';
  data.horizons[0] = {
    ...data.horizons[0]!,
    status: 'UNAVAILABLE',
    missing: ['缺少2026-11-13有效收盘日线'],
    actions: [],
  };
  data.horizons[1] = {
    ...data.horizons[1]!,
    status: 'UNAVAILABLE',
    missing: ['中期还需连续日历'],
    actions: [],
  };
  vi.mocked(getHoldingResearch).mockResolvedValue(data);
  const wrapper = mount(HoldingResearchPanel, {
    attachTo: document.body,
    props: { symbol: data.symbol, date: data.asOf, refreshToken: 0 },
    global: {
      stubs: {
        RouterLink: { props: ['to'], template: '<a :href="to"><slot/></a>' },
        TermHelp: true,
        ElButton: true,
        ElSkeleton: true,
      },
    },
  });
  try {
    await flushPromises();
    await wrapper
      .get('[data-testid="holding-horizon"] > header .research-help-trigger')
      .trigger('click');
    let panel = document.querySelector('[data-testid="research-help-popover"]');
    expect(panel?.textContent).toContain('英维克 · 短期交易');
    expect(panel?.textContent).toContain('研究日2026-11-13缺少对应有效行情');
    expect(panel?.textContent).toContain('最近已保存至2026-11-12');
    expect(panel?.textContent).toContain('缺少2026-11-13有效收盘日线');
    expect(panel?.textContent).toContain('不会自动替换研究日期');
    expect(panel?.querySelector('a')?.getAttribute('href')).toBe('/jobs');
    expect(panel?.textContent).not.toContain('当前未取得可用于此项工作的资料或能力');
    await wrapper.findAll('.holding-horizon-selector button')[1]!.trigger('click');
    panel = document.querySelector('[data-testid="research-help-popover"]');
    expect(panel?.textContent).toContain('中期趋势持有');
    expect(panel?.textContent).toContain('中期还需连续日历');
    expect(panel?.textContent).not.toContain('缺少2026-11-13有效收盘日线');
    expect(getHoldingResearch).toHaveBeenCalledTimes(1);
  } finally {
    wrapper.unmount();
  }
});

import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import PriceVolumeCard from './PriceVolumeCard.vue';
import PriceReferenceSummary from './PriceReferenceSummary.vue';
import type { PriceVolumeStudy } from '../../types/price-volume';

function fixture(overrides: Partial<PriceVolumeStudy> = {}): PriceVolumeStudy {
  return {
    version: 'PRICE_VOLUME_2M_V2',
    volumeBasis: 'TURNOVER_AMOUNT',
    symbol: 'TEST.SZ',
    name: '测试',
    status: 'AVAILABLE',
    windowStart: '2026-09-10',
    asOf: '2026-11-10',
    observedDays: 40,
    expectedDays: 40,
    actualStart: '2026-09-10',
    source: 'SINA',
    close: 90,
    return5: -0.1,
    volumeRatio: 1.5,
    volumeRatio5: 1.3,
    atr14: 4,
    ma20: 100,
    support: {
      lower: 97,
      upper: 99,
      basis: 'PRIOR_20_LOW',
      dates: ['2026-11-05'],
      volumeShare: null,
    },
    stopReference: 95,
    stopStatus: 'BREACHED',
    resistances: [
      {
        lower: 110,
        upper: 112,
        basis: 'CONFIRMED_SWING_HIGH',
        dates: ['2026-10-20'],
        volumeShare: null,
      },
    ],
    decision: 'REDUCE_REVIEW',
    conclusion: '已触及或跌破原止损参考位，优先评估减仓控制风险。',
    evidence: ['前20期最低价与放量下跌'],
    missing: [],
    methodNotes: ['仅用于研究，不保证成交。'],
    ...overrides,
  };
}

const render = (study: PriceVolumeStudy) =>
  mount(PriceVolumeCard, {
    props: { study },
    global: { stubs: { StateBadge: true } },
  });

describe('近两月量价参考', () => {
  it('shows a breached original stop, pressure provenance and conditional reduction', () => {
    const wrapper = render(fixture());
    expect(wrapper.text()).toContain('已触及或跌破');
    expect(wrapper.text()).toContain('原支撑已失守');
    expect(wrapper.text()).toContain('可考虑减仓');
    expect(wrapper.text()).toContain('2026-10-20');
    expect(wrapper.text()).toContain('已确认波峰');
    expect(wrapper.text()).toContain('不提供减仓数量');
    expect(wrapper.text()).toContain('成交额');
    expect(wrapper.text()).toContain('参考位相对本期收盘');
    expect(wrapper.text()).toContain('盘中穿越需另行核查');
    expect(
      wrapper
        .findAll('.reference-price')
        .map((n) => n.text())
        .join(' '),
    ).toContain('95.00');
  });

  it('keeps missing data explicit without presenting actionable levels', () => {
    const wrapper = render(
      fixture({
        status: 'INSUFFICIENT_DATA',
        decision: 'UNAVAILABLE',
        stopReference: null,
        resistances: [],
        evidence: [],
        missing: ['缺少3个交易日'],
        conclusion: '证据不足，暂不提供压力、止损或减仓判断。',
      }),
    );
    expect(wrapper.text()).toContain('缺少3个交易日');
    expect(wrapper.text()).toContain('暂不判断');
    expect(wrapper.find('.price-volume-metrics').exists()).toBe(false);
    expect(wrapper.find('.price-volume-levels').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('已达到或突破');
  });

  it('keeps the old frozen proxy visibly distinct from turnover amount', () => {
    const wrapper = render(fixture({ version: 'PRICE_VOLUME_2M_V1', volumeBasis: null }));
    expect(wrapper.text()).toContain('旧代理量');
    expect(wrapper.text()).not.toContain('成交额比');
  });

  it('does not invent a pressure target after reaching a new two month high', () => {
    const wrapper = render(
      fixture({
        close: 130,
        resistances: [],
        decision: 'OBSERVE',
        stopStatus: 'ABOVE_REFERENCE',
        conclusion: '继续观察；未触发不等于可以买入。',
      }),
    );
    expect(wrapper.text()).toContain('上方缺少本窗口压力样本');
    expect(wrapper.text()).toContain('继续观察');
  });
  it('summarizes only verified frozen levels and excludes stale or demo prices', () => {
    const wrapper = mount(PriceReferenceSummary, {
      props: { studies: [fixture(), fixture({ symbol: 'OLD.SZ', name: '陈旧', status: 'STALE' })] },
    });
    expect(wrapper.text()).toContain('关键价位速览');
    expect(wrapper.text()).not.toContain('陈旧');
    const references = wrapper.findAll('.reference-price').map((n) => n.text());
    expect(references).toHaveLength(2);
    expect(references.join(' ')).toContain('95.00');
    const missing = mount(PriceReferenceSummary, {
      props: { studies: [fixture({ status: 'DEMO' })] },
    });
    expect(missing.text()).toBe('');
  });
});

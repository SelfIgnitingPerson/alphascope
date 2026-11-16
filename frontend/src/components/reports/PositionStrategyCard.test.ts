import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import type { PositionStrategy } from '../../types/position-strategy';
import PositionStrategyCard from './PositionStrategyCard.vue';

function fixture(overrides: Partial<PositionStrategy> = {}): PositionStrategy {
  return {
    version: 'POSITION_RESEARCH_V1',
    code: 'OPEN_BREAKOUT',
    title: '放量突破建仓',
    status: 'WAIT',
    conditionLogic: 'ALL',
    conclusion: '条件尚未全部满足，不追价。',
    observationZone: {
      lower: 100,
      upper: 103,
      basis: 'CONFIRMED_SWING_HIGH',
      dates: ['2026-11-05'],
      volumeShare: null,
    },
    referenceZone: {
      lower: 98,
      upper: 100,
      basis: 'CONFIRMED_SWING_HIGH',
      dates: ['2026-11-05'],
      volumeShare: null,
    },
    stopReference: 97,
    referenceRisk: 0.05825,
    maximumReferenceRisk: 0.08,
    conditions: [
      { label: '首次突破', observed: '收盘99元', required: '收盘>100元', met: false },
      { label: '成交额确认', observed: '量比1.4', required: '≥1.2', met: true },
    ],
    invalidation: ['收盘跌回原参考区下沿后重评。'],
    assumptions: ['未核实个人是否持仓，不提供仓位比例。'],
    ...overrides,
  };
}

const render = (strategies: PositionStrategy[]) =>
  mount(PositionStrategyCard, { props: { strategies } });

describe('条件式仓位研究', () => {
  it('shows waiting conditions, reference prices and invalidation together', () => {
    const wrapper = render([fixture()]);
    expect(wrapper.text()).toContain('放量突破建仓');
    expect(wrapper.text()).toContain('等待条件');
    expect(wrapper.text()).toContain('首次突破');
    expect(wrapper.text()).toContain('实测：收盘99元');
    expect(wrapper.text()).toContain('收盘跌回原参考区下沿后重评');
    expect(
      wrapper
        .findAll('.reference-price')
        .map((n) => n.text())
        .join(' '),
    ).toContain('97.00');
  });

  it('does not demand every risk alternative after one reduction group matched', () => {
    const wrapper = render([
      fixture({
        code: 'REDUCE',
        title: '减仓与风险退出',
        status: 'CONDITIONAL_MATCH',
        conditionLogic: 'ANY',
      }),
    ]);
    expect(wrapper.text()).toContain('任一满足即进入减仓复核');
    expect(wrapper.text()).toContain('行情条件满足 · 待人工复核');
    expect(wrapper.find('.position-plan__pending').exists()).toBe(false);
    expect(wrapper.text()).toContain('未核实个人是否持仓');
  });

  it('keeps unknown conditions distinct and never invents an entry price', () => {
    const wrapper = render([
      fixture({
        status: 'UNAVAILABLE',
        conditionLogic: 'ANY',
        observationZone: null,
        referenceZone: null,
        stopReference: null,
        referenceRisk: null,
        conditions: [{ label: '日历', observed: '资料缺失', required: '完整', met: null }],
      }),
    ]);
    expect(wrapper.text()).toContain('待核验 · 日历');
    expect(wrapper.text()).toContain('未知不代表未触发');
    expect(wrapper.findAll('.reference-price')).toHaveLength(0);
    expect(wrapper.text()).toContain('资料不足');
  });
});

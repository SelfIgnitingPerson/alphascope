import { expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import StrategyEvidenceSummary from './StrategyEvidenceSummary.vue';
import type { StrategySimulation } from '../../types/validation';
function simulation(trades = 3, benchmark = 0.02): StrategySimulation {
  return {
    version: 'TEST',
    parameters: {},
    limitations: [],
    periods: [
      {
        label: 'HOLDOUT',
        start: '2026-08-01',
        end: '2026-11-16',
        status: 'ASSUMPTION_BASED_REPLAY',
        completedTrades: trades,
        skippedOrders: 1,
        netReturn: 0.01,
        maximumDrawdown: 0.05,
        benchmarkReturn: benchmark,
        fees: 100,
        openPosition: false,
        reason: '',
      },
    ],
  };
}
it('distinguishes positive returns from a benchmark advantage and makes result date and reproducibility limits visible', () => {
  const w = mount(StrategyEvidenceSummary, {
    props: {
      simulation: simulation(),
      frozenEnd: '2026-11-16',
      date: '2026-11-17',
      reproducible: true,
    },
  });
  expect(w.text()).toContain('留出回顾未跑赢基准');
  expect(w.text()).toContain('未自动更新至所选日');
  expect(w.text()).toContain('可复算不等于策略有效');
  expect(w.text()).toContain('-1.00%');
  w.unmount();
});
it('never treats zero trades or missing old evidence as proof of effectiveness', async () => {
  const w = mount(StrategyEvidenceSummary, {
    props: {
      simulation: simulation(0),
      frozenEnd: '2026-11-16',
      date: '2026-11-16',
      reproducible: false,
    },
  });
  expect(w.text()).toContain('留出证据不足');
  expect(w.findAll('dd').every((d) => d.text() === '—')).toBe(true);
  await w.setProps({ simulation: null });
  expect(w.text()).toContain('没有可用的较晚留出结果');
  w.unmount();
});
it('describes a positive holdout difference without claiming the strategy is validated', () => {
  const w = mount(StrategyEvidenceSummary, {
    props: {
      simulation: simulation(3, 0),
      frozenEnd: '2026-11-16',
      date: '2026-11-16',
      reproducible: true,
    },
  });
  expect(w.text()).toContain('仍待后续验证');
  expect(w.text()).toContain('卖出次数不等于独立交易样本数');
  w.unmount();
});

import { expect, it } from 'vitest';
import { shallowMount } from '@vue/test-utils';
import StrategySimulationPanel from './StrategySimulationPanel.vue';
it('keeps old results absent and clearly labels unexecutable corporate action periods', async () => {
  const w = shallowMount(StrategySimulationPanel);
  expect(w.text()).toContain('此旧验证未保存组合模拟');
  await w.setProps({
    simulation: {
      version: 'TEST',
      parameters: {},
      limitations: ['测试限制'],
      periods: [
        {
          label: 'HOLDOUT',
          start: '2026-01-01',
          end: '2026-02-01',
          status: 'INCOMPLETE',
          completedTrades: 1,
          skippedOrders: 0,
          netReturn: null,
          maximumDrawdown: null,
          benchmarkReturn: null,
          fees: 5,
          openPosition: true,
          reason: '缺少公司行动账务',
        },
      ],
    },
  });
  expect(w.text()).toContain('证据不足，暂不报告收益');
  expect(w.text()).toContain('缺少公司行动账务');
  expect(w.text()).toContain('仍有持仓');
  w.unmount();
});

import { expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import FinancialResearchPanel from './FinancialResearchPanel.vue';
import type { Financial } from '../../types/fundamental';
function report(year: number, type = 'Q3_CUMULATIVE', end = '09-30'): Financial {
  return {
    period_end: `${year}-${end}`,
    report_type: type,
    published_at: `${year}-10-25T18:00:00`,
    source: 'TEST',
    revenue: year === 2026 ? 120e8 : 100e8,
    net_profit: year === 2026 ? 8e8 : 10e8,
    operating_cashflow: year === 2026 ? 3e8 : -2e8,
    gross_margin: year === 2026 ? 0.26 : 0.28,
  };
}
it('shows four charts, separates improvements from concerns and switches period locally without losing raw evidence', async () => {
  const w = mount(FinancialResearchPanel, {
    props: {
      items: [
        report(2025),
        report(2026),
        report(2025, 'H1_CUMULATIVE', '06-30'),
        report(2026, 'H1_CUMULATIVE', '06-30'),
      ],
      date: '2026-11-17',
    },
    global: {
      stubs: {
        FinancialMetricChart: {
          props: ['metric', 'rows'],
          template: '<div class="chart-stub" :data-metric="metric"/>',
        },
        TermHelp: { props: ['label'], template: '<span>{{label}}</span>' },
        ElSelect: {
          name: 'ElSelect',
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<div><slot/></div>',
        },
        ElOption: true,
        ElTable: true,
        ElTableColumn: true,
      },
    },
  });
  try {
    expect(w.findAll('.chart-stub')).toHaveLength(4);
    expect(w.get('[data-testid=financial-verdict]').text()).toContain('经营表现分化');
    expect(w.text()).toContain('由净流出转为净流入');
    expect(w.text()).toContain('-2.00 个百分点');
    expect(w.text()).toContain('增收未增利');
    expect(w.text()).toContain('现有字段不能确认具体原因');
    expect(w.get('[data-testid=financial-originals]').attributes('open')).toBeUndefined();
    await w.getComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', 'H1_CUMULATIVE');
    await w.vm.$nextTick();
    expect(w.get('[data-testid=financial-verdict]').text()).toContain('2026-06-30');
    expect(w.get('[data-testid=financial-verdict]').text()).toContain('2025-06-30');
    expect(w.findAll('.chart-stub')).toHaveLength(4);
  } finally {
    w.unmount();
  }
});
it('explains missing financials without producing a positive or negative investment rating', () => {
  const w = mount(FinancialResearchPanel, {
    props: { items: [], date: '2026-11-17' },
    global: { stubs: { ElTable: true, ElTableColumn: true } },
  });
  expect(w.text()).toContain('不据此判断公司经营好坏');
  expect(w.find('[data-testid=financial-verdict]').exists()).toBe(false);
  w.unmount();
});

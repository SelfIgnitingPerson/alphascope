import { expect, it, vi } from 'vitest';
import { defineComponent, h, type VNode } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import ResearchPlansView from './ResearchPlansView.vue';
import { getPlans, getPlan, getPlanReviews, reviewPlan } from '../api/plans';
vi.mock('../api/plans', () => ({
  getPlans: vi.fn(),
  getPlan: vi.fn(),
  getPlanReviews: vi.fn(),
  reviewPlan: vi.fn(),
  archivePlan: vi.fn(),
  getPlanTodos: vi.fn().mockResolvedValue({
    total: 0,
    risk: 0,
    changed: 0,
    data: 0,
    due: 0,
    acknowledged: 0,
    items: [],
  }),
  followupPlans: vi.fn(),
  acknowledgePlan: vi.fn(),
}));
const table = defineComponent({
  props: { data: { type: Array, default: () => [] } },
  setup(p, { slots }) {
    return () =>
      h(
        'div',
        p.data.flatMap((row) =>
          (slots.default?.() || []).flatMap((v) => {
            const cell = v.children as { default?: (scope: { row: unknown }) => VNode[] } | null;
            return cell?.default?.({ row }) || [];
          }),
        ),
      );
  },
});
it('opens the saved plan directly, reads reviews without writing and only reviews on explicit action', async () => {
  vi.mocked(getPlans).mockResolvedValue({
    page: 1,
    size: 20,
    total: 1,
    items: [
      {
        id: 7,
        symbol: '600000.SH',
        name: '测试',
        horizon: 'SHORT',
        actionCode: 'OPEN_PULLBACK',
        archived: false,
        createdAt: '2026-11-16T18:00:00',
        asOf: '2026-11-16',
        latestReviewDate: '2026-11-16',
        latestReviewStatus: 'UNCHANGED',
        latestReviewExplanation: '旧日复核',
      },
    ],
  });
  vi.mocked(getPlan).mockResolvedValue({
    id: 7,
    horizon: 'SHORT',
    actionCode: 'OPEN_PULLBACK',
    archived: false,
    createdAt: '2026-11-16T18:00:00',
    snapshotHash: 'hash',
    research: {
      symbol: '600000.SH',
      name: '测试',
      asOf: '2026-11-16',
      close: 100,
      version: 'HOLDING_RESEARCH_V2',
      horizons: [],
      quoteDate: '2026-11-16',
      source: 'TEST',
      notes: [],
      priceVolume: {
        version: 'TEST',
        symbol: '600000.SH',
        name: '测试',
        status: 'INSUFFICIENT_DATA',
        windowStart: '2026-09-16',
        asOf: '2026-11-16',
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
        conclusion: '待核验',
        evidence: [],
        missing: [],
        methodNotes: [],
      },
    },
  });
  vi.mocked(getPlanReviews).mockResolvedValue({
    page: 1,
    size: 20,
    total: 1,
    items: [
      {
        planId: 7,
        date: '2026-11-16',
        status: 'UNCHANGED',
        explanation: '原始复核说明',
        adjustedOriginalStop: 90,
        close: 100,
        currentActionStatus: 'WAIT',
        limitations: ['旧记录适用范围'],
      },
    ],
  });
  const w = mount(ResearchPlansView, {
    attachTo: document.body,
    props: { date: '2026-11-17', refreshToken: 0, initialPlanId: 7 },
    global: {
      stubs: {
        RouterLink: { template: '<a><slot/></a>' },
        ElSkeleton: true,
        ElTable: table,
        ElTableColumn: true,
        ElInput: true,
        ElSelect: true,
        ElOption: true,
        ElButton: {
          emits: ['click'],
          props: ['disabled'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot/></button>',
        },
      },
    },
  });
  try {
    await flushPromises();
    expect(getPlan).toHaveBeenCalledWith(7);
    expect(getPlanReviews).toHaveBeenCalledWith(7);
    expect(reviewPlan).not.toHaveBeenCalled();
    expect(w.text()).toContain('回踩企稳建仓');
    expect(w.text()).toContain('方案暂无变化');
    expect(w.text()).toContain('本页 1 份');
    const method = w.get('[data-testid="plan-method-details"]');
    expect(method.attributes('open')).toBeUndefined();
    expect(method.text()).toContain('持有研究规则 · 第2版');
    expect(method.text()).toContain('HOLDING_RESEARCH_V2');
    expect(w.get('.plan-review').text()).toContain('90.00');
    expect(w.get('.plan-review').text()).toContain('复核日收盘（2026-11-16）：100.00');
    expect(w.get('[data-testid="same-day-review-note"]').text()).toContain(
      '尚未使用下一交易日的数据',
    );
    expect(w.get('.plan-review').text()).toContain('原来未满足的买入条件，不会因此变成满足');
    await w.get('.plan-review button[aria-label="风险参考怎么看：查看说明"]').trigger('click');
    const popover = document.querySelector('[data-testid="research-help-popover"]');
    expect(popover?.textContent).toContain('不是买入价');
    expect(popover?.textContent).toContain('即使收盘收复');
    (popover?.querySelector('[aria-label="关闭说明"]') as HTMLButtonElement).click();
    await flushPromises();
    await w.setProps({ date: '2026-11-18' });
    await flushPromises();
    expect(reviewPlan).not.toHaveBeenCalled();
    const button = w.findAll('button').find((b) => b.text().includes('按 2026-11-18 复核'))!;
    await button.trigger('click');
    await flushPromises();
    expect(reviewPlan).toHaveBeenCalledExactlyOnceWith(7, '2026-11-18');
    expect(getPlans).toHaveBeenCalledTimes(3);
  } finally {
    w.unmount();
  }
});

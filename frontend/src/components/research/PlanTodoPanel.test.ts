import { beforeEach, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import PlanTodoPanel from './PlanTodoPanel.vue';
import { getPlanTodos, followupPlans, acknowledgePlan } from '../../api/plans';
vi.mock('../../api/plans', () => ({
  getPlanTodos: vi.fn(),
  followupPlans: vi.fn(),
  acknowledgePlan: vi.fn(),
}));
const stubs = {
  RouterLink: { template: '<a><slot/></a>' },
  ElButton: {
    props: ['disabled'],
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot/></button>',
  },
  ElSkeleton: true,
  ElAlert: { props: ['title'], template: '<p>{{ title }}</p>' },
};
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getPlanTodos).mockResolvedValue({
    date: '2026-11-17',
    total: 121,
    risk: 1,
    changed: 0,
    data: 0,
    due: 2,
    acknowledged: 0,
    items: [
      {
        id: 121,
        symbol: '600000.SH',
        name: '重点风险',
        asOf: '2026-11-16',
        horizon: 'SHORT',
        actionCode: 'OPEN_PULLBACK',
        archived: false,
        createdAt: '2026-11-16T18:00:00',
        latestReviewDate: '2026-11-17',
        latestReviewStatus: 'ORIGINAL_STOP_BREACHED',
        latestReviewExplanation: '当日触及原风险线',
      },
    ],
  });
});
it('reads global todo counts on mount and date changes without writing reviews', async () => {
  const w = mount(PlanTodoPanel, {
    props: { date: '2026-11-17', refreshToken: 0 },
    global: { stubs },
  });
  await flushPromises();
  expect(w.text()).toContain('重点风险');
  expect(w.text()).toContain('当日触及原风险线');
  expect(followupPlans).not.toHaveBeenCalled();
  expect(acknowledgePlan).not.toHaveBeenCalled();
  await w.setProps({ date: '2026-11-18' });
  await flushPromises();
  expect(getPlanTodos).toHaveBeenLastCalledWith('2026-11-18', expect.any(AbortSignal));
  expect(followupPlans).not.toHaveBeenCalled();
  w.unmount();
});
it('acknowledges only the explicitly selected review and refreshes global counts', async () => {
  vi.mocked(acknowledgePlan).mockResolvedValue(null);
  const w = mount(PlanTodoPanel, {
    props: { date: '2026-11-17', refreshToken: 0 },
    global: { stubs },
  });
  await flushPromises();
  await w
    .findAll('button')
    .find((b) => b.text() === '已阅读此项')!
    .trigger('click');
  await flushPromises();
  expect(acknowledgePlan).toHaveBeenCalledWith(121, '2026-11-17');
  expect(w.emitted('changed')).toHaveLength(1);
  w.unmount();
});
it('reports partial batch failure instead of claiming all plans were completed', async () => {
  vi.mocked(followupPlans).mockResolvedValue({
    reviewed: 1,
    remaining: 2,
    issues: ['缺少同源因子'],
  });
  const w = mount(PlanTodoPanel, {
    props: { date: '2026-11-17', refreshToken: 0 },
    global: { stubs },
  });
  await flushPromises();
  await w
    .findAll('button')
    .find((b) => b.text() === '按所选日批量复核')!
    .trigger('click');
  await flushPromises();
  expect(followupPlans).toHaveBeenCalledWith('2026-11-17');
  expect(w.text()).toContain('仍需处理2份');
  expect(w.text()).toContain('缺少同源因子');
  w.unmount();
});
it('does not display a completed batch from a previous research date', async () => {
  let finish!: (value: Awaited<ReturnType<typeof followupPlans>>) => void;
  vi.mocked(followupPlans).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  const w = mount(PlanTodoPanel, {
    props: { date: '2026-11-17', refreshToken: 0 },
    global: { stubs },
  });
  await flushPromises();
  await w
    .findAll('button')
    .find((b) => b.text() === '按所选日批量复核')!
    .trigger('click');
  await w.setProps({ date: '2026-11-18' });
  finish({ reviewed: 99, remaining: 0, issues: [] });
  await flushPromises();
  expect(w.text()).not.toContain('本次已核对99');
  expect(w.emitted('changed')).toBeUndefined();
  w.unmount();
});

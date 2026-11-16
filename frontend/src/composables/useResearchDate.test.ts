import { afterEach, expect, it, vi } from 'vitest';
import { defineComponent, ref } from 'vue';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { useResearchDate, validResearchDate } from './useResearchDate';
import type { WorkspaceStatus } from '../api/product';
vi.mock('../utils/date', () => ({ today: () => '2026-11-13' }));
const status = (
  expectedTradeDate: string | null = '2026-11-12',
  calendarStatus = 'AVAILABLE',
): WorkspaceStatus => ({
  expectedTradeDate,
  calendarStatus,
  checkedAt: '',
  status: 'HEALTHY',
  instruments: [],
  capacity: { activeJobs: 0, activeValidations: 0, evidenceBytes: 0, oldestPendingAt: null },
  issues: [],
});
const disposals: (() => void)[] = [];
afterEach(() => disposals.splice(0).forEach((dispose) => dispose()));
async function setup(
  path: string,
  load = vi.fn<(signal: AbortSignal) => Promise<WorkspaceStatus>>().mockResolvedValue(status()),
) {
  const session = ref('one');
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/research', component: { template: '<div/>' } },
      { path: '/help', component: { template: '<div/>' }, meta: { hideBusinessDate: true } },
    ],
  });
  await router.push(path);
  let date!: ReturnType<typeof useResearchDate>;
  const wrapper = mount(
    defineComponent({
      setup() {
        date = useResearchDate({ session: () => session.value, load });
        return () => null;
      },
    }),
    { global: { plugins: [router] } },
  );
  disposals.push(() => wrapper.unmount());
  await flushPromises();
  return { date, router, session, load };
}
it('defaults to the confirmed close date and keeps the date in a reloadable URL', async () => {
  const { date, router, load } = await setup('/research');
  expect(load).toHaveBeenCalledOnce();
  expect(date.date.value).toBe('2026-11-12');
  expect(router.currentRoute.value.query.date).toBe('2026-11-12');
  expect(date.label.value).toContain('已确认');
  const reopened = await setup(router.currentRoute.value.fullPath);
  expect(reopened.date.date.value).toBe('2026-11-12');
  expect(reopened.load).not.toHaveBeenCalled();
});
it('honors explicit deep links and back/forward navigation without changing hidden help pages', async () => {
  const { date, router, load } = await setup('/research?date=2026-10-30');
  expect(load).not.toHaveBeenCalled();
  await router.push('/research?date=2026-11-10');
  await flushPromises();
  expect(date.date.value).toBe('2026-11-10');
  router.back();
  await flushPromises();
  expect(date.date.value).toBe('2026-10-30');
  await router.push('/help?term=ma');
  await flushPromises();
  expect(router.currentRoute.value.query).toEqual({ term: 'ma' });
  await router.push('/research');
  await flushPromises();
  expect(router.currentRoute.value.query.date).toBe('2026-10-30');
});
it('does not let a late calendar response overwrite a manual date or another session', async () => {
  const requests: { signal: AbortSignal; resolve: (value: WorkspaceStatus) => void }[] = [];
  const load = vi.fn(
    (signal: AbortSignal) =>
      new Promise<WorkspaceStatus>((resolve) => requests.push({ signal, resolve })),
  );
  const { date, session } = await setup('/research', load);
  date.select('2026-11-10');
  await flushPromises();
  expect(requests[0]!.signal.aborted).toBe(true);
  requests[0]!.resolve(status());
  await flushPromises();
  expect(date.date.value).toBe('2026-11-10');
  const refresh = date.latest();
  session.value = '';
  await flushPromises();
  requests[1]!.resolve(status());
  await refresh;
  expect(date.ready.value).toBe(false);
});
it('does not guess weekdays or treat stale quotes as the calendar; explicit reset can recover', async () => {
  const load = vi
    .fn<(signal: AbortSignal) => Promise<WorkspaceStatus>>()
    .mockResolvedValue(status(null, 'UNKNOWN_CALENDAR'));
  const { date } = await setup('/research', load);
  expect(date.date.value).toBe('2026-11-13');
  expect(date.ready.value).toBe(true);
  expect(date.warning.value).toContain('不能确认');
  expect(date.label.value).toContain('尚未确认');
  load.mockRejectedValueOnce(new Error('offline'));
  await date.latest();
  expect(date.warning.value).toContain('核查暂不可用');
  load.mockResolvedValueOnce(status());
  await date.latest();
  expect(date.date.value).toBe('2026-11-12');
  expect(date.warning.value).toBe('');
});
it('rejects invalid calendar dates and future dates rather than issuing business queries for them', async () => {
  for (const value of ['2026-02-30', '2026-11-14', '1969-12-31', ['2026-11-12'], ''])
    expect(validResearchDate(value)).toBe(false);
  expect(validResearchDate('2024-02-29')).toBe(true);
  const { date, router } = await setup('/research?date=2026-02-30');
  expect(date.date.value).toBe('2026-11-12');
  expect(router.currentRoute.value.query.date).toBe('2026-11-12');
  date.select('2026-11-14');
  await flushPromises();
  expect(date.date.value).toBe('2026-11-12');
  expect(date.warning.value).toContain('有效日期');
});
it('never cancels an in-progress lazy page navigation to canonicalize the previous URL', async () => {
  let resolveCalendar!: (value: WorkspaceStatus) => void;
  const load = vi.fn(
    () =>
      new Promise<WorkspaceStatus>((resolve) => {
        resolveCalendar = resolve;
      }),
  );
  const { date, router } = await setup('/research', load);
  let resolvePage!: (value: { template: string }) => void;
  router.addRoute({
    path: '/other',
    component: () =>
      new Promise((resolve) => {
        resolvePage = resolve;
      }),
  });
  const navigation = router.push('/other');
  await flushPromises();
  resolveCalendar(status());
  await flushPromises();
  expect(date.ready.value).toBe(true);
  resolvePage({ template: '<div/>' });
  await navigation;
  await flushPromises();
  expect(router.currentRoute.value.path).toBe('/other');
  expect(router.currentRoute.value.query.date).toBe('2026-11-12');
});

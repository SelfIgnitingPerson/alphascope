import { it, expect, vi, afterEach } from 'vitest';
import { shallowMount, flushPromises } from '@vue/test-utils';
import { defineComponent } from 'vue';
import JobsView from './JobsView.vue';
import { getJobs, getJob, runJob } from '../api/jobs';
vi.mock('../api/jobs', () => ({
  getJobs: vi.fn().mockResolvedValue({ items: [], total: 0, page: 1, size: 20 }),
  getJob: vi.fn().mockResolvedValue({ id: 7, status: 'SUCCESS', stages: [] }),
  runJob: vi.fn().mockResolvedValue({ id: 7, status: 'PENDING', stages: [] }),
  retryJob: vi.fn(),
}));
vi.mock('../api/market', () => ({
  getProviders: vi.fn().mockResolvedValue({ dataScope: 'REAL:B' }),
}));
afterEach(() => vi.useRealTimers());
it('shows the accepted job immediately and stops polling after disposal', async () => {
  vi.useFakeTimers();
  const Button = defineComponent({ template: '<button><slot/></button>' });
  vi.mocked(getJobs).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
  vi.mocked(runJob).mockResolvedValue({
    id: 7,
    job_name: 'DAILY',
    data_scope: 'REAL:B',
    business_date: '2026-10-30',
    attempt: 1,
    status: 'PENDING',
    stages: [],
  });
  vi.mocked(getJob).mockResolvedValue({
    id: 7,
    job_name: 'DAILY',
    data_scope: 'REAL:B',
    business_date: '2026-10-30',
    attempt: 1,
    status: 'SUCCESS',
    stages: [],
  });
  const wrapper = shallowMount(JobsView, {
    props: { date: '2026-10-30', refreshToken: 0 },
    global: {
      stubs: {
        SectionCard: { template: '<section><slot name="actions"/><slot/></section>' },
        AsyncState: { template: '<div><slot/></div>' },
        'el-table': { template: '<div />' },
        'el-button': Button,
      },
    },
  });
  await flushPromises();
  const submit = wrapper.findAll('button').find((b) => b.text().includes('生成 / 补跑日报'))!;
  await submit.trigger('click');
  await flushPromises();
  expect(runJob).toHaveBeenCalledWith({ type: 'DAILY', date: '2026-10-30', force: false });
  expect(wrapper.text()).toContain('后台会继续执行');
  await vi.advanceTimersByTimeAsync(2100);
  await flushPromises();
  expect(getJob).toHaveBeenCalledWith(7, expect.any(AbortSignal));
  wrapper.unmount();
  const count = vi.mocked(getJobs).mock.calls.length;
  await vi.advanceTimersByTimeAsync(5000);
  expect(getJobs).toHaveBeenCalledTimes(count);
});
it('labels a failed job from an old provider configuration as historical', async () => {
  const { getProviders } = await import('../api/market');
  vi.mocked(getProviders).mockResolvedValue({
    mode: 'MULTI',
    dataScope: 'REAL:BAOSTOCK,SINA',
    isSynthetic: false,
    calendarSource: 'BAOSTOCK',
    channels: [],
    latestData: [],
  });
  const old = {
    id: 13,
    job_name: 'DAILY',
    data_scope: 'REAL:BAOSTOCK,AKSHARE',
    business_date: '2026-10-30',
    attempt: 1,
    status: 'FAILED',
    error: 'BAOSTOCK:UNAVAILABLE,AKSHARE:UNAVAILABLE',
    started_at: '2026-10-30T19:00:57',
    ended_at: '2026-10-30T19:00:57',
    stages: [],
  };
  vi.mocked(getJobs).mockResolvedValue({ items: [old], total: 1, page: 1, size: 20 });
  vi.mocked(getJob).mockResolvedValue(old);
  const Button = defineComponent({ template: '<button><slot/></button>' });
  const Column = defineComponent({
    setup() {
      return { row: old };
    },
    template: '<div><slot :row="row"/></div>',
  });
  const wrapper = shallowMount(JobsView, {
    props: { date: '2026-10-30', refreshToken: 0 },
    global: {
      stubs: {
        SectionCard: { template: '<section><slot name="actions"/><slot/></section>' },
        AsyncState: { template: '<div><slot/></div>' },
        'el-table': { template: '<div><slot/></div>' },
        'el-table-column': Column,
        'el-button': Button,
        'el-drawer': { template: '<aside><slot/></aside>' },
      },
    },
  });
  await flushPromises();
  await wrapper
    .findAll('button')
    .find((b) => b.text().includes('查看阶段'))!
    .trigger('click');
  await flushPromises();
  expect(wrapper.text()).toContain('历史配置下的执行记录');
  expect(wrapper.text()).toContain('不代表当前通道状态');
  expect(wrapper.text()).toContain('REAL:BAOSTOCK,SINA');
  expect(wrapper.text()).toContain('执行时间');
  wrapper.unmount();
});

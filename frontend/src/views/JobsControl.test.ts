import { it, expect, vi, afterEach } from 'vitest';
import { shallowMount, flushPromises } from '@vue/test-utils';
import { defineComponent } from 'vue';
import JobsView from './JobsView.vue';
import { getJobs, getJob, cancelJob } from '../api/jobs';
import type { JobDetail } from '../types/jobs';
vi.mock('../api/jobs', () => ({
  getJobs: vi.fn(),
  getJob: vi.fn(),
  cancelJob: vi.fn(),
  runJob: vi.fn(),
  retryJob: vi.fn(),
}));
vi.mock('../api/market', () => ({
  getProviders: vi.fn().mockResolvedValue({ dataScope: 'REAL:BAOSTOCK' }),
}));
afterEach(() => vi.useRealTimers());
const pending: JobDetail = {
  id: 9,
  job_name: 'DAILY',
  data_scope: 'REAL:BAOSTOCK',
  business_date: '2026-10-30',
  attempt: 1,
  status: 'RUNNING',
  stages: [],
};
function mount() {
  const Column = defineComponent({
    setup: () => ({ row: pending }),
    template: '<div><slot :row="row"/></div>',
  });
  return shallowMount(JobsView, {
    props: { date: '2026-10-30', refreshToken: 0 },
    global: {
      stubs: {
        SectionCard: { template: '<section><slot name="actions"/><slot/></section>' },
        AsyncState: { template: '<div><slot/></div>' },
        'el-table': { template: '<div><slot/></div>' },
        'el-table-column': Column,
        'el-button': { template: '<button><slot/></button>' },
        'el-drawer': { template: '<aside><slot/></aside>' },
      },
    },
  });
}
it('polls a persisted cancellation until cleanup finishes and then stops', async () => {
  vi.useFakeTimers();
  vi.mocked(getJobs).mockResolvedValue({ items: [pending], total: 1, page: 1, size: 20 });
  const cancelled = { ...pending, status: 'CANCELLED', data_status: 'UNAVAILABLE' };
  vi.mocked(cancelJob).mockImplementation(async () => {
    vi.mocked(getJobs).mockResolvedValue({ items: [cancelled], total: 1, page: 1, size: 20 });
    return { ...pending, status: 'CANCEL_REQUESTED' };
  });
  vi.mocked(getJob).mockResolvedValue(cancelled);
  const wrapper = mount();
  await flushPromises();
  await wrapper
    .findAll('button')
    .find((button) => button.text() === '取消')!
    .trigger('click');
  await flushPromises();
  expect(cancelJob).toHaveBeenCalledWith(9);
  expect(wrapper.text()).toContain('正在释放资源');
  await vi.advanceTimersByTimeAsync(2100);
  await flushPromises();
  const calls = vi.mocked(getJob).mock.calls.length;
  await vi.advanceTimersByTimeAsync(5000);
  await flushPromises();
  expect(getJob).toHaveBeenCalledTimes(calls);
  wrapper.unmount();
});
it('displays completed execution together with partial data and the actual missing reason', async () => {
  vi.mocked(getJobs).mockResolvedValue({ items: [pending], total: 1, page: 1, size: 20 });
  vi.mocked(getJob).mockResolvedValue({
    ...pending,
    status: 'SUCCESS',
    data_status: 'PARTIAL',
    stages: [
      {
        stage_name: 'RESEARCH_DATA',
        status: 'SUCCESS',
        data_status: 'UNAVAILABLE',
        details: {
          quality: {
            status: 'UNAVAILABLE',
            available: 0,
            expected: 1,
            note: '执行已结束',
            issues: [{ scope: 'SH_SZ_A', status: 'UNAVAILABLE', reason: '当日市场截面未覆盖' }],
          },
        },
      },
    ],
  });
  const wrapper = mount();
  await flushPromises();
  await wrapper
    .findAll('button')
    .find((button) => button.text().includes('查看阶段'))!
    .trigger('click');
  await flushPromises();
  expect(wrapper.text()).toContain('当日市场截面未覆盖');
  const badges = wrapper
    .findAllComponents({ name: 'StateBadge' })
    .map((component) => component.props('value'));
  expect(badges).toContain('SUCCESS');
  expect(badges).toContain('PARTIAL');
  expect(badges).toContain('UNAVAILABLE');
  wrapper.unmount();
});
it('explains unperformed quality checks and shows the whole-job reason for an old generic stage', async () => {
  vi.mocked(getJobs).mockResolvedValue({ items: [pending], total: 1, page: 1, size: 20 });
  vi.mocked(getJob).mockResolvedValue({
    ...pending,
    status: 'FAILED',
    data_status: 'UNAVAILABLE',
    error: '行情采集失败：000300.SH；BAOSTOCK:ACCESS_RESTRICTED',
    stages: [
      {
        stage_name: 'MARKET_SYNC',
        status: 'FAILED',
        data_status: 'UNAVAILABLE',
        error: '阶段失败，错误类型：IllegalStateException',
        details: {
          quality: { status: 'UNAVAILABLE', available: 0, expected: 0, issues: [], note: '未完成' },
        },
      },
    ],
  });
  const wrapper = mount();
  await flushPromises();
  await wrapper
    .findAll('button')
    .find((button) => button.text().includes('查看阶段'))!
    .trigger('click');
  await flushPromises();
  expect(wrapper.text()).toContain('尚未形成可核验项数');
  expect(wrapper.get('.stage-error').text()).toContain('000300.SH');
  wrapper.unmount();
});
it('keeps the pasted legacy channel codes out of the primary alert', async () => {
  const raw =
    '行情采集失败：002837.SZ（2024-11-12至2026-11-12，已保存485条/应有486个交易日；无完整可用版本）。所有真实行情通道不可用（不会回退模拟）：SINA:UNCLASSIFIED_FAILURE';
  vi.mocked(getJobs).mockResolvedValue({ items: [pending], total: 1, page: 1, size: 20 });
  vi.mocked(getJob).mockResolvedValue({
    ...pending,
    status: 'FAILED',
    error: raw,
    stages: [{ stage_name: 'MARKET_SYNC', status: 'FAILED', error: raw }],
  });
  const wrapper = mount();
  await flushPromises();
  await wrapper
    .findAll('button')
    .find((button) => button.text().includes('查看阶段'))!
    .trigger('click');
  await flushPromises();
  const alert = wrapper.findAll('[type="error"]').find((element) => element.attributes('title'))!;
  expect(alert.attributes('title')).toContain('缺1天');
  expect(alert.attributes('title')).not.toContain('UNCLASSIFIED_FAILURE');
  expect(wrapper.get('.stage-error').attributes('open')).toBeUndefined();
  wrapper.unmount();
});

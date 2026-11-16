import { beforeEach, expect, it, vi } from 'vitest';
import { defineComponent } from 'vue';
import { flushPromises, shallowMount } from '@vue/test-utils';
import ChannelProbePanel from './ChannelProbePanel.vue';
import { testProvider } from '../../api/provider-probe';
import type { ProviderProbeResult } from '../../types/provider-probe';
const auth = vi.hoisted(() => ({ user: { value: { id: 1, username: 'owner', role: 'ADMIN' } } }));
vi.mock('../../composables/useAuth', () => ({ useAuth: () => auth }));
vi.mock('../../api/provider-probe', () => ({ testProvider: vi.fn() }));
vi.mock('../../utils/date', () => ({ today: () => '2026-11-13' }));
const result = (): ProviderProbeResult => ({
  channel: 'SINA',
  capability: 'STOCK_DAILY',
  requestedDate: '2026-11-12',
  status: 'SUCCESS',
  checkedAt: '2026-11-13T01:00:00Z',
  elapsedMs: 125,
  sample: {
    symbol: '600000.SH',
    records: 1,
    dataDate: '2026-11-12',
    source: 'SINA',
    marketOpen: null,
  },
  diagnostic: null,
  retryAt: '2026-11-13T01:01:00Z',
});
const Select = defineComponent({
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div><slot/></div>',
});
const props = {
  channel: 'SINA',
  date: '2026-11-12',
  channels: [
    {
      channel: 'SINA',
      supportsIndexBars: false,
      state: 'NOT_CHECKED',
      consecutiveFailures: 0,
      lastError: '',
      lastSuccess: null,
      retryAt: null,
      capabilities: [
        { capability: 'STOCK_DAILY', supported: true, state: 'NOT_CHECKED' },
        { capability: 'INDEX_DAILY', supported: false, state: 'UNSUPPORTED' },
      ],
    },
  ],
};
const options = {
  props,
  global: {
    stubs: {
      SectionCard: { template: '<section><slot/></section>' },
      FailureExplanation: false,
      'el-select': Select,
      'el-option': true,
      'el-button': {
        props: ['disabled', 'loading'],
        template: '<button :disabled="disabled"><slot/></button>',
      },
      'el-date-picker': true,
      'el-alert': true,
    },
  },
};
beforeEach(() => {
  vi.clearAllMocks();
  auth.user.value = { id: 1, username: 'owner', role: 'ADMIN' };
  vi.mocked(testProvider).mockResolvedValue(result());
});
it('does not test on mount or prop refresh and sends exactly one explicit request', async () => {
  const wrapper = shallowMount(ChannelProbePanel, options);
  await flushPromises();
  expect(testProvider).not.toHaveBeenCalled();
  await wrapper.setProps({ date: '2026-11-11' });
  expect(testProvider).not.toHaveBeenCalled();
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  expect(testProvider).toHaveBeenCalledExactlyOnceWith({
    channel: 'SINA',
    capability: 'STOCK_DAILY',
    date: '2026-11-11',
  });
  expect(wrapper.get('[data-testid="provider-test-result"]').text()).toContain('本项样本核验通过');
  expect(wrapper.text()).toContain('2026-11-12');
  expect(wrapper.text()).toContain('600000.SH');
  expect(wrapper.text()).toContain('125 毫秒');
  expect(wrapper.emitted('completed')).toHaveLength(1);
  wrapper.unmount();
});
it('blocks duplicate submit, keeps request identity and labels a protected skip rather than success', async () => {
  let complete!: (value: ProviderProbeResult) => void;
  vi.mocked(testProvider).mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        complete = resolve;
      }),
  );
  const wrapper = shallowMount(ChannelProbePanel, options);
  await wrapper.get('form').trigger('submit');
  await wrapper.get('form').trigger('submit');
  expect(testProvider).toHaveBeenCalledTimes(1);
  complete({
    ...result(),
    status: 'SKIPPED',
    sample: null,
    diagnostic: {
      code: 'ACCESS_RESTRICTED',
      title: '访问保护中',
      reason: '原始拒绝已保存',
      impact: '本次未请求',
      action: '核对限制，不能绕过',
    },
  });
  await flushPromises();
  expect(wrapper.get('[data-testid="provider-test-result"]').text()).toContain(
    '本次未执行上游核验',
  );
  expect(wrapper.text()).toContain('原始拒绝已保存');
  expect(wrapper.text()).not.toContain('有效记录');
  expect(wrapper.emitted('completed')).toBeUndefined();
  wrapper.unmount();
});
it('hides active testing from non-admin users and explains shared quota', async () => {
  auth.user.value.role = 'USER';
  const wrapper = shallowMount(ChannelProbePanel, options);
  expect(wrapper.find('[data-testid="test-provider"]').exists()).toBe(false);
  expect(wrapper.text()).toContain('由管理员执行');
  expect(testProvider).not.toHaveBeenCalled();
  wrapper.unmount();
});
it('preserves user-edited dates and reports transport errors without fabricating test results', async () => {
  const wrapper = shallowMount(ChannelProbePanel, options);
  wrapper.findComponent({ name: 'el-date-picker' }).vm.$emit('update:modelValue', '2026-10-30');
  await flushPromises();
  await wrapper.setProps({ date: '2026-11-11' });
  vi.mocked(testProvider).mockRejectedValueOnce(new Error('网络异常，请核对测试状态'));
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  expect(testProvider).toHaveBeenLastCalledWith({
    channel: 'SINA',
    capability: 'STOCK_DAILY',
    date: '2026-10-30',
  });
  expect(wrapper.find('[data-testid="provider-test-result"]').exists()).toBe(false);
  expect(wrapper.find('el-alert-stub').attributes('title')).toContain('网络异常');
  wrapper.unmount();
});

it('disables repeated testing during the same-channel cooldown', async () => {
  vi.mocked(testProvider).mockResolvedValueOnce({
    ...result(),
    retryAt: new Date(Date.now() + 60000).toISOString(),
  });
  const wrapper = shallowMount(ChannelProbePanel, options);
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  expect(wrapper.get('[data-testid="test-provider"]').attributes('disabled')).toBeDefined();
  expect(wrapper.get('[data-testid="test-provider"]').text()).toMatch(/(59|60)秒后可再试/);
  await wrapper.setProps({ channel: 'EASTMONEY' });
  expect(wrapper.get('[data-testid="test-provider"]').text()).toContain('1秒后可再试');
  expect(wrapper.text()).toContain('新浪财经'); // 上次结果仍明确保留原通道身份。
  wrapper.unmount();
});

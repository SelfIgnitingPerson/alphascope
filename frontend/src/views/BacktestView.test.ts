import { beforeEach, it, expect, vi } from 'vitest';
import { shallowMount, flushPromises } from '@vue/test-utils';
import { defineComponent } from 'vue';
import BacktestView from './BacktestView.vue';
import { getBacktest } from '../api/market';
vi.mock('../api/market', () => ({ getBacktest: vi.fn() }));
const Select = defineComponent({
  name: 'SelectStub',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div />',
});
const Table = defineComponent({ name: 'TableStub', props: ['data'], template: '<div />' });
const Input = defineComponent({
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
});
beforeEach(() => vi.clearAllMocks());
const mountPage = (initial = '300476.SZ') => {
  const wrapper = shallowMount(BacktestView, {
    props: { date: '2026-10-30', refreshToken: 0 },
    global: {
      stubs: {
        SectionTabs: false,
        SectionCard: { template: '<section><slot name="actions"/><slot/></section>' },
        AsyncState: { template: '<div><slot/></div>' },
        'el-input': Input,
        'el-select': Select,
        'el-table': Table,
      },
    },
  });
  if (initial)
    wrapper.findComponent({ name: 'ResearchValidationPanel' }).vm.$emit('select', initial);
  return wrapper;
};
const select = (wrapper: ReturnType<typeof mountPage>, label: string) =>
  wrapper.findAllComponents(Select).find((field) => field.attributes('aria-label') === label)!;
it('keeps no-sample status, strict availability and holdout statistics distinct', async () => {
  vi.mocked(getBacktest).mockResolvedValue({
    dataStatus: 'INSUFFICIENT_DATA',
    reason: '没有成熟样本',
    statistics: {
      '5D': { RULE_A: { sampleCount: 0, triggeredCount: 1, dataStatus: 'WAITING_WINDOW' } },
    },
    validationWindows: [
      {
        label: 'EARLY',
        start: '2026-01-01',
        end: '2026-07-01',
        statistics: {
          '5D': { RULE_A: { sampleCount: 0, triggeredCount: 1, dataStatus: 'WAITING_WINDOW' } },
        },
      },
      {
        label: 'HOLDOUT',
        start: '2026-07-02',
        end: '2026-10-30',
        statistics: {
          '5D': {
            RULE_A: {
              sampleCount: 1,
              triggeredCount: 1,
              dataStatus: 'SMALL_SAMPLE',
              meanReturn: 0.1,
            },
          },
        },
      },
    ],
  });
  const wrapper = mountPage();
  await flushPromises();
  expect(wrapper.findComponent(Table).props('data')[0]).toMatchObject({
    sampleCount: 0,
    dataStatus: 'WAITING_WINDOW',
  });
  select(wrapper, '可用时点').vm.$emit('update:modelValue', 'POINT_IN_TIME');
  await flushPromises();
  expect(vi.mocked(getBacktest).mock.calls.at(-1)?.[0].availabilityMode).toBe('POINT_IN_TIME');
  const calls = vi.mocked(getBacktest).mock.calls.length;
  select(wrapper, '验证区间').vm.$emit('update:modelValue', 'HOLDOUT');
  await flushPromises();
  expect(wrapper.findComponent(Table).props('data')[0]).toMatchObject({
    sampleCount: 1,
    dataStatus: 'SMALL_SAMPLE',
  });
  expect(getBacktest).toHaveBeenCalledTimes(calls);
  const validation = wrapper.findComponent({ name: 'ResearchValidationPanel' }).vm;
  const tabs = wrapper.findAll('[role="tab"]');
  expect(tabs[0].text()).toBe('历史规律验证');
  expect(tabs[0].attributes('aria-selected')).toBe('true');
  await tabs[1].trigger('click');
  expect(wrapper.findAll('[role="tabpanel"]')[0].isVisible()).toBe(false);
  await tabs[2].trigger('click');
  expect(
    wrapper.findComponent({ name: 'ValidationExperimentPanel' }).props('request'),
  ).toMatchObject({
    symbol: '300476.SZ',
    availabilityMode: 'POINT_IN_TIME',
  });
  await tabs[1].trigger('click');
  expect(wrapper.findComponent({ name: 'ResearchValidationPanel' }).vm).toBe(validation);
  expect(select(wrapper, '验证区间').props('modelValue')).toBe('HOLDOUT');
  expect(getBacktest).toHaveBeenCalledTimes(calls);
  wrapper.unmount();
});

it('accepts a bare stock code, labels unsubmitted edits and never silently queries the previous stock', async () => {
  vi.mocked(getBacktest).mockResolvedValue({ dataStatus: 'INSUFFICIENT_DATA', statistics: {} });
  const wrapper = mountPage();
  await flushPromises();
  await wrapper.get('[aria-label="查询股票代码"]').setValue('002837');
  expect(wrapper.get('[data-testid="signal-query-pending"]').text()).toContain('300476.SZ');
  const calls = vi.mocked(getBacktest).mock.calls.length;
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  expect(getBacktest).toHaveBeenCalledTimes(calls + 1);
  expect(vi.mocked(getBacktest).mock.calls.at(-1)?.[0].symbol).toBe('002837.SZ');
  expect(wrapper.find('[data-testid="signal-query-pending"]').exists()).toBe(false);
  await wrapper.get('[aria-label="查询股票代码"]').setValue('wrong');
  await wrapper.get('form').trigger('submit');
  expect(wrapper.get('[role="alert"]').text()).toContain('请输入有效股票代码');
  expect(getBacktest).toHaveBeenCalledTimes(calls + 1);
  wrapper.unmount();
});

it('does not hardcode a stock and follows the research watchlist selection without losing an explicit version', async () => {
  vi.mocked(getBacktest).mockResolvedValue({ dataStatus: 'INSUFFICIENT_DATA', statistics: {} });
  const wrapper = mountPage('');
  await flushPromises();
  expect(getBacktest).not.toHaveBeenCalled();
  const research = wrapper.findComponent({ name: 'ResearchValidationPanel' });
  research.vm.$emit('select', '002463.SZ');
  await flushPromises();
  expect(vi.mocked(getBacktest).mock.calls.at(-1)?.[0].symbol).toBe('002463.SZ');
  select(wrapper, '规则版本或阈值指纹').vm.$emit('update:modelValue', 'RULE_V2@abc');
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  research.vm.$emit('select', '002463.SZ');
  await flushPromises();
  expect(vi.mocked(getBacktest).mock.calls.at(-1)?.[0].ruleVersion).toBe('RULE_V2@abc');
  research.vm.$emit('select', '601899.SH');
  await flushPromises();
  expect(vi.mocked(getBacktest).mock.calls.at(-1)?.[0]).toMatchObject({
    symbol: '601899.SH',
    ruleVersion: undefined,
  });
  wrapper.unmount();
});

it('offers actual saved versions and changes the return window without merging or refetching', async () => {
  vi.mocked(getBacktest).mockResolvedValue({
    dataStatus: 'INSUFFICIENT_DATA',
    statistics: {
      '5D': {
        RULE_A: {
          sampleCount: 0,
          triggeredCount: 3,
          dataStatus: 'INSUFFICIENT_DATA',
          ruleVersions: ['RULE_V1', 'RULE_V2@abc'],
          exclusions: { MIXED_RULE_VERSIONS: 3 },
        },
        RULE_B: { sampleCount: 0, triggeredCount: 0, dataStatus: 'NO_TRIGGERED_SIGNALS' },
      },
      '10D': { RULE_A: { sampleCount: 0, triggeredCount: 3, dataStatus: 'WAITING_WINDOW' } },
    },
  });
  const wrapper = mountPage();
  await flushPromises();
  expect(wrapper.get('[data-testid="saved-signal-guidance"]').text()).toContain(
    '同规则存在多个版本',
  );
  expect(wrapper.findComponent(Table).props('data')).toHaveLength(1);
  const calls = vi.mocked(getBacktest).mock.calls.length;
  select(wrapper, '信号收益窗口').vm.$emit('update:modelValue', '10D');
  await flushPromises();
  expect(wrapper.findComponent(Table).props('data')[0].window).toBe('10D');
  expect(getBacktest).toHaveBeenCalledTimes(calls);
  select(wrapper, '规则版本或阈值指纹').vm.$emit('update:modelValue', 'RULE_V2@abc');
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  expect(vi.mocked(getBacktest).mock.calls.at(-1)?.[0].ruleVersion).toBe('RULE_V2@abc');
  wrapper.unmount();
});

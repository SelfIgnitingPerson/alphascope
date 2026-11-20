import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { flushPromises, shallowMount } from '@vue/test-utils';
import ResearchValidationPanel from './ResearchValidationPanel.vue';
import {
  cancelValidation,
  getValidationTask,
  getValidationTasks,
  submitValidation,
} from '../../api/validation';
import { getWatchlist } from '../../api/portfolio';
import type { ValidationTask } from '../../types/validation';

vi.mock('../../api/validation', () => ({
  cancelValidation: vi.fn(),
  getValidationTask: vi.fn(),
  getValidationTasks: vi.fn(),
  submitValidation: vi.fn(),
  validationEvidenceUrl: (id: number) => `/api/v1/backtest/validation-tasks/${id}/evidence`,
}));
vi.mock('../../api/portfolio', () => ({ getWatchlist: vi.fn() }));
const stubs = {
  'el-alert': true,
  'el-option': true,
  'el-table-column': true,
  'router-link': { template: '<a><slot/></a>' },
  'el-date-picker': {
    props: ['modelValue', 'valueFormat', 'format', 'clearable'],
    emits: ['update:modelValue'],
    template:
      '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  SectionCard: { template: '<section><slot name="actions"/><slot/></section>' },
  'el-button': {
    props: ['disabled', 'nativeType'],
    emits: ['click'],
    template:
      '<button :type="nativeType || \'button\'" :disabled="disabled" @click="$emit(\'click\')"><slot/></button>',
  },
  'el-select': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div><slot/></div>',
  },
  'el-table': { props: ['data'], template: '<div />' },
};
const task = (status: string): ValidationTask => ({
  id: 7,
  status,
  scope: 'ALL',
  dataScope: 'REAL:TEST_FIXTURE',
  completed: 0,
  total: 2,
});
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getWatchlist).mockResolvedValue([
    { symbol: '300476.SZ', name: '胜宏科技', enabled: true, priority: 0 },
  ]);
  vi.mocked(getValidationTasks).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
  vi.mocked(submitValidation).mockResolvedValue(task('PENDING'));
  vi.mocked(getValidationTask).mockResolvedValue(task('RUNNING'));
  vi.mocked(cancelValidation).mockResolvedValue(task('CANCELLED'));
});
afterEach(() => vi.useRealTimers());

it('lists all enabled current watches instead of limiting the selector to old task members', async () => {
  vi.mocked(getWatchlist).mockResolvedValue([
    { symbol: '002463.SZ', name: '沪电股份', enabled: true, priority: 0 },
    { symbol: '601899.SH', name: '紫金矿业', enabled: true, priority: 1 },
    { symbol: '300476.SZ', name: '胜宏科技', enabled: false, priority: 2 },
  ]);
  const wrapper = shallowMount(ResearchValidationPanel, {
    props: { date: '2026-11-13', synthetic: false, preferredSymbol: '300476.SZ' },
    global: { stubs },
  });
  await flushPromises();
  const selector = wrapper.get('[aria-label="验证结果股票"]');
  expect(selector.findAll('el-option-stub').map((option) => option.attributes('value'))).toEqual([
    '002463.SZ',
    '601899.SH',
  ]);
  expect(wrapper.emitted('select')?.at(-1)).toEqual(['002463.SZ']);
  expect(wrapper.get('[data-testid="validation-stock-missing"]').text()).toContain('002463.SZ');
  expect(submitValidation).not.toHaveBeenCalled();
  await wrapper
    .findAll('button')
    .find((button) => button.text().includes('准备本股验证'))!
    .trigger('click');
  await wrapper.get('[data-testid="validation-form"]').trigger('submit');
  await flushPromises();
  expect(submitValidation).toHaveBeenCalledExactlyOnceWith({
    start: '2024-11-13',
    end: '2026-11-13',
    scope: 'TECHNICAL',
    symbols: ['002463.SZ'],
    portfolioCapital: 50000,
  });
  wrapper.unmount();
});

it('refreshes the current watch scope and clears a removed selection without generating a task', async () => {
  vi.mocked(getWatchlist)
    .mockResolvedValueOnce([{ symbol: '002463.SZ', name: '沪电股份', enabled: true, priority: 0 }])
    .mockResolvedValueOnce([{ symbol: '601899.SH', name: '紫金矿业', enabled: true, priority: 0 }])
    .mockResolvedValueOnce([]);
  const wrapper = shallowMount(ResearchValidationPanel, {
    props: { date: '2026-11-13', synthetic: false, refreshToken: 0 },
    global: { stubs },
  });
  await flushPromises();
  await wrapper
    .findAll('button')
    .find((button) => button.text().includes('刷新自选与记录'))!
    .trigger('click');
  await flushPromises();
  expect(wrapper.emitted('select')?.at(-1)).toEqual(['601899.SH']);
  expect(
    wrapper
      .get('[aria-label="验证结果股票"]')
      .findAll('el-option-stub')
      .map((option) => option.attributes('value')),
  ).toEqual(['601899.SH']);
  await wrapper.setProps({ refreshToken: 2 });
  await flushPromises();
  expect(wrapper.emitted('select')?.at(-1)).toEqual(['']);
  expect(wrapper.get('[data-testid="validation-no-watches"]').text()).toContain('暂无启用的自选股');
  expect(wrapper.find('[data-testid="validation-stock-context"]').exists()).toBe(false);
  expect(submitValidation).not.toHaveBeenCalled();
  wrapper.unmount();
});

it('does not let an earlier stock task response replace a later selection', async () => {
  let first!: (value: ValidationTask) => void, second!: (value: ValidationTask) => void;
  const one = { ...task('SUCCESS'), id: 7, symbols: ['002463.SZ'] };
  const two = { ...task('SUCCESS'), id: 8, symbols: ['601899.SH'] };
  vi.mocked(getWatchlist).mockResolvedValue([
    { symbol: '002463.SZ', name: '沪电股份', enabled: true, priority: 0 },
    { symbol: '601899.SH', name: '紫金矿业', enabled: true, priority: 1 },
  ]);
  vi.mocked(getValidationTasks).mockResolvedValue({
    items: [one, two],
    total: 2,
    page: 1,
    size: 20,
  });
  vi.mocked(getValidationTask).mockImplementation(
    (id) =>
      new Promise((resolve) => {
        if (id === 7) first = resolve;
        else second = resolve;
      }),
  );
  const wrapper = shallowMount(ResearchValidationPanel, {
    props: { date: '2026-11-13', synthetic: false },
    global: { stubs },
  });
  await flushPromises();
  wrapper
    .findAllComponents(stubs['el-select'])
    .find((field) => field.attributes('aria-label') === '验证结果股票')!
    .vm.$emit('update:modelValue', '601899.SH');
  await flushPromises();
  second(two);
  await flushPromises();
  first(one);
  await flushPromises();
  expect(wrapper.get('[data-testid="validation-task-detail"]').text()).toContain('#8');
  expect(wrapper.get('[data-testid="validation-task-detail"]').text()).not.toContain('#7');
  wrapper.unmount();
});

it('reads only lightweight history on mount and submits the explicit selected dates once', async () => {
  const wrapper = shallowMount(ResearchValidationPanel, {
    props: { date: '2026-10-30', synthetic: false },
    global: { stubs },
  });
  await flushPromises();
  expect(submitValidation).not.toHaveBeenCalled();
  await wrapper.get('[aria-label="验证开始日期"]').setValue('2024-10-14');
  await wrapper.get('[aria-label="验证截止日期"]').setValue('2026-10-30');
  await wrapper.get('[data-testid="validation-form"]').trigger('submit');
  await flushPromises();
  expect(submitValidation).toHaveBeenCalledExactlyOnceWith({
    start: '2024-10-14',
    end: '2026-10-30',
    scope: 'ALL',
    symbols: [],
    portfolioCapital: 50000,
  });
  expect(wrapper.find('[data-testid="download-validation"]').exists()).toBe(false);
  wrapper.unmount();
});

it('polls progress and persists cancel while stopping polling on terminal state and unmount', async () => {
  vi.useFakeTimers();
  const wrapper = shallowMount(ResearchValidationPanel, {
    props: { date: '2026-10-30', synthetic: false },
    global: { stubs },
  });
  await flushPromises();
  await wrapper.get('[data-testid="validation-form"]').trigger('submit');
  await flushPromises();
  await vi.advanceTimersByTimeAsync(2000);
  await flushPromises();
  expect(getValidationTask).toHaveBeenCalledWith(7);
  await wrapper.get('[data-testid="cancel-validation"]').trigger('click');
  await flushPromises();
  expect(cancelValidation).toHaveBeenCalledExactlyOnceWith(7);
  const calls = vi.mocked(getValidationTask).mock.calls.length;
  await vi.advanceTimersByTimeAsync(6000);
  expect(getValidationTask).toHaveBeenCalledTimes(calls);
  wrapper.unmount();
  await vi.advanceTimersByTimeAsync(6000);
  expect(getValidationTask).toHaveBeenCalledTimes(calls);
});

it('does not submit real research in demo mode even when the form is triggered directly', async () => {
  const wrapper = shallowMount(ResearchValidationPanel, {
    props: { date: '2026-10-30', synthetic: true },
    global: { stubs },
  });
  await flushPromises();
  expect(wrapper.get('[data-testid="submit-validation"]').attributes('disabled')).toBeDefined();
  await wrapper.get('[data-testid="validation-form"]').trigger('submit');
  await flushPromises();
  expect(submitValidation).not.toHaveBeenCalled();
  wrapper.unmount();
});

it('shows real archived samples with their own frozen dates and prepares an update without auto-submitting', async () => {
  const result = {
    dataStatus: 'OBSERVED',
    statistics: {
      '5D': {
        TREND_BREAK_20: {
          sampleCount: 6,
          triggeredCount: 14,
          dataStatus: 'SMALL_SAMPLE',
          meanReturn: 0.02,
        },
      },
    },
    validationWindows: [
      {
        label: 'HOLDOUT',
        start: '2026-03-01',
        end: '2026-10-30',
        statistics: {
          '5D': {
            TREND_BREAK_20: {
              sampleCount: 6,
              triggeredCount: 14,
              dataStatus: 'SMALL_SAMPLE',
              meanReturn: 0.02,
            },
          },
        },
      },
    ],
  };
  const completed: ValidationTask = {
    ...task('SUCCESS'),
    start: '2024-10-14',
    end: '2026-10-30',
    summary: {
      scope: 'TECHNICAL',
      start: '2024-10-14',
      end: '2026-10-30',
      protocolVersion: 'RESEARCH_AUDIT_V2',
      ruleDefinition: { ruleVersion: 'RULE_V2', technicalVersion: 'TECHNICAL_V2', parameters: {} },
      fullMarketVerdict: 'NOT_REQUESTED',
      qualifiedScoreDays: 0,
      marketResults: [],
      stocks: ['002837.SZ', '300476.SZ'].map((symbol) => ({
        symbol,
        priceDays: 498,
        firstPriceDate: '2024-10-14',
        lastPriceDate: '2026-10-30',
        savedRuleRecords: 8,
        savedRetrospectiveStatus: 'INSUFFICIENT_DATA',
        savedPointInTimeStatus: 'INSUFFICIENT_DATA',
        reconstructed: result,
      })),
      blockers: [],
      limitations: [],
      verification: {
        matched: true,
        marketWindows: 0,
        technicalStocks: 2,
        technicalVersion: 'TECHNICAL_V2',
      },
    },
  };
  vi.mocked(getValidationTasks).mockResolvedValue({
    items: [completed],
    total: 1,
    page: 1,
    size: 20,
  });
  vi.mocked(getValidationTask).mockResolvedValue(completed);
  const wrapper = shallowMount(ResearchValidationPanel, {
    props: { date: '2026-11-13', synthetic: false, preferredSymbol: '300476.SZ' },
    global: { stubs },
  });
  await flushPromises();
  expect(submitValidation).not.toHaveBeenCalled();
  expect(wrapper.get('[data-testid="validation-frozen-range"]').text()).toContain(
    '2024-10-14 至 2026-10-30',
  );
  expect(wrapper.get('[data-testid="validation-create"]').attributes('open')).toBeUndefined();
  expect(
    wrapper
      .findAllComponents(stubs['el-select'])
      .find((field) => field.attributes('aria-label') === '验证结果股票')!
      .props('modelValue'),
  ).toBe('300476.SZ');
  const dataTables = wrapper
    .get('[data-testid="validation-summary"]')
    .findAllComponents(stubs['el-table']);
  expect(dataTables.at(-1)?.props('data')[0]).toMatchObject({ sampleCount: 6, meanReturn: 0.02 });
  await wrapper
    .findAll('button')
    .find((b) => b.text().includes('使用所选股票更新'))!
    .trigger('click');
  expect(wrapper.get('[data-testid="validation-create"]').attributes('open')).toBeDefined();
  expect(wrapper.get('[aria-label="验证开始日期"]').element).toHaveProperty('value', '2024-10-14');
  expect(wrapper.get('[aria-label="验证截止日期"]').element).toHaveProperty('value', '2026-11-13');
  expect(submitValidation).not.toHaveBeenCalled();
  await wrapper.get('[data-testid="validation-form"]').trigger('submit');
  await flushPromises();
  expect(submitValidation).toHaveBeenCalledExactlyOnceWith({
    start: '2024-10-14',
    end: '2026-11-13',
    scope: 'TECHNICAL',
    symbols: ['300476.SZ'],
    portfolioCapital: 50000,
  });
  wrapper.unmount();
});

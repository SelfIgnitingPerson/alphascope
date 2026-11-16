import { it, expect, vi } from 'vitest';
import { shallowMount, flushPromises } from '@vue/test-utils';
import { defineComponent } from 'vue';
import ReportDetailView from './ReportDetailView.vue';
import { getReport, getReportVersions, compareReports } from '../api/reports';
vi.mock('../api/reports', () => ({
  getReport: vi.fn(),
  getReportVersions: vi.fn(),
  compareReports: vi.fn(),
}));
it('compares two same-day revisions and renders both values', async () => {
  const summary = {
    id: 7,
    report_type: 'DAILY' as const,
    business_date: '2026-10-30',
    revision: 2,
    status: 'SUCCESS',
    model: 'RULE_FALLBACK',
    data_as_of: '2026-10-30T15:00:00',
    is_synthetic: false,
  };
  vi.mocked(getReport).mockResolvedValue({
    ...summary,
    content_json: {
      marketState: 'UNKNOWN',
      summary: '新摘要',
      positiveFactors: [],
      negativeFactors: [],
      themeOutlook: [],
      watchlistRisks: [],
      nextChecks: [],
      uncertainties: [],
      sourceIds: [],
    },
    sources: [],
  });
  vi.mocked(getReportVersions).mockResolvedValue([{ ...summary, id: 8, revision: 1 }]);
  vi.mocked(compareReports).mockResolvedValue({
    leftId: 7,
    rightId: 8,
    inputChanged: true,
    changes: [{ field: 'summary', before: '新摘要', after: '旧摘要' }],
  });
  const Select = defineComponent({
    name: 'SelectStub',
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div><slot/></div>',
  });
  const wrapper = shallowMount(ReportDetailView, {
    props: { date: '2026-10-30', refreshToken: 0, reportId: 7 },
    global: {
      stubs: {
        SectionCard: { template: '<section><slot/></section>' },
        AsyncState: { template: '<div><slot/></div>' },
        'el-select': Select,
        'el-option': true,
        RouterLink: true,
      },
    },
  });
  await flushPromises();
  await wrapper
    .findAll('[role="tab"]')
    .find((tab) => tab.text() === '证据与版本')!
    .trigger('click');
  wrapper.findComponent(Select).vm.$emit('update:modelValue', 8);
  await flushPromises();
  expect(compareReports).toHaveBeenCalledWith(7, 8, expect.any(AbortSignal));
  expect(wrapper.text()).toContain('新摘要');
  expect(wrapper.text()).toContain('旧摘要');
  wrapper.unmount();
});
it('identifies a legacy report and links to its newer revision', async () => {
  const summary = {
    id: 15,
    report_type: 'DAILY' as const,
    business_date: '2026-10-30',
    revision: 4,
    status: 'SUCCESS',
    model: 'RULE_FALLBACK',
    data_as_of: '2026-10-30T16:10:00',
    is_synthetic: false,
    algorithm_version: 'LEGACY',
  };
  vi.mocked(getReport).mockResolvedValue({
    ...summary,
    content_json: {
      marketState: 'UNKNOWN',
      summary: '旧报告',
      positiveFactors: [],
      negativeFactors: [],
      themeOutlook: [],
      watchlistRisks: [],
      nextChecks: [],
      uncertainties: [],
      sourceIds: [],
    },
    sources: [],
  });
  vi.mocked(getReportVersions).mockResolvedValue([
    { ...summary, id: 16, revision: 5, algorithm_version: 'REPORT_V2' },
  ]);
  const wrapper = shallowMount(ReportDetailView, {
    props: { date: '2026-10-30', refreshToken: 0, reportId: 15 },
    global: {
      stubs: {
        SectionCard: { template: '<section><slot/></section>' },
        AsyncState: { template: '<div><slot/></div>' },
        RouterLink: { props: ['to'], template: '<a :href="to"><slot/></a>' },
      },
    },
  });
  await flushPromises();
  expect(wrapper.text()).toContain('历史报告修订');
  expect(wrapper.text()).toContain('升级前生成');
  expect(wrapper.find('a[href="/reports/16"]').exists()).toBe(true);
  wrapper.unmount();
});

it('loads only the chosen report section, supports keyboard tabs and resets on another report', async () => {
  vi.mocked(compareReports).mockClear();
  vi.mocked(getReport).mockResolvedValue({
    id: 99,
    report_type: 'DAILY',
    business_date: '2026-11-12',
    revision: 1,
    status: 'SUCCESS',
    model: 'RULE_FALLBACK',
    data_as_of: '2026-11-12T15:00:00',
    is_synthetic: false,
    content_json: {
      marketState: 'UNKNOWN',
      summary: '冻结文字摘要',
      positiveFactors: [],
      negativeFactors: [],
      themeOutlook: [],
      watchlistRisks: [],
      nextChecks: [],
      uncertainties: [],
      sourceIds: [],
    },
    sources: [],
  });
  vi.mocked(getReportVersions).mockResolvedValue([]);
  const wrapper = shallowMount(ReportDetailView, {
    props: { date: '2026-11-12', refreshToken: 0, reportId: 99 },
    global: {
      stubs: {
        AsyncState: { template: '<div><slot/></div>' },
        SectionCard: { template: '<section><slot/></section>' },
        RouterLink: true,
      },
    },
  });
  await flushPromises();
  expect(wrapper.findAll('[role="tab"]')).toHaveLength(5);
  expect(wrapper.get('[aria-selected="true"]').text()).toBe('重点概览');
  expect(wrapper.find('.report-evidence').exists()).toBe(false);
  await wrapper.get('[role="tablist"]').trigger('keydown', { key: 'End' });
  expect(wrapper.get('[aria-selected="true"]').text()).toBe('证据与版本');
  expect(wrapper.find('.report-evidence').exists()).toBe(true);
  await wrapper.get('[role="tablist"]').trigger('keydown', { key: 'ArrowLeft' });
  expect(wrapper.get('[aria-selected="true"]').text()).toBe('报告正文');
  expect(wrapper.find('.report-evidence').exists()).toBe(false);
  expect(wrapper.get('.report-document__content').text()).toContain('冻结文字摘要');
  await wrapper.setProps({ reportId: 100 });
  expect(wrapper.find('.report-document').exists()).toBe(false);
  await flushPromises();
  expect(wrapper.get('[aria-selected="true"]').text()).toBe('重点概览');
  expect(compareReports).not.toHaveBeenCalled();
  wrapper.unmount();
});

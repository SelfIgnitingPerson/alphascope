import { shallowMount, flushPromises } from '@vue/test-utils';
import { beforeEach, expect, it, vi } from 'vitest';
import ThemeDetailView from './ThemeDetailView.vue';
import { getThemes, getThemeStrength, getIndustryMetrics } from '../api/market';
import type { ThemeStrength } from '../types/models';
vi.mock('../api/market', () => ({
  getThemes: vi.fn(),
  getThemeStrength: vi.fn(),
  getIndustryMetrics: vi.fn(),
}));
const sample: ThemeStrength = {
  members: [{ symbol: '300476.SZ', name: '胜宏科技' }],
  algorithmVersion: 'EQUAL_RETURN_V1',
  industryCoverage: 'UNKNOWN',
  items: [{ trade_date: '2026-11-10', score: 3.1, rs20: -0.23, coverage: 1, breadth: 0 }],
  cohort: {
    title: 'PCB披露样本',
    expected_members: 1,
    source_title: '原始公开依据',
    source_url: 'https://example.com/source.pdf',
    source_published_at: '2026-03-21T00:00:00',
    source_fetched_at: '2026-11-01T12:00:00',
    available_at: '2026-03-22T00:00:00',
    effective_from: '2026-03-22',
    definition_json: '{"definition":"仅为固定PCB制造样本","sourcePages":"第33—35页"}',
  },
};
function create() {
  return shallowMount(ThemeDetailView, {
    props: { date: '2026-11-12', refreshToken: 0, code: 'PCB' },
    global: {
      stubs: {
        RouterLink: true,
        ElTable: true,
        ElTableColumn: true,
        ElPagination: true,
        ElButton: true,
        ElSkeleton: true,
        SectionTabs: false,
        AsyncState: false,
        ThemeObservation: false,
        SectionCard: { template: '<section><slot name="actions"/><slot/></section>' },
      },
    },
  });
}
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getThemes).mockResolvedValue([{ id: 1, code: 'PCB', name: 'PCB/CCL' }]);
  vi.mocked(getThemeStrength).mockResolvedValue(sample);
  vi.mocked(getIndustryMetrics).mockResolvedValue([]);
});
it('explains sample scope, old observations and original source separately from missing industry metrics', async () => {
  const wrapper = create();
  await flushPromises();
  expect(wrapper.text()).toContain('所选日期 2026-11-12');
  expect(wrapper.text()).toContain('最近保存的观测为 2026-11-10');
  expect(wrapper.text()).toContain('不能据此判断整个行业');
  await wrapper.findAll('[role="tab"]')[2].trigger('click');
  expect(wrapper.get('a[href="https://example.com/source.pdf"]').text()).toContain('原始公开依据');
  expect(wrapper.text()).toContain('仅为固定PCB制造样本');
  expect(wrapper.text()).toContain('第33—35页');
  expect(wrapper.text()).toContain('尚未接入该主题的独立产业景气指标');
  wrapper.unmount();
});
it('isolates industry metric failures and blocks unsafe source links', async () => {
  vi.mocked(getIndustryMetrics).mockRejectedValue(Error('产业指标暂不可读'));
  vi.mocked(getThemeStrength).mockResolvedValue({
    ...sample,
    cohort: { ...sample.cohort!, source_url: 'javascript:alert(1)', definition_json: '{invalid' },
  });
  const wrapper = create();
  await flushPromises();
  expect(wrapper.text()).toContain('3.1');
  expect(wrapper.text()).toContain('产业指标暂不可读');
  expect(wrapper.text()).toContain('原始定义未保存范围说明');
  expect(wrapper.find('a[href^="javascript:"]').exists()).toBe(false);
  wrapper.unmount();
});

it('explains an empty classification without presenting a zero score or fabricated members', async () => {
  vi.mocked(getThemeStrength).mockResolvedValue({
    members: [],
    items: [],
    algorithmVersion: 'EQUAL_RETURN_V1',
  });
  const wrapper = create();
  await flushPromises();
  expect(wrapper.text()).toContain('该主题尚未建立有效研究样本');
  expect(wrapper.findComponent({ name: 'ThemeObservation' }).exists()).toBe(false);
  expect(wrapper.text()).toContain('需要补充公开样本依据');
  wrapper.unmount();
});

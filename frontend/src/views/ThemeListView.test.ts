import { shallowMount, flushPromises } from '@vue/test-utils';
import { beforeEach, expect, it, vi } from 'vitest';
import ThemeListView from './ThemeListView.vue';
import { getThemes, getThemeStrength } from '../api/market';
import type { ThemeStrength } from '../types/models';
import { getThemeLeadersCatalog } from '../api/theme-leaders';
vi.mock('../api/theme-leaders', () => ({ getThemeLeadersCatalog: vi.fn() }));
vi.mock('../api/market', () => ({ getThemes: vi.fn(), getThemeStrength: vi.fn() }));
const sample: ThemeStrength = {
  members: [{ symbol: '300476.SZ', name: '胜宏科技' }],
  algorithmVersion: 'EQUAL_RETURN_V1',
  scope: 'DISCLOSED_RESEARCH_SAMPLE',
  industryCoverage: 'UNKNOWN',
  items: [
    { trade_date: '2026-11-12', score: 0, rs20: -0.2, coverage: 1, breadth: 0, day_return: -0.01 },
  ],
};
function create() {
  return shallowMount(ThemeListView, {
    props: { date: '2026-11-12', refreshToken: 0 },
    global: {
      stubs: {
        ElButton: true,
        ElSkeleton: true,
        ElEmpty: true,
        AsyncState: false,
        StateBadge: false,
        ThemeObservation: false,
        RouterLink: { props: ['to'], template: '<a :href="to"><slot/></a>' },
      },
    },
  });
}
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getThemeLeadersCatalog).mockResolvedValue([]);
  vi.mocked(getThemes).mockResolvedValue([
    { id: 1, code: 'PCB', name: 'PCB/CCL' },
    { id: 2, code: 'CPO', name: '光模块/CPO' },
  ]);
  vi.mocked(getThemeStrength).mockImplementation(async (code) =>
    code === 'PCB' ? sample : { members: [], items: [], algorithmVersion: 'EQUAL_RETURN_V1' },
  );
});
it('shows current core enterprise names independently of whether a historical cohort exists', async () => {
  vi.mocked(getThemeLeadersCatalog).mockResolvedValue([
    {
      themeCode: 'CPO',
      title: '光互联核心龙头',
      marketAlias: '易中天',
      reviewedAt: '2026-11-16',
      selectionBasis: '核心产业位置',
      limitation: '不倒填历史',
      members: ['中际旭创', '新易盛', '天孚通信'].map((name, i) => ({
        displayOrder: i + 1,
        symbol: ['300308.SZ', '300502.SZ', '300394.SZ'][i]!,
        name,
        tier: '核心龙头',
        segment: '光互联',
        standing: '细分头部',
        rationale: '原始资料',
        formalRanking: null,
        sources: [],
      })),
    },
  ]);
  const wrapper = create();
  await flushPromises();
  const optical = wrapper.get('a[href="/themes/CPO"]');
  expect(optical.text()).toContain('中际旭创、新易盛、天孚通信');
  expect(optical.text()).toContain('尚未建立样本');
  expect(getThemeStrength).toHaveBeenCalledTimes(2);
  wrapper.unmount();
});
it('shows stored sample metrics including true zero and separates empty classifications', async () => {
  const wrapper = create();
  await flushPromises();
  const pcb = wrapper.get('a[href="/themes/PCB"]');
  expect(pcb.text()).toContain('胜宏科技');
  expect(pcb.text()).toContain('0.0');
  expect(pcb.text()).toContain('-20.00%');
  expect(pcb.text()).toContain('100.0%');
  expect(pcb.text()).toContain('完整行业覆盖未核实');
  expect(wrapper.get('a[href="/themes/CPO"]').text()).toContain('尚未建立样本');
  expect(wrapper.text()).toContain('已有样本研究 1 个主题');
  expect(getThemeStrength).toHaveBeenCalledWith(
    'PCB',
    { start: '2026-08-14', end: '2026-11-12' },
    expect.any(AbortSignal),
  );
  wrapper.unmount();
});
it('keeps other themes available when one theme read fails and never describes failure as zero members', async () => {
  vi.mocked(getThemeStrength).mockImplementation(async (code) => {
    if (code === 'CPO') throw Error('读取暂时失败');
    return sample;
  });
  const wrapper = create();
  await flushPromises();
  expect(wrapper.get('a[href="/themes/PCB"]').text()).toContain('胜宏科技');
  const failed = wrapper.get('a[href="/themes/CPO"]');
  expect(failed.text()).toContain('读取失败');
  expect(failed.text()).toContain('读取暂时失败');
  expect(failed.text()).not.toContain('当前有效成员为0');
  wrapper.unmount();
});

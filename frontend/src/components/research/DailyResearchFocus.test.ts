import { expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import DailyResearchFocus from './DailyResearchFocus.vue';
import { getResearchFocus } from '../../api/research-focus';
vi.mock('../../api/research-focus', () => ({ getResearchFocus: vi.fn() }));
it('shows only the user page, filters without new requests and preserves research date and horizon in links', async () => {
  vi.mocked(getResearchFocus).mockResolvedValue({
    asOf: '2026-11-17',
    page: 1,
    size: 10,
    total: 12,
    items: [
      {
        symbol: '600000.SH',
        name: '风险样本',
        category: 'RISK',
        title: '优先复核减仓或退出',
        explanation: '中期风险',
        horizon: 'MEDIUM',
        quoteDate: '2026-11-17',
        comparisonDate: '2026-11-16',
        newlyMatched: false,
      },
      {
        symbol: '600001.SH',
        name: '缺项样本',
        category: 'DATA_GAP',
        title: '所选日行情待补',
        explanation: '不能把旧行情用于本日判断',
        horizon: null,
        quoteDate: '2026-11-16',
        comparisonDate: null,
        newlyMatched: false,
      },
    ],
  });
  const wrapper = mount(DailyResearchFocus, {
    props: { date: '2026-11-17', refreshToken: 0 },
    global: {
      stubs: {
        RouterLink: { props: ['to'], template: '<a :data-to="JSON.stringify(to)"><slot/></a>' },
        ElSkeleton: true,
        ElButton: {
          emits: ['click'],
          props: ['disabled'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot/></button>',
        },
      },
    },
  });
  try {
    await flushPromises();
    expect(getResearchFocus).toHaveBeenLastCalledWith(
      { asOf: '2026-11-17', page: 1, size: 10 },
      expect.any(AbortSignal),
    );
    expect(wrapper.text()).toContain('2 / 12');
    expect(wrapper.text()).toContain('事项仅在本页按风险排序');
    const link = wrapper.get('.focus-item a').attributes('data-to');
    expect(link).toContain('2026-11-17');
    expect(link).toContain('MEDIUM');
    await wrapper.findAll('.focus-filters button')[1]!.trigger('click');
    expect(wrapper.findAll('.focus-item')).toHaveLength(1);
    expect(wrapper.text()).not.toContain('缺项样本');
    expect(getResearchFocus).toHaveBeenCalledTimes(1);
    await wrapper.findAll('.focus-pagination button')[1]!.trigger('click');
    await flushPromises();
    expect(getResearchFocus).toHaveBeenLastCalledWith(
      { asOf: '2026-11-17', page: 2, size: 10 },
      expect.any(AbortSignal),
    );
  } finally {
    wrapper.unmount();
  }
});

it('keeps the first three items readable, expands remaining risks and separates weak observations without refetching', async () => {
  vi.clearAllMocks();
  vi.mocked(getResearchFocus).mockResolvedValue({
    asOf: '2026-11-17',
    page: 1,
    size: 10,
    total: 5,
    items: Array.from({ length: 5 }, (_, i) => ({
      symbol: `60000${i}.SH`,
      name: `样本${i}`,
      category: 'RISK',
      title: `风险${i}`,
      explanation: '已保存证据',
      horizon: 'MEDIUM',
      quoteDate: '2026-11-17',
      comparisonDate: '2026-11-16',
      newlyMatched: false,
      changeLabel: '上期也有此信号',
      priorities: [
        {
          code: i < 2 ? 'STRUCTURE_RISK' : 'TREND_WATCH',
          level: i < 2 ? ('FIRST' as const) : ('WATCH' as const),
          title: `风险${i}`,
          explanation: '事实解释',
          horizon: 'MEDIUM',
          actionCode: 'REDUCE',
          facts: ['收盘与参考对比'],
          nextChecks: ['核对原风险边界'],
        },
      ],
    })),
  });
  const w = mount(DailyResearchFocus, {
    props: { date: '2026-11-17', refreshToken: 0 },
    global: { stubs: { RouterLink: true, ElSkeleton: true } },
  });
  try {
    await flushPromises();
    expect(w.findAll('.focus-item')).toHaveLength(3);
    expect(w.get('.focus-expand').text()).toContain('其余2项');
    await w.get('.focus-expand').trigger('click');
    expect(w.findAll('.focus-item')).toHaveLength(5);
    await w.findAll('.focus-filters button')[1]!.trigger('click');
    expect(w.findAll('.focus-item')).toHaveLength(2);
    expect(w.text()).not.toContain('样本4');
    await w.findAll('.focus-filters button')[2]!.trigger('click');
    expect(w.findAll('.focus-item')).toHaveLength(3);
    expect(w.text()).toContain('样本4');
    expect(getResearchFocus).toHaveBeenCalledTimes(1);
  } finally {
    w.unmount();
  }
});

import { beforeEach, expect, it, vi } from 'vitest';
import { shallowMount, flushPromises } from '@vue/test-utils';
import ThemeLeaderPanel from './ThemeLeaderPanel.vue';
import { getThemeLeaders } from '../../api/theme-leaders';
import type { ThemeLeaders } from '../../types/theme-leaders';

vi.mock('../../api/theme-leaders', () => ({ getThemeLeaders: vi.fn() }));
const sample: ThemeLeaders = {
  themeCode: 'CPO',
  title: '光互联核心龙头',
  marketAlias: '易中天：组合称呼不是名次',
  reviewedAt: '2026-11-16',
  selectionBasis: '按产业角色优先展示核心企业',
  limitation: '不同细分不混排，不倒填历史。',
  members: [
    {
      displayOrder: 1,
      symbol: '300308.SZ',
      name: '中际旭创',
      tier: '核心龙头',
      segment: '光模块',
      standing: '规模头部',
      rationale: '已披露业务与行业资料',
      formalRanking: null,
      sources: [{ title: '原始行业资料', url: 'https://example.com/industry' }],
    },
    {
      displayOrder: 2,
      symbol: '300394.SZ',
      name: '天孚通信',
      tier: '核心龙头',
      segment: '光器件',
      standing: '上游光器件核心',
      rationale: '不能标为第三大光模块厂商',
      formalRanking: null,
      sources: [{ title: '原始年报', url: 'https://example.com/annual.pdf' }],
    },
  ],
};
const create = () =>
  shallowMount(ThemeLeaderPanel, {
    props: { code: 'CPO', researchDate: '2026-11-13', refreshToken: 0 },
    global: {
      stubs: {
        SectionCard: { template: '<section><slot/></section>' },
        AsyncState: false,
        RouterLink: true,
      },
    },
  });
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getThemeLeaders).mockResolvedValue(sample);
});

it('shows named core companies, original evidence and current rather than historical knowledge dates', async () => {
  const wrapper = create();
  await flushPromises();
  expect(wrapper.findAll('.leader-card').map((card) => card.attributes('data-symbol'))).toEqual([
    '300308.SZ',
    '300394.SZ',
  ]);
  expect(wrapper.text()).toContain('易中天');
  expect(wrapper.text()).toContain('后来整理的当前目录');
  expect(wrapper.text()).toContain('不能标为第三大光模块厂商');
  expect(wrapper.get('a[href="https://example.com/industry"]').text()).toBe('原始行业资料');
  const calls = vi.mocked(getThemeLeaders).mock.calls.length;
  await wrapper.setProps({ researchDate: '2026-11-17' });
  expect(wrapper.text()).not.toContain('后来整理的当前目录');
  expect(getThemeLeaders).toHaveBeenCalledTimes(calls);
  wrapper.unmount();
});

it('does not expose unsafe source links and preserves explicit lack of formal rankings', async () => {
  vi.mocked(getThemeLeaders).mockResolvedValue({
    ...sample,
    members: [
      { ...sample.members[0]!, sources: [{ title: '待核对来源', url: 'javascript:alert(1)' }] },
    ],
  });
  const wrapper = create();
  await flushPromises();
  expect(wrapper.find('a[href^="javascript:"]').exists()).toBe(false);
  expect(wrapper.text()).toContain('链接不可用');
  expect(wrapper.text()).not.toContain('来源中的明确名次');
  wrapper.unmount();
});

it('clears the prior sector when the new sector directory fails', async () => {
  const wrapper = create();
  await flushPromises();
  vi.mocked(getThemeLeaders).mockRejectedValue(Error('该板块目录暂不可读'));
  await wrapper.setProps({ code: 'PCB' });
  await flushPromises();
  expect(wrapper.find('[data-testid="industry-leaders"]').exists()).toBe(false);
  expect(wrapper.text()).toContain('该板块目录暂不可读');
  wrapper.unmount();
});

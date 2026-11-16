import { expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import ThemeLinkageCard from './ThemeLinkageCard.vue';
import ResearchAdviceCard from './ResearchAdviceCard.vue';
import type { ThemeResearch, ResearchAdvice } from '../../types/models';

it('shows peer denominators, missing correlation and the statistical analogy boundary', () => {
  const window = {
    lookback: 20,
    samples: 20,
    expectedPeers: 5,
    peers: ['002463.SZ', '002916.SZ', '002815.SZ', '001389.SZ', '688183.SH'],
    coverage: 1,
    correlation: null,
    marketAdjustedCorrelation: null,
    stockReturn: -0.3,
    peerReturn: -0.1,
    excessReturn: -0.2,
    peerDayReturn: -0.02,
    status: 'PARTIAL',
  };
  const research: ThemeResearch = {
    symbol: '300476.SZ',
    themeCode: 'PCB',
    title: 'PCB制造披露样本',
    scope: 'DISCLOSED_RESEARCH_SAMPLE',
    status: 'PARTIAL',
    source: {
      title: '原始披露',
      url: 'https://static.cninfo.com.cn/finalpage/example.PDF',
      pages: 'PDF第33–35页',
      availableAt: '2026-03-22T00:00:00',
      contentHash: 'hash',
      definition: '固定样本，行业覆盖率未知',
    },
    members: [{ symbol: '300476.SZ', name: '胜宏科技' }],
    recentDays: [
      {
        date: '2026-10-30',
        dayReturn: -0.02,
        breadth: 0.5,
        coverage: 1,
        validMembers: 6,
        totalMembers: 6,
      },
    ],
    window20: window,
    window60: { ...window, lookback: 60, samples: 60 },
    observation: '个股跑输同业',
    hypotheses: [],
    checks: [],
    limitations: ['相关性不代表因果'],
  };
  const wrapper = mount(ThemeLinkageCard, { props: { research } });
  expect(wrapper.text()).toContain('20日 5/5；60日 5/5');
  expect(wrapper.text()).toContain('同业组合已排除 300476.SZ 自身');
  expect(wrapper.findAll('dd')[0].text()).toBe('—');
  expect(wrapper.text()).toContain('不声称测得股票之间的物理量子纠缠');
  expect(wrapper.find('a').attributes('href')).toBe(research.source.url);
  wrapper.unmount();
});

it('keeps facts and hypotheses distinct and gives concrete verification conditions', () => {
  const advice: ResearchAdvice = {
    symbol: '300476.SZ',
    name: '胜宏科技',
    priority: 'REVIEW_FIRST',
    conclusion: '趋势与盈利质量均有待核查',
    observations: ['毛利率下降3.0011个百分点'],
    hypotheses: ['产品结构变化，尚待验证'],
    verifyNext: ['核对分产品收入与成本'],
    reconsiderIf: ['动态MA20不再下降且连续两日站回均线'],
  };
  const wrapper = mount(ResearchAdviceCard, { props: { advice } });
  expect(wrapper.text()).toContain('优先核查');
  expect(wrapper.text()).toContain('已观察到的事实');
  expect(wrapper.text()).toContain('可能解释，尚待验证');
  expect(wrapper.find('ol').text()).toContain('核对分产品收入与成本');
  expect(wrapper.text()).toContain('哪些变化会调整判断');
  wrapper.unmount();
});

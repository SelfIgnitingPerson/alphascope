import { it, expect } from 'vitest';
import { shallowMount } from '@vue/test-utils';
import ReportFacts from './ReportFacts.vue';
import type { ReportAnalysis } from '../../types/models';
function fixture(): ReportAnalysis {
  return {
    version: 'FROZEN_FACTS_V1',
    status: 'AVAILABLE',
    headline: '1只关注标的，1项规则记录可评估，1项已触发。',
    benchmarkReturn: -0.01,
    stocks: [
      {
        symbol: '300476.SZ',
        name: '胜宏科技',
        asOf: '2026-10-30',
        status: 'TRADING',
        source: 'BAOSTOCK',
        batchId: 'frozen-batch',
        close: 139.04,
        dayReturn: -0.1331,
        periodReturn: -0.1331,
        return5: -0.2,
        return20: -0.3,
        ma20: 188.062,
        ma60: 223.812,
        distanceMa20: -0.2607,
        distanceMa60: -0.3788,
        volumeRatio: 2.465,
        rs20: -0.3345,
        previous20Low: 160.38,
        trend: '低于20日和60日均线',
        triggered: 1,
        reviewed: 1,
        insufficient: 1,
      },
    ],
    assessments: [
      {
        symbol: '300476.SZ',
        name: '胜宏科技',
        date: '2026-10-30',
        ruleCode: 'VOLUME_SELL',
        ruleName: '放量下跌',
        status: 'TRIGGERED',
        severity: 'HIGH',
        evidence: [
          { label: '当日复权收益', value: -0.1331, unit: 'PERCENT' },
          { label: '量比', value: 2.465, unit: 'TIMES' },
        ],
        missingInputs: [],
      },
      {
        symbol: '300476.SZ',
        name: '胜宏科技',
        date: '2026-10-30',
        ruleCode: 'PROFIT_SLOWDOWN',
        ruleName: '利润增速放缓',
        status: 'INSUFFICIENT_DATA',
        severity: 'MEDIUM',
        evidence: [],
        missingInputs: ['营收同比', '利润同比'],
      },
    ],
    checks: [
      { symbol: '300476.SZ', title: '核对下一期趋势', detail: '比较新收盘与更新后的20日均线。' },
    ],
    missingData: ['未保存可用财报'],
    methodNotes: ['仅基于本修订冻结证据。'],
  };
}
it('shows useful metrics and actual evidence without empty generic sections', () => {
  const wrapper = shallowMount(ReportFacts, {
    props: { analysis: fixture(), weekly: false },
    global: { stubs: { StateBadge: false, TermHelp: false, HelpPopover: false, RouterLink: true } },
  });
  expect(wrapper.text()).toContain('139.04');
  expect(wrapper.text()).toContain('188.06');
  expect(wrapper.text()).toContain('放量下跌');
  expect(wrapper.text()).toContain('量比');
  expect(wrapper.text()).toContain('核对下一期趋势');
  expect(wrapper.text()).not.toContain('本次没有可列示事项');
  wrapper.unmount();
});
it('keeps missing evidence separate from triggered risks', () => {
  const wrapper = shallowMount(ReportFacts, { props: { analysis: fixture(), weekly: false } });
  expect(wrapper.find('.report-other-rules').text()).toContain('缺少：营收同比、利润同比');
  expect(wrapper.findAll('.report-rule-row').length).toBe(2);
  expect(wrapper.find('.report-data-gaps').text()).toContain('未保存可用财报');
  wrapper.unmount();
});
it('labels weekly returns separately from daily returns', () => {
  const wrapper = shallowMount(ReportFacts, { props: { analysis: fixture(), weekly: true } });
  expect(wrapper.text()).toContain('本周复权收益');
  expect(wrapper.text()).toContain('沪深300本周');
  wrapper.unmount();
});

it('explains member and history gaps instead of listing missing theme fields alone', () => {
  const analysis = fixture();
  analysis.assessments.push({
    symbol: '300476.SZ',
    name: '胜宏科技',
    date: '2026-10-30',
    ruleCode: 'INDUSTRY_DIVERGENCE',
    ruleName: '主题走势分化',
    status: 'INSUFFICIENT_DATA',
    severity: 'MEDIUM',
    evidence: [],
    missingInputs: ['主题收益', '主题广度'],
    explanation:
      'PCB/CCL：当前登记1只成员（至少2只）；已保存1/3天记录；缺少2026-10-28、2026-10-29。',
  });
  const wrapper = shallowMount(ReportFacts, { props: { analysis, weekly: false } });
  expect(wrapper.text()).toContain('当前登记1只成员（至少2只）');
  expect(wrapper.text()).toContain('已保存1/3天记录');
  expect(wrapper.text()).not.toContain('缺少：主题收益、主题广度');
  wrapper.unmount();
});

it('shows financial facts and scoped market data while marking personal holdings inapplicable', () => {
  const analysis = fixture();
  analysis.researchMode = 'WATCHLIST';
  analysis.financials = [
    {
      symbol: '300476.SZ',
      name: '胜宏科技',
      periodEnd: '2026-06-30',
      reportType: 'H1_CUMULATIVE',
      publishedAt: '2026-08-28T00:00:00',
      availableAt: '2026-08-29T00:00:00',
      source: 'EASTMONEY_FREE',
      revenue: 100000000,
      netProfit: 10000000,
      operatingCashflow: -20000000,
      grossMargin: 0.3,
      revenueYoy: 0.25,
      profitYoy: 0.04,
    },
  ];
  analysis.market = {
    status: 'COMPLETE',
    scope: 'SH_SZ_A',
    source: 'BAOSTOCK',
    amount: 20000000000,
    advancers: 2000,
    decliners: 3000,
    unchanged: 100,
    suspended: 20,
    expected: 5120,
    observed: 5100,
    coverage: 1,
  };
  analysis.assessments.push({
    symbol: '300476.SZ',
    name: '胜宏科技',
    date: '2026-10-30',
    ruleCode: 'POSITION_CONCENTRATION',
    ruleName: '持仓集中度偏高',
    status: 'NOT_APPLICABLE',
    severity: 'HIGH',
    evidence: [],
    missingInputs: [],
  });
  const wrapper = shallowMount(ReportFacts, {
    props: { analysis, weekly: false },
    global: { stubs: { StateBadge: false, TermHelp: false, HelpPopover: false, RouterLink: true } },
  });
  expect(wrapper.text()).toContain('自选研究模式');
  expect(wrapper.text()).toContain('不评估个人持仓风险');
  expect(wrapper.text()).toContain('不适用');
  expect(wrapper.text()).toContain('2026-06-30');
  expect(wrapper.text()).toContain('累计经营现金流');
  expect(wrapper.text()).toContain('沪深A股当日市场截面');
  expect(wrapper.text()).toContain('不含北交所');
  wrapper.unmount();
});

it('keeps the overview compact and opens one selected stock without mixing the market section', async () => {
  const analysis = fixture();
  analysis.stocks.push({ ...analysis.stocks[0]!, symbol: '002837.SZ', name: '英维克' });
  const wrapper = shallowMount(ReportFacts, {
    props: { analysis, weekly: false, section: 'overview' },
  });
  expect(wrapper.find('.report-focus').exists()).toBe(true);
  expect(wrapper.findAll('.report-stock-overview__grid button')).toHaveLength(2);
  expect(wrapper.findAll('article.report-stock-fact')).toHaveLength(0);
  await wrapper.findAll('.report-stock-overview__grid button')[1]!.trigger('click');
  expect(wrapper.emitted('selectStock')?.[0]).toEqual(['002837.SZ']);
  await wrapper.setProps({ section: 'stocks', symbol: '002837.SZ' });
  expect(wrapper.findAll('article.report-stock-fact')).toHaveLength(1);
  expect(wrapper.get('article.report-stock-fact h3').text()).toContain('英维克');
  expect(wrapper.find('.report-focus').exists()).toBe(false);
  wrapper.unmount();
});

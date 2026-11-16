import { it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import MarketObservationCard from './MarketObservationCard.vue';
import ForecastCard from './ForecastCard.vue';
import type { MarketObservation, ForecastView } from '../../types/models';
it('shows market scope, actual denominators and direction without inventing a total', () => {
  const observation: MarketObservation = {
    version: 'SH_SZ_OBSERVATION_V1',
    businessDate: '2026-10-30',
    scope: 'SH_SZ_A',
    status: 'AVAILABLE',
    trend: {
      status: 'AVAILABLE',
      judgment: '趋势偏弱',
      close: 100,
      ma20: 110,
      ma60: 120,
      ma20Slope: -1,
      dayReturn: -0.02,
      explanation: '基准沪深300',
    },
    turnover: {
      status: 'AVAILABLE',
      judgment: '放量下跌',
      amount: 2e8,
      average5: 1e8,
      average20: 1e8,
      ratio5: 2,
      ratio20: 2,
      completeDays: 20,
      explanation: '方向采用指数收益',
    },
    breadth: {
      status: 'AVAILABLE',
      judgment: '多数有效样本低于MA20',
      trading: 5000,
      suspended: 5,
      valid20: 4950,
      above20: 990,
      coverage20: 0.99,
      ratio20: 0.2,
      valid60: 4900,
      above60: 980,
      coverage60: 0.98,
      ratio60: 0.2,
      advancingRatio: 0.15,
      explanation: '缺失不填0',
    },
    parameters: {
      minimumCoverage: 0.98,
      turnoverExpansion: 1.1,
      turnoverContraction: 0.9,
      breadthStrong: 0.6,
      breadthWeak: 0.4,
    },
    limitations: ['不合成全A总分'],
  };
  const wrapper = mount(MarketObservationCard, { props: { observation } });
  expect(wrapper.text()).toContain('不含北交所');
  expect(wrapper.text()).toContain('放量下跌');
  expect(wrapper.text()).toContain('4950 / 5000');
  expect(wrapper.text()).toContain('20.00%');
  expect(wrapper.text()).toContain('20/20 天');
  wrapper.unmount();
});
const forecast = (): ForecastView => ({
  symbol: '300476.SZ',
  name: '胜宏科技',
  status: 'AVAILABLE',
  latest: {
    id: 1,
    availableAt: '2026-11-01T19:00:00',
    fetchedAt: '2026-11-01T19:00:00',
    source: 'EASTMONEY_PUBLIC',
    sourceUrl: 'https://data.eastmoney.com/report/profitforecast.jshtml',
    ratingAgencies: 7,
    estimates: [{ year: 2026, eps: 8.174 }],
  },
  changes: [],
  explanation: '尚无同年可比历史快照',
  limitations: ['从实际采集时刻起可用，不倒填旧报告'],
  collection: {},
});
it('labels forecasts and first observation separately from reported facts', () => {
  const wrapper = mount(ForecastCard, { props: { forecast: forecast() } });
  expect(wrapper.text()).toContain('2026年预测EPS');
  expect(wrapper.text()).toContain('8.174');
  expect(wrapper.text()).toContain('2026-11-01 19:00:00');
  expect(wrapper.text()).toContain('不倒填旧报告');
  expect(wrapper.text()).toContain('近六个月评级机构 7 家');
  wrapper.unmount();
});
it('keeps an as-of gap empty instead of displaying the current forecast', () => {
  const item = forecast();
  item.latest = null;
  item.status = 'NOT_OBSERVED_AS_OF';
  item.explanation = '本时点之前尚无已保存预测';
  const wrapper = mount(ForecastCard, { props: { forecast: item } });
  expect(wrapper.text()).toContain('本时点之前尚无已保存预测');
  expect(wrapper.text()).not.toContain('8.174');
  expect(wrapper.findAll('.forecast-estimates').length).toBe(0);
  wrapper.unmount();
});

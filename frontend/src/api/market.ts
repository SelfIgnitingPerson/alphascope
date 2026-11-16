import { api, query } from './client';
import type { DateRangeQuery, SignalStatisticsQuery } from '../types/requests';
import type {
  BarResponse,
  Indicator,
  Signal,
  Financial,
  Instrument,
  MarketOverview,
  ProviderStatus,
  Theme,
  ThemeStrength,
  IndustryMetric,
  BacktestResult,
  ForecastView,
} from '../types/models';
const instrumentPath = (symbol: string) => `/instruments/${encodeURIComponent(symbol)}`;
/** 读取截止日期已保存的机构预测；刷新页面不会触发上游采集。 */
export const getExpectations = (asOf: string, signal?: AbortSignal) =>
  api<ForecastView[]>(`/expectations?${query({ asOf })}`, { signal });
/** 读取所选业务日已有市场观察，页面请求不会触发上游回补。 */
export const getOverview = (date: string, signal?: AbortSignal) =>
  api<MarketOverview>(`/market/overview?${query({ date })}`, { signal });
/** 读取通道当前状态与已保存行情来源；状态查询不探测网络。 */
export const getProviders = (signal?: AbortSignal) =>
  api<ProviderStatus>('/market/providers', { signal });
/** 按公开标的代码查询主数据，内部数据库ID不参与页面路由。 */
export const getInstrument = (symbol: string, signal?: AbortSignal) =>
  api<Instrument>(instrumentPath(symbol), { signal });
/** 读取同源原价和复权因子窗口；保留价格、量额单位及原采集时间。 */
export const getBars = (symbol: string, range: DateRangeQuery, signal?: AbortSignal) =>
  api<BarResponse>(`${instrumentPath(symbol)}/bars?${query({ ...range })}`, { signal });
/** 读取已计算的历史技术指标，空指标表示证据不足。 */
export const getIndicators = (symbol: string, range: DateRangeQuery, signal?: AbortSignal) =>
  api<Indicator[]>(`${instrumentPath(symbol)}/indicators?${query({ ...range })}`, { signal });
/** 读取截至所选日期已经保存的规则评估，不把未触发与证据不足混为一类。 */
export const getSignals = (symbol: string, end: string, signal?: AbortSignal) =>
  api<Signal[]>(`${instrumentPath(symbol)}/signals?${query({ end })}`, { signal });
/** 按业务时点读取可用累计财报，当前修订不倒填旧报告。 */
export const getFinancials = (symbol: string, asOf: string, signal?: AbortSignal) =>
  api<{ items: Financial[] }>(`${instrumentPath(symbol)}/financials?${query({ asOf })}`, {
    signal,
  });
/** 读取固定主题字典。 */
export const getThemes = (signal?: AbortSignal) => api<Theme[]>('/themes', { signal });
/** 读取主题的历史有效成员与等权收益、广度和覆盖率。 */
export const getThemeStrength = (code: string, range: DateRangeQuery, signal?: AbortSignal) =>
  api<ThemeStrength>(`/themes/${encodeURIComponent(code)}/strength?${query({ ...range })}`, {
    signal,
  });
/** 读取带公开来源、发布时间及单位的产业指标。 */
export const getIndustryMetrics = (code: string, asOf: string, signal?: AbortSignal) =>
  api<IndustryMetric[]>(`/themes/${encodeURIComponent(code)}/metrics?${query({ asOf })}`, {
    signal,
  });
/** 查询已保存信号的基础后续收益统计，不模拟可成交性。 */
export const getBacktest = (request: SignalStatisticsQuery, signal?: AbortSignal) =>
  api<BacktestResult>(`/backtest/signals?${query({ ...request })}`, { signal });

/** 运行透明评分公式，仅读已存事实；缺项不会补造全A总分。 */
export const getResearchScore = (date: string, signal?: AbortSignal) =>
  api<import('../types/scoring').ResearchScore>(`/market/research-score?${query({ date })}`, {
    signal,
  });

/** 核对实际保存的市场清单，不通过刷新回补或伪造全A覆盖。 */
export const getUniverseCoverage = (date: string, signal?: AbortSignal) =>
  api<import('../types/validation').UniverseCoverage>(`/market/coverage?${query({ date })}`, {
    signal,
  });

/** 只读本机BaoStock预算与保护状态，不触发供应商请求。 */
export const getBaoStockPolicy = (signal?: AbortSignal) =>
  api<import('../types/market').BaoStockPolicy>('/market/providers/baostock-policy', { signal });

/** 多周期买卖研究只读当前保存版本，不采集、不写入或改写冻结报告。 */
export const getHoldingResearch = (symbol: string, asOf: string, signal?: AbortSignal) =>
  api<import('../types/holding-research').HoldingResearch>(
    `${instrumentPath(symbol)}/holding-research?${query({ asOf })}`,
    { signal },
  );

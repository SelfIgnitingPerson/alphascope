import { api, write } from './client';
import type { PositionInput } from '../types/requests';
import type { WatchItem, PositionsResponse } from '../types/models';
/** 读取当前自选研究范围。 */
export const getWatchlist = (signal?: AbortSignal) => api<WatchItem[]>('/watchlist', { signal });
/** 添加或恢复启用关注标的；不建立真实持仓。 */
export const addWatch = (symbol: string) =>
  api<null>('/watchlist', write('POST', { symbol, priority: 0 }));
/** 移除关注标的；不改写已冻结报告。 */
export const removeWatch = (symbol: string) =>
  api<null>(`/watchlist/${encodeURIComponent(symbol)}`, write('DELETE'));
/** 读取当前手工持仓和资产总额，数据未知时不推算集中度。 */
export const getPositions = (signal?: AbortSignal, asOf?: string) =>
  api<PositionsResponse>(`/portfolio/positions${asOf ? '?asOf=' + encodeURIComponent(asOf) : ''}`, {
    signal,
  });
/** 登记当前人工持仓，不代表向券商下单。 */
export const savePosition = (symbol: string, input: PositionInput) =>
  api<null>(`/portfolio/positions/${encodeURIComponent(symbol)}`, write('PUT', input));
/** 登记资产总额的业务日期及金额，单位人民币元。 */
export const saveAccount = (input: {
  totalAssets: number;
  asOfDate: string;
  riskBudget?: number;
  liquidityReserve?: number;
  fundsNeededOn?: string | null;
  fundsPurpose?: string;
  cashBalance?: number;
  positionsComplete?: boolean;
}) => api<null>('/portfolio/account', write('PUT', input));

export interface InstrumentCandidate {
  symbol: string;
  name: string;
  exchange: string;
  registered: boolean;
  source: string;
}
export interface InstrumentDiscovery {
  items: InstrumentCandidate[];
  remoteStatus: string;
  message: string;
}
export interface WatchDiscoveryResult {
  symbol: string;
  jobId: number | null;
  message: string;
}
/** 显式搜索已登记及公开目录，不在页面刷新时持续请求上游。 */
export const discoverInstruments = (q: string, signal?: AbortSignal) =>
  api<InstrumentDiscovery>(`/instruments/discover?q=${encodeURIComponent(q)}`, { signal });
/** 添加已验证候选，并根据用户选择初始化。 */
export const addDiscoveredWatch = (symbol: string, date: string, initialize: boolean) =>
  api<WatchDiscoveryResult>('/watchlist/discover', write('POST', { symbol, date, initialize }));

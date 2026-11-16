import type { FailureDiagnosis } from './common';
/** 通道测试仅开放基础能力，不用全市场采集证明连通。 */
export type ProbeCapability = 'TRADING_CALENDAR' | 'STOCK_DAILY' | 'INDEX_DAILY';
/** 一个配置内通道、一个能力、一个精确日期。 */
export interface ProviderProbeRequest {
  channel: string;
  capability: ProbeCapability;
  date: string;
}
/** 后端实际核验结果；失败和跳过的sample为空，不是零条成功。 */
export interface ProviderProbeResult {
  channel: string;
  capability: ProbeCapability;
  requestedDate: string;
  status: 'SUCCESS' | 'FAILED' | 'SKIPPED' | 'TIMED_OUT';
  checkedAt: string;
  elapsedMs: number;
  sample: {
    symbol: string | null;
    records: number;
    dataDate: string;
    source: string;
    marketOpen: boolean | null;
  } | null;
  diagnostic: FailureDiagnosis | null;
  /** 本机限频截止，不是供应商解封时间。 */
  retryAt: string | null;
}

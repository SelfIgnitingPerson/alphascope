import type { BacktestResult } from './models';
import type { ResearchScore } from './scoring';

/** 后台验证条件；未选证券时由服务端冻结当前已启用自选。 */
export interface ResearchValidationRequest {
  start: string;
  end: string;
  scope: 'ALL' | 'TECHNICAL' | 'MARKET';
  symbols: string[];
}
/** 实际清单覆盖，不把沪深数量作为全A分母。 */
export interface UniverseCoverage {
  businessDate: string;
  status: string;
  coverage: { scope: string; expected: number; observed: number; rosterVerified: boolean };
  exchangeMembers: Record<string, number>;
  missing: string[];
  source?: string;
  snapshotId?: number;
  sourceUrl?: string;
}
/** 一项市场评分对未来收益的配对检验。 */
export interface MarketValidationResult {
  period: string;
  horizon: number;
  status: string;
  sampleCount: number;
  rankCorrelation?: number | null;
  lowCount: number;
  lowMeanReturn?: number | null;
  highCount: number;
  highMeanReturn?: number | null;
  highMinusLow?: number | null;
  savedAfterBusinessDay: number;
}
/** 已裁剪大证据的页面摘要。 */
export interface ValidationSummary {
  scope: ResearchValidationRequest['scope'];
  start: string;
  end: string;
  protocolVersion: string;
  ruleDefinition: {
    ruleVersion: string;
    technicalVersion: string;
    parameters: Record<string, number>;
  };
  fullMarketVerdict: string;
  scoreAtEnd?: ResearchScore | null;
  coverageAtEnd?: UniverseCoverage | null;
  qualifiedScoreDays: number;
  marketResults: MarketValidationResult[];
  stocks: {
    symbol: string;
    priceDays: number;
    firstPriceDate?: string;
    lastPriceDate?: string;
    savedRuleRecords: number;
    savedRetrospectiveStatus: string;
    savedPointInTimeStatus: string;
    reconstructed?: BacktestResult | null;
    failure?: string | null;
  }[];
  blockers: string[];
  limitations: string[];
  verification: {
    matched: boolean;
    marketWindows: number;
    technicalStocks: number;
    technicalVersion: string;
  };
}
/** 任务详情只包含状态和轻量结果，完整证据仅显式下载。 */
export interface ValidationTask {
  id: number;
  status: string;
  scope: string;
  start?: string;
  end?: string;
  symbols?: string[];
  dataScope: string;
  completed: number;
  total: number;
  message?: string | null;
  createdAt?: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  heartbeatAt?: string | null;
  deadlineAt?: string | null;
  evidenceSha256?: string | null;
  summary?: ValidationSummary | null;
}

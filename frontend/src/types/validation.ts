import type { BacktestResult } from './models';
import type { ResearchScore } from './scoring';

/** 后台验证条件；未选证券时由服务端冻结当前已启用自选。 */
export interface ResearchValidationRequest {
  start: string;
  end: string;
  scope: 'ALL' | 'TECHNICAL' | 'MARKET';
  symbols: string[];
  portfolioCapital?: number;
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
  bseRoster?: import('./universe').MarketUniverse | null;
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
    strategy?: StrategySimulation | null;
  }[];
  blockers: string[];
  limitations: string[];
  portfolio?: StrategySimulation | null;
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

/** 按冻结执行口径记录逐笔买卖和平仓净盈亏。 */
export interface ReplayTradeDetails {
  closedCycles: { symbol: string; openedOn: string; closedOn: string; netPnl: number }[];
  orders: {
    symbol: string;
    signalDate: string | null;
    executionDate: string | null;
    action: string;
    trigger: string;
    shares: number | null;
    fillPrice: number | null;
    fee: number | null;
    blockedReason: string | null;
    entryBudget?: {
      oneLotAmount: number;
      oneLotCost: number;
      cash: number;
      positionRoom: number;
      estimatedStopLoss: number | null;
      riskLimit: number;
    };
  }[];
  filteredEntries: number;
  entryExecution?: { signals: number; filled: number; blocked: Record<string, number> };
}

/** 新验证任务冻结的保守组合模拟；旧任务不补写。 */
export interface StrategySimulation {
  version: string;
  parameters: Record<string, number>;
  limitations: string[];
  comparisons?: { code: string; title: string; periods: StrategySimulation['periods'] }[];
  stockMediumReplays?: {
    symbol: string;
    original: StrategySimulation['periods'];
    trend: StrategySimulation['periods'];
  }[];
  periods: {
    label: string;
    start: string;
    end: string;
    status: string;
    completedTrades: number;
    skippedOrders: number;
    netReturn: number | null;
    maximumDrawdown: number | null;
    benchmarkReturn: number | null;
    fees: number;
    openPosition: boolean;
    reason: string;
    skipReasons?: Record<string, number> | null;
    details?: ReplayTradeDetails | null;
    tradeQuality?: {
      averageClosedPnl: number | null;
      closedPnlWithoutBestWinner: number | null;
      delayedExitSessions: number;
      longestExitDelay: number;
      pendingExit: boolean;
      marketRegimes?: {
        code: string;
        closedTrades: number;
        winningTrades: number;
        netPnl: number | null;
      }[];
    } | null;
    tradeStatistics?: {
      closedTrades: number;
      winningTrades: number;
      losingTrades: number;
      breakEvenTrades: number;
      winRate: number | null;
    } | null;
  }[];
}

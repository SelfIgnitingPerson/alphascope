import { api, query } from './client';
import type { ResearchPriority } from '../types/research-priority';
/** 当前页自选的研究变化，不等同全市场机会排序。 */
export interface ResearchFocus {
  asOf: string;
  issues?: string[];
  knowledgeDate?: string;
  positionRiskEnabled?: boolean;
  total: number;
  page: number;
  size: number;
  portfolioImpact?: {
    heldCount: number;
    pendingCount: number;
    knownMarketValue: number | null;
    knownWeight: number | null;
    cashBalance?: number | null;
    cashAfterReserve?: number | null;
    stress10Loss?: number | null;
    stress20Loss?: number | null;
    referenceBudgetStatus?: string;
    riskGroups?: { label: string; marketValue: number; weight: number | null }[];
    limitations?: string[];
  };
  items: {
    symbol: string;
    name: string;
    category: 'RISK' | 'MATCH' | 'DATA_GAP' | 'CHANGED' | 'WAIT';
    title: string;
    explanation: string;
    horizon: string | null;
    quoteDate: string | null;
    comparisonDate: string | null;
    newlyMatched: boolean;
    changeLabel?: string;
    changeState?: string;
    comparisonBasis?: 'FROZEN_DAILY' | 'CURRENT_RULE_REPLAY';
    previousCapturedAt?: string | null;
    previousTitle?: string | null;
    originalPlanId?: number | null;
    positionImpact?: {
      state: 'HELD' | 'ESTIMATED' | 'FLAT' | 'STALE' | 'UNKNOWN';
      recordedAt: string | null;
      marketValue: number | null;
      weight: number | null;
      unrealizedPnl: number | null;
      referenceLoss: number | null;
      checks: string[];
      referenceState?: string;
      liquidityDays?: number | null;
    };
    priorities?: ResearchPriority[];
  }[];
}
/** 仅读取服务端当前用户的自选及两期现存研究，不采集或写入。 */
export const getResearchFocus = (
  input: { asOf: string; page: number; size: number },
  signal?: AbortSignal,
) => api<ResearchFocus>(`/research-focus?${query(input)}`, { signal });

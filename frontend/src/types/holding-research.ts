import type { PriceVolumeStudy, PriceVolumeZone } from './price-volume';
import type { ResearchPriority } from './research-priority';
import type { PositionStrategy } from './position-strategy';

/** 当前版本回顾视图，与报告冻结修订分别展示。 */
export interface HoldingResearch {
  version: string;
  symbol: string;
  name: string;
  asOf: string;
  quoteDate: string | null;
  source: string | null;
  close: number | null;
  horizons: HoldingHorizon[];
  priceVolume: PriceVolumeStudy;
  notes: string[];
  /** 当前服务端观察优先级；旧冻结记录不补造。 */
  priorities?: ResearchPriority[];
}
/** 每个持有周期独立判断，不从短期方案外推长期价值。 */
export interface HoldingHorizon {
  code: 'SHORT' | 'MEDIUM' | 'LONG';
  title: string;
  period: string;
  status: string;
  conclusion: string;
  actions: HoldingAction[];
  evidence: string[];
  missing: string[];
}
/** 方案的价位、行情条件和人工前提分别保留。 */
export interface HoldingAction {
  code: string;
  title: string;
  status: string;
  conclusion: string;
  observationZone: PriceVolumeZone | null;
  referencePrice: number | null;
  referenceLabel: string | null;
  stopReference: number | null;
  conditionLogic: 'ALL' | 'ANY';
  conditions: PositionStrategy['conditions'];
  invalidation: string[];
  assumptions: string[];
}

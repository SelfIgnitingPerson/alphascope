import type { PriceVolumeZone } from './price-volume';

/** 已冻结的条件式仓位研究；未核实个人是否持仓，不产生委托。 */
export interface PositionStrategy {
  /** 参数绑定的方法标识。 */
  version: string;
  /** OPEN_PULLBACK、OPEN_BREAKOUT、ADD_RETEST 或 REDUCE。 */
  code: string;
  title: string;
  /** 条件满足、等待、复核、风险过滤或资料不足。 */
  status: string;
  /** ALL 全部条件 / ANY 任一风险组合。 */
  conditionLogic: 'ALL' | 'ANY';
  conclusion: string;
  /** 候选观察带及提前形成的参考区间，单位人民币元。 */
  observationZone: PriceVolumeZone | null;
  referenceZone: PriceVolumeZone | null;
  /** 结构止损参考，人民币元。 */
  stopReference: number | null;
  /** 候选上沿至结构止损的比例距离及技术过滤上限，非账户风险预算。 */
  referenceRisk: number | null;
  maximumReferenceRisk: number | null;
  /** 每个行情条件的实测值、阈值及三态结果。 */
  conditions: { label: string; observed: string; required: string; met: boolean | null }[];
  /** 失效条件和待人工核验的个人前提。 */
  invalidation: string[];
  assumptions: string[];
}

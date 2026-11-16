import type { PositionStrategy } from './position-strategy';

/** 价格区间，全部价格为报告截止日因子锚点的人民币元。 */
export interface PriceVolumeZone {
  /** 区间下沿。 */
  lower: number;
  /** 区间上沿。 */
  upper: number;
  /** 可追溯的波峰、前高、日K近似量价桶或支撑类别。 */
  basis: string;
  /** 证据交易日期，不含报告截止日之后的数据。 */
  dates: string[];
  /** 仅日K价桶有效，占此前窗口量能的小数比例。 */
  volumeShare: number | null;
}

/** 已冻结的近两个月量价研究；旧报告不补算这一字段。 */
export interface PriceVolumeStudy {
  /** PRICE_VOLUME_2M_V3 等明确方法标识。 */
  version: string;
  /** TURNOVER_AMOUNT为人民币成交额；旧V1为空，保持旧代理量口径。 */
  volumeBasis?: string | null;
  /** 证券代码及展示名称。 */
  symbol: string;
  name: string;
  /** AVAILABLE、INSUFFICIENT_DATA、STALE、SUSPENDED 或 DEMO。 */
  status: string;
  /** 两个自然月的请求起点和业务截止日。 */
  windowStart: string;
  asOf: string;
  /** 有效日K数和应有交易日数；未通过校验时不展示为覆盖承诺。 */
  observedDays: number;
  expectedDays: number;
  /** 实际起点、共同来源及当前收盘，缺证据时为空。 */
  actualStart: string | null;
  source: string | null;
  close: number | null;
  /** 最近5期复权收益，小数比例。 */
  return5: number | null;
  /** 成交额量比；旧V1保留冻结的等效量代理，不解释为成交额。 */
  volumeRatio: number | null;
  volumeRatio5: number | null;
  /** 前14期真实波幅及含当日MA20，人民币元。 */
  atr14: number | null;
  ma20: number | null;
  /** 原结构支撑，可能已失守。 */
  support: PriceVolumeZone | null;
  /** 原支撑下沿减0.5ATR，人民币元；不是委托保证成交价。 */
  stopReference: number | null;
  stopStatus: string;
  /** 上方最多3个压力区间；突破此前两月高点时可为空。 */
  resistances: PriceVolumeZone[];
  /** 条件式减仓研究状态与说明，不含个人仓位比例。 */
  decision: string;
  conclusion: string;
  /** 冻结事实、缺项和本方法的固定定义与限制。 */
  evidence: string[];
  missing: string[];
  methodNotes: string[];
  /** 新方法修订冻结的条件式仓位研究；旧修订不补算。 */
  strategies?: PositionStrategy[] | null;
}

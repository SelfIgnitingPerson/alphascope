/** 累计财报及机构预测类型；保持现有 REST 字段名称。 */
import type { Numeric } from './common';

export interface Financial {
  /** 累计财报的报告期末日期，按同报告期进行同比比较。 */
  period_end: string;
  /** 材料公开发布时间；仅知日期时按供应商的保守可用时点处理。 */
  published_at: string;
  /** 截至报告业务日可用的累计财报类型，不混用单季度口径。 */
  report_type: string;
  /** 供同期对照核验的实际来源；旧资料缺省时不假定可比。 */
  source?: string | null;
  /** 可核验的原公开来源地址，不包含本机凭据。 */
  source_url?: string | null;
  /** 同一报告期的不可变内容版本。 */
  version?: number;
  /** 资料可用于研究的保守时点，后端据此筛选。 */
  available_at?: string | null;
  /** 累计营业收入，单位人民币元；为空表示尚无可用证据。 */
  revenue: Numeric | null;
  /** 累计归属于母公司股东的净利润，单位人民币元；允许亏损，空值表示缺失。 */
  net_profit: Numeric | null;
  /** 累计经营活动现金流量净额，单位人民币元；需与历年相同累计期间比较。 */
  operating_cashflow: Numeric | null;
  /** 累计毛利率，以小数存储；例如0.30表示30%，空值表示缺失。 */
  gross_margin: Numeric | null;
}

export interface ForecastView {
  /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
  symbol: string;
  /** 标的中文名称，用于页面展示。 */
  name: string;
  /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
  status: string;
  /** 截至请求时点可见的最新预测快照；尚无观测时为空。 */
  latest: {
    /** 当前资源记录的数据库主键。 */
    id: number;
    /** 该证据最早可用于研究的时刻；不能倒填为未观测到的历史时间。 */
    availableAt: string;
    /** 原始数据实际采集时间；复用缓存时不改成当前时间。 */
    fetchedAt: string;
    /** 该项证据的实际供应商或原始来源标识。 */
    source: string;
    /** 可核验的公开原始来源URL。 */
    sourceUrl: string;
    /** 近六个月评级机构数，不等于每个年度EPS的实际参与机构数。 */
    ratingAgencies: number;
    /** 按财务年度排列的公开汇总预测EPS列表。 */
    estimates: {
      /** 预测对应的财务年度，不是快照采集年份。 */
      year: number;
      /** 该年度公开汇总预测每股收益，单位元/股；未核验逐家机构的一致口径。 */
      eps: number;
    }[];
  } | null;
  /** 同一业务对象的已保存版本之间的差异列表。 */
  changes: {
    /** 预测对应的财务年度，不是快照采集年份。 */
    year: number;
    /** 上一份可比同年度快照的预测EPS，单位元/股。 */
    previousEps: number;
    /** 本次同年度快照的预测EPS，单位元/股。 */
    currentEps: number;
    /** 本次EPS减前次EPS，单位元/股；不是百分比变化率。 */
    difference: number;
    /** 上一份可比预测快照的实际首次观测时刻。 */
    previousObservedAt: string;
    /** 当前预测快照的实际首次观测时刻。 */
    currentObservedAt: string;
  }[];
  /** 当前状态、判断依据或数据不足原因的文字说明。 */
  explanation: string;
  /** 当前范围、数据时点和统计方法的适用边界。 */
  limitations: string[];
  /** 采集状态及失败原因；只读页面不触发新的上游请求。 */
  collection: {
    /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */ status?: string;
  };
}

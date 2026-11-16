/** 主题、披露样本及研究窗口类型；保持现有 REST 字段名称。 */
import type { Numeric } from './common';
import type { Instrument } from './market';

export interface Theme {
  /** 当前资源记录的数据库主键。 */
  id: number;
  /** 稳定业务代码，需结合所属结构解释。 */
  code: string;
  /** 标的中文名称，用于页面展示。 */
  name: string;
  /** 业务主题或资源的中文说明。 */
  description?: string;
}

export interface ThemeScore {
  /** 历史有效成员当日同源复权收益的等权均值，缺失时不按零处理。 */
  day_return?: Numeric | null;
  /** 该记录是否为演示数据，不能作为真实效果证据。 */
  is_synthetic?: boolean;
  /** 行情或指标所属交易日期，使用上海时区。 */
  trade_date: string;
  /** 模型评分；缺失时保持空值，不能按0分或按已有维度放大。 */
  score: Numeric | null;
  /** 20期标的收益比除以同期沪深300收益比再减1，以小数存储；日期不齐时为空。 */
  rs20: Numeric | null;
  /** 有效成员中收盘高于MA20的比例，范围0至1。 */
  breadth: Numeric | null;
  /** 有效数据数量相对于规定覆盖分母的比例，以0至1小数表示。 */
  coverage: Numeric | null;
}

export interface ThemeStrength {
  /** 当前页或当前响应包含的业务记录列表。 */
  items: ThemeScore[];
  /** 该不可变研究样本定义的完整成员，不等于整个行业成分。 */
  members: Instrument[];
  /** 本次计算采用的算法标识，用于核对历史口径。 */
  algorithmVersion: string;
  /** 研究范围标识；披露样本不等同于完整行业。 */
  scope?: string;
  /** 截至业务时点可见的公开样本定义，无披露定义时为空。 */
  cohort?: {
    /** 固定研究样本的名称。 */
    title: string;
    /** 定义的完整成员数量。 */
    expected_members: number;
    /** 样本公开依据的标题和原始链接。 */
    source_title: string;
    source_url: string;
    /** 证据实际公开、采集及最早可用时间，均不可倒填。 */
    source_published_at: string;
    source_fetched_at: string;
    available_at: string;
    /** 成员归属的有效区间。 */
    effective_from: string;
    effective_to?: string | null;
    /** 原始定义JSON，包含范围说明与来源页码。 */
    definition_json: string;
  } | null;
  /** 完整行业覆盖情况；未核实保持UNKNOWN。 */
  industryCoverage?: string;
}

export interface IndustryMetric {
  /** 产业指标稳定代码，需与unit共同解释。 */
  metric_code: string;
  /** 本次资产或产业指标登记对应的业务日期。 */
  as_of_date: string;
  /** 指标数值；单位由unit给出，缺失时不得补成0。 */
  value: Numeric;
  /** 指标计量单位，例如元、股、倍或小数比例。 */
  unit: string;
  /** 供研究页面展示的中文标题。 */
  title: string;
  /** 原始公开材料链接，用于核查证据。 */
  url: string;
}

export interface ThemeWindow {
  /** 统计回看交易期数，当前为20或60。 */
  lookback: number;
  /** 实际用于相关性计算的日期对齐收益样本数。 */
  samples: number;
  /** 剔除当前标的自身后应覆盖的同业成员数量。 */
  expectedPeers: number;
  /** 实际取得完整窗口并参与计算的同业代码列表。 */
  peers: string[];
  /** 有效数据数量相对于规定覆盖分母的比例，以0至1小数表示。 */
  coverage: number | null;
  /** 个股与留一法同业日收益的皮尔逊相关系数，范围-1至1。 */
  correlation: number | null;
  /** 分别剔除沪深300线性影响后，个股与同业收益的偏相关系数。 */
  marketAdjustedCorrelation: number | null;
  /** 当前标的在该统计窗口的累计复权收益，以小数存储。 */
  stockReturn: number | null;
  /** 窗口内同业每日等权组合的累计收益，以小数存储。 */
  peerReturn: number | null;
  /** 标的窗口收益减同业组合窗口收益；乘100后为百分点差。 */
  excessReturn: number | null;
  /** 统计窗口最后一个交易日的同业等权收益，以小数存储。 */
  peerDayReturn: number | null;
  /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
  status: string;
}

export interface ThemeResearch {
  /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
  symbol: string;
  /** 主题稳定代码，例如PCB或COOLING。 */
  themeCode: string;
  /** 供研究页面展示的中文标题。 */
  title: string;
  /** 研究覆盖范围；固定样本、沪深A股与完整全A不能相互替代。 */
  scope: string;
  /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
  status: string;
  /** 该研究样本采用的原始来源、页码和完整定义。 */
  source: {
    /** 供研究页面展示的中文标题。 */
    title: string;
    /** 原始公开材料链接，用于核查证据。 */
    url: string;
    /** 原始材料中支持该样本定义的页码。 */
    pages: string;
    /** 该证据最早可用于研究的时刻；不能倒填为未观测到的历史时间。 */
    availableAt: string;
    /** 原始内容的SHA-256指纹，用于去重和追溯。 */
    contentHash: string;
    /** 固定研究样本的选择依据、成员定义及覆盖边界。 */
    definition: string;
  };
  /** 该不可变研究样本定义的完整成员，不等于整个行业成分。 */
  members: {
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    symbol: string /** 标的中文名称，用于页面展示。 */;
    name: string;
  }[];
  /** 同一研究样本最近三个实际交易日的收益、广度及覆盖记录。 */
  recentDays: {
    /** 当前页面所选业务日期，接口使用YYYY-MM-DD格式。 */
    date: string;
    /** 相邻两个有效收盘的同源复权收益，以小数存储。 */
    dayReturn: number | null;
    /** 有效成员中收盘高于MA20的比例，范围0至1。 */
    breadth: number | null;
    /** 有效数据数量相对于规定覆盖分母的比例，以0至1小数表示。 */
    coverage: number | null;
    /** 该日期满足计算条件的成员数量。 */
    validMembers: number;
    /** 该业务日期研究样本的完整成员数量。 */
    totalMembers: number;
  }[];
  /** 排除当前标的自身后的20期同业联动统计。 */
  window20: ThemeWindow;
  /** 排除当前标的自身后的60期同业联动统计。 */
  window60: ThemeWindow;
  /** 从当前样本实际数据得到的观测事实。 */
  observation: string;
  /** 待验证的解释假设；不能当作已经证实的事实。 */
  hypotheses: string[];
  /** 后续需要人工核对的事项列表。 */
  checks: string[];
  /** 当前范围、数据时点和统计方法的适用边界。 */
  limitations: string[];
}

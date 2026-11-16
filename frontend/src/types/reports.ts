import type { PriceVolumeStudy } from './price-volume';
/** 冻结报告及结构化研究分析类型；保持现有 REST 字段名称。 */
import type { MarketAcquisition, MarketObservation } from './market';
import type { ForecastView } from './fundamental';
import type { ThemeResearch } from './industry';

export type ReportType = 'DAILY' | 'WEEKLY';

export interface ReportSummary {
  /** 当前资源记录的数据库主键。 */
  id: number;
  /** 报告业务类型；具体枚举和累计口径由所属结构说明。 */
  report_type: ReportType;
  /** 本次研究采用的业务日期，使用上海时区。 */
  business_date: string;
  /** 同类型、同业务日期的报告修订序号，从1递增。 */
  revision: number;
  /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
  status: string;
  /** 实际使用的生成模型；RULE_FALLBACK表示规则摘要。 */
  model: string;
  /** 报告采用行情的业务截止时刻，通常为交易日15:00，上海时区。 */
  data_as_of: string;
  /** 本报告采用的证据中最新实际采集时刻；复用缓存不改写。 */
  collected_at?: string;
  /** 本次计算采用的算法标识，用于核对历史口径。 */
  algorithm_version?: string;
  /** 运行配置的数据源范围，例如DEMO或REAL:BAOSTOCK,SINA，不等于单条证据来源。 */
  data_scope?: string;
  /** 计算所采用完整输入的SHA-256指纹。 */
  input_hash?: string;
  /** 是否为演示数据；真实数据和演示数据必须隔离。 */
  is_synthetic: boolean;
}

export interface ReportContent {
  /** 完整全市场评级；证据不足必须保留UNKNOWN。 */
  marketState: string;
  /** 只解释已提供事实的报告文字摘要。 */
  summary: string;
  /** 有冻结证据支持的积极因素；没有依据时为空列表。 */
  positiveFactors: string[];
  /** 有冻结证据支持的不利因素。 */
  negativeFactors: string[];
  /** 主题观测与待验证推断，不冒充全行业结论。 */
  themeOutlook: string[];
  /** 当前关注标的的规则风险及证据边界。 */
  watchlistRisks: string[];
  /** 下一步应补齐或复核的具体事项。 */
  nextChecks: string[];
  /** 尚未验证的假设、数据缺口和方法限制。 */
  uncertainties: string[];
  /** 本次摘要允许引用的来源标识白名单。 */
  sourceIds: string[];
}

export interface ReportSource {
  /** 引用的原始来源文档ID；仅有数据批次引用时可为空。 */
  source_document_id?: number;
  /** 实际引用的数据记录或采集批次标识。 */
  data_reference?: string;
}

export interface ReportDetail extends ReportSummary {
  /** 生成时保存的结构化文字报告，不从当前数据库补写历史正文。 */
  content_json: string | ReportContent;
  /** 本修订实际采用的来源文档或数据批次引用列表。 */
  sources: ReportSource[];
  /** 本修订冻结的完整输入JSON，是读取历史报告的唯一证据边界。 */
  input_snapshot?: unknown;
  /** 由该修订冻结输入形成的结构化分析，允许为空以兼容缺少证据的旧报告。 */
  analysis?: ReportAnalysis;
}

export interface ReportComparison {
  /** 作为比较基准的报告修订ID。 */
  leftId: number;
  /** 与基准比较的另一份报告修订ID。 */
  rightId: number;
  /** 两份报告冻结输入的内容指纹是否不同。 */
  inputChanged: boolean;
  /** 同一业务对象的已保存版本之间的差异列表。 */
  changes: {
    /** 发生变化的结构化报告字段名称。 */ field: string /** 比较基准修订中保存的原始值。 */;
    before: unknown /** 比较目标修订中保存的原始值。 */;
    after: unknown;
  }[];
}

export interface ReportStockFact {
  /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
  symbol: string;
  /** 标的中文名称，用于页面展示。 */
  name: string;
  /** 当前事实真正对应的交易日期；不能用报告生成日期替代。 */
  asOf: string | null;
  /** 行情事实状态：TRADING当日有效、SUSPENDED证实停牌、MISSING无记录或STALE仅有旧日行情。 */
  status: string;
  /** 该项证据的实际供应商或原始来源标识。 */
  source: string;
  /** 原始行情写入批次，用于追溯本条事实。 */
  batchId: string;
  /** 未复权收盘价，单位人民币元；缺失时不能当作0。 */
  close: number | null;
  /** 相邻两个有效收盘的同源复权收益，以小数存储。 */
  dayReturn: number | null;
  /** 报告研究期间的同源复权收益；日报取当日，周报按有效交易日区间计算。 */
  periodReturn: number | null;
  /** 最近5个有K线交易日的复权收益，以小数存储；历史不足时为空。 */
  return5: number | null;
  /** 最近20个有K线交易日的复权收益，以小数存储；历史不足时为空。 */
  return20: number | null;
  /** 最近20个有K线交易日的同锚点复权收盘均价，单位元；含当日，历史不足时为空。 */
  ma20: number | null;
  /** 最近60个有K线交易日的同锚点复权收盘均价，单位元；含当日，历史不足时为空。 */
  ma60: number | null;
  /** 收盘价相对动态MA20的偏离比例，以小数存储。 */
  distanceMa20: number | null;
  /** 收盘价相对动态MA60的偏离比例，以小数存储。 */
  distanceMa60: number | null;
  /** 当日成交量相对前20个有K线交易日均量的倍数；证据不足时为空。 */
  volumeRatio: number | null;
  /** 20期标的收益比除以同期沪深300收益比再减1，以小数存储；日期不齐时为空。 */
  rs20: number | null;
  /** 分析日前20个有K线交易日的最低复权收盘价，使用当前分析锚点，单位元。 */
  previous20Low: number | null;
  /** 当前收盘与动态均线形成的趋势描述，不是固定买卖价格。 */
  trend: string;
  /** 已触发的规则记录数量。 */
  triggered: number;
  /** 有足够证据可评估的规则记录数量；不含证据不足或不适用。 */
  reviewed: number;
  /** 缺少必要输入、无法评估的规则记录数量。 */
  insufficient: number;
}

export interface ReportAssessment {
  /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
  symbol: string;
  /** 标的中文名称，用于页面展示。 */
  name: string;
  /** 当前页面所选业务日期，接口使用YYYY-MM-DD格式。 */
  date: string;
  /** 稳定规则代码，例如TREND_BREAK_20或PROFIT_SLOWDOWN。 */
  ruleCode: string;
  /** 供研究页面展示的规则中文名称。 */
  ruleName: string;
  /** 规则状态：TRIGGERED触发、NOT_TRIGGERED未触发、INSUFFICIENT_DATA不足或NOT_APPLICABLE不适用。 */
  status: string;
  /** 规则严重度：HIGH高、MEDIUM中、LOW低。 */
  severity: string;
  /** 本条判断采用的具体指标值或证据列表。 */
  evidence: {
    /** 指标的中文名称。 */ label: string /** 指标数值；单位由unit给出，缺失时不得补成0。 */;
    value: number /** 指标计量单位，例如元、股、倍或小数比例。 */;
    unit: string;
  }[];
  /** 评估该规则仍缺少的输入名称，不能把缺失值当作未触发。 */
  missingInputs: string[];
  /** 当前状态、判断依据或数据不足原因的文字说明。 */
  explanation?: string;
}

export interface ReportAnalysis {
  /** 冻结分析结构标识；旧修订保留其生成时的标识。 */
  version: string;
  /** 结构化事实状态：AVAILABLE可用、PARTIAL部分、DEMO演示或LEGACY_NO_MARKET_SNAPSHOT旧版缺少行情。 */
  status: string;
  /** 基于当前冻结证据形成的研究摘要标题。 */
  headline: string;
  /** 同一研究期间沪深300的复权收益，以小数存储；无证据时为空。 */
  benchmarkReturn: number | null;
  /** 当前报告关注标的的冻结行情事实列表。 */
  stocks: ReportStockFact[];
  /** 各标的逐条规则评估，分别保留触发、未触发、证据不足和不适用。 */
  assessments: ReportAssessment[];
  /** 后续需要人工核对的事项列表。 */
  checks: {
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    symbol: string /** 供研究页面展示的中文标题。 */;
    title: string /** 待核查事项的具体操作说明。 */;
    detail: string;
  }[];
  /** 当前报告尚未覆盖的数据或方法缺项。 */
  missingData: string[];
  /** 价格口径、时点和方法适用范围的说明。 */
  methodNotes: string[];
  /** 研究模式：WATCHLIST只研究自选，PORTFOLIO启用持仓风险；LEGACY表示旧报告未保存。 */
  researchMode?: 'WATCHLIST' | 'PORTFOLIO' | 'LEGACY';
  /** 有效主题研究样本或其证据诊断列表。 */
  themes?: ThemeResearch[];
  /** 基于冻结证据形成的研究核查建议，不是交易指令。 */
  advice?: ResearchAdvice[];
  /** 独立沪深市场分项观察；旧报告或尚无快照时可为空。 */
  marketObservation?: MarketObservation | null;
  /** 业务截止时点之前可见的公开盈利预测快照。 */
  forecasts?: ForecastView[];
  /** 本次各标的行情获取方式、校验区间与原采集时间。 */
  marketAcquisition?: MarketAcquisition[];
  /** 仅新方法报告包含的冻结近两月量价、压力和条件式减仓研究。 */
  priceVolume?: PriceVolumeStudy[] | null;
  /** 截至报告业务时点可用的已披露累计财报证据。 */
  financials?: {
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    symbol: string;
    /** 标的中文名称，用于页面展示。 */
    name: string;
    /** 累计财报的报告期末日期，按同报告期进行同比比较。 */
    periodEnd: string;
    /** 截至报告业务日可用的累计财报类型，不混用单季度口径。 */
    reportType: string;
    /** 材料公开发布时间；仅知日期时按供应商的保守可用时点处理。 */
    publishedAt: string;
    /** 该证据最早可用于研究的时刻；不能倒填为未观测到的历史时间。 */
    availableAt: string;
    /** 该项证据的实际供应商或原始来源标识。 */
    source: string;
    /** 累计营业收入，单位人民币元；为空表示尚无可用证据。 */
    revenue: number | null;
    /** 累计归属于母公司股东的净利润，单位人民币元；允许亏损，空值表示缺失。 */
    netProfit: number | null;
    /** 累计经营活动现金流量净额，单位人民币元；需与历年相同累计期间比较。 */
    operatingCashflow: number | null;
    /** 累计毛利率，以小数存储；例如0.30表示30%，空值表示缺失。 */
    grossMargin: number | null;
    /** 累计营业收入同比增速，以小数存储；例如0.20表示20%。 */
    revenueYoy: number | null;
    /** 累计归母净利润同比增速，以小数存储；例如-0.10表示下降10%。 */
    profitYoy: number | null;
  }[];
  /** 与报告业务日期对应的市场截面事实。 */
  market?: {
    /** 沪深截面覆盖状态，COMPLETE完整不等于已完成全A评级。 */
    status: string;
    /** 研究覆盖范围；固定样本、沪深A股与完整全A不能相互替代。 */
    scope: string;
    /** 该项证据的实际供应商或原始来源标识。 */
    source: string;
    /** 成交金额，单位人民币元；缺失时不补造0。 */
    amount: number | null;
    /** 当日收盘上涨的有效交易股票家数。 */
    advancers: number | null;
    /** 当日收盘下跌的有效交易股票家数。 */
    decliners: number | null;
    /** 当日收盘持平的有效交易股票家数。 */
    unchanged: number | null;
    /** 当日供应商明确标记停牌的股票家数。 */
    suspended: number | null;
    /** 当日证券清单中应覆盖的交易股票数量。 */
    expected: number | null;
    /** 实际取得有效日线的交易股票数量。 */
    observed: number | null;
    /** 有效数据数量相对于规定覆盖分母的比例，以0至1小数表示。 */
    coverage: number | null;
  };
}

export interface ResearchAdvice {
  /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
  symbol: string;
  /** 标的中文名称，用于页面展示。 */
  name: string;
  /** 人工研究优先级，不是个人仓位建议或交易指令。 */
  priority: string;
  /** 基于冻结证据确定的本期研究重点，不构成下单指令。 */
  conclusion: string;
  /** 用于支持研究结论的已观测事实列表。 */
  observations: string[];
  /** 待验证的解释假设；不能当作已经证实的事实。 */
  hypotheses: string[];
  /** 下一步可具体执行的人工核查事项。 */
  verifyNext: string[];
  /** 哪些新证据或指标变化出现后需要调整当前判断。 */
  reconsiderIf: string[];
}

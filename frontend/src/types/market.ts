/** 行情、技术指标及市场观察类型；保持现有 REST 字段名称。 */
import type { Numeric } from './common';

export interface Instrument {
  /** 旧类型兼容的可选主键；当前标的公开接口不返回数据库ID。 */
  id?: number;
  /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
  symbol: string;
  /** 标的中文名称，用于页面展示。 */
  name: string;
  /** 交易所代码，需与symbol的后缀保持一致。 */
  exchange?: string;
  /** 标的类型：STOCK为股票，INDEX为指数。 */
  type?: string;
  /** 主数据是否启用；不等同于当日是否交易。 */
  active?: boolean;
}

export interface DailyBar {
  /** 行情或指标所属交易日期，使用上海时区。 */
  trade_date: string;
  /** 未复权开盘价，单位人民币元。 */
  open: Numeric;
  /** 未复权最高价，单位人民币元。 */
  high: Numeric;
  /** 未复权最低价，单位人民币元。 */
  low: Numeric;
  /** 未复权收盘价，单位人民币元；缺失时不能当作0。 */
  close: Numeric;
  /** 成交量，单位股；供应商使用手时必须先换算。 */
  volume: Numeric;
  /** 成交金额，单位人民币元；缺失时不补造0。 */
  amount: Numeric;
  /** 与原价同源的正数复权因子；缺失时不能假定为1。 */
  factor: Numeric | null;
  /** 该项证据的实际供应商或原始来源标识。 */
  source: string;
  /** 原始数据实际采集时间；复用缓存时不改成当前时间。 */
  fetched_at: string;
  /** 是否为演示数据；真实数据和演示数据必须隔离。 */
  is_synthetic: boolean;
}

export interface BarResponse {
  /** 当前页或当前响应包含的业务记录列表。 */
  items: DailyBar[];
  /** 价格单位标识，当前为CNY（人民币元）。 */
  priceUnit: string;
  /** 数量单位标识，当前为SHARE（股）。 */
  volumeUnit: string;
  /** 成交金额单位标识，当前为CNY（人民币元）。 */
  amountUnit: string;
}

export interface Indicator {
  /** 行情或指标所属交易日期，使用上海时区。 */
  trade_date: string;
  /** 最近5个有K线交易日的同锚点复权收盘均价，单位元；含当日，历史不足时为空。 */
  ma5: Numeric | null;
  /** 最近10个有K线交易日的同锚点复权收盘均价，单位元；含当日，历史不足时为空。 */
  ma10: Numeric | null;
  /** 最近20个有K线交易日的同锚点复权收盘均价，单位元；含当日，历史不足时为空。 */
  ma20: Numeric | null;
  /** 最近60个有K线交易日的同锚点复权收盘均价，单位元；含当日，历史不足时为空。 */
  ma60: Numeric | null;
  /** 14期Wilder平均真实波幅，单位人民币元；历史不足时为空。 */
  atr14: Numeric | null;
  /** 20期标的收益比除以同期沪深300收益比再减1，以小数存储；日期不齐时为空。 */
  rs20: Numeric | null;
  /** 当日成交量除以前20个有K线交易日均量，单位倍；均量不含当日。 */
  vol_ratio: Numeric | null;
  /** 数据完整性状态；COMPLETE表示完整，证据或历史不足时不能按零值解释。 */
  data_status: string;
}

export interface MarketOverview {
  /** 本次研究采用的业务日期，使用上海时区。 */
  businessDate: string;
  /** 当前通道或市场状态；市场UNKNOWN与通道UNAVAILABLE含义不同。 */
  state: string;
  /** 实际提供该窗口数据的供应商代码，不是多通道配置名称。 */
  provider: string;
  /** 是否为演示数据；真实数据和演示数据必须隔离。 */
  isSynthetic: boolean;
  /** 数据完整性状态；COMPLETE表示完整，证据或历史不足时不能按零值解释。 */
  dataStatus: string;
  /** 从当前样本实际数据得到的观测事实。 */
  observation?: MarketObservationView;
}

export interface ProviderChannel {
  diagnostic?: import('./common').FailureDiagnosis | null;
  /** 五项能力的声明及独立状态；支持实现不等于账号有权限。 */
  capabilities?: {
    diagnostic?: import('./common').FailureDiagnosis | null;
    capability: string;
    supported: boolean;
    state: string;
    lastSuccess?: string | null;
    lastError?: string | null;
    retryAt?: string | null;
    /** 本进程最近实际失败时间；刷新与冷却不更新。 */
    lastFailure?: string | null;
  }[];
  /** 单个真实行情通道标识，例如BAOSTOCK或SINA。 */
  channel: string;
  /** 该通道是否支持项目要求的完整指数日线，包括可验证成交额。 */
  supportsIndexBars?: boolean;
  /** 当前通道或市场状态；市场UNKNOWN与通道UNAVAILABLE含义不同。 */
  state: string;
  /** 最近一次实际成功访问该通道的时间；尚无成功记录时为空。 */
  lastSuccess: string | null;
  /** 最近失败原因；受限状态保留上游明确拒绝的原因。 */
  lastError: string;
  /** 该通道连续失败次数，用于冷却判定。 */
  consecutiveFailures: number;
  /** 通道允许自动重试的时刻；访问被明确拒绝时为null，不自动解除保护。 */
  retryAt: string | null;
}

export interface SourceRecord {
  /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
  symbol: string;
  /** 该项证据的实际供应商或原始来源标识。 */
  source: string;
  /** 行情或指标所属交易日期，使用上海时区。 */
  trade_date: string;
  /** 原始数据实际采集时间；复用缓存时不改成当前时间。 */
  fetched_at: string;
}

export interface ProviderStatus {
  /** 本次行情获取方式：FETCHED新采集、FRESH_CACHE有效缓存、STORED_HISTORY已存真实历史。 */
  mode: string;
  /** 运行配置的数据源范围，例如DEMO或REAL:BAOSTOCK,SINA，不等于单条证据来源。 */
  dataScope: string;
  /** 是否为演示数据；真实数据和演示数据必须隔离。 */
  isSynthetic: boolean;
  /** 本进程最近成功取得交易日历的实际通道；重启后可能尚未访问。 */
  calendarSource: string;
  /** 按当前优先顺序配置的通道状态列表。 */
  channels: ProviderChannel[];
  /** 每只标的已经保存的最新行情来源、业务日期及原采集时刻。 */
  latestData: SourceRecord[];
  /** 未进入当前路由的实际配置原因；不公开Token。 */
  inactiveChannels?: { channel: string; state: string; reason: string; action: string }[];
}

export interface MarketAcquisition {
  /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
  symbol: string;
  /** 本次行情获取方式：FETCHED新采集、FRESH_CACHE有效缓存、STORED_HISTORY已存真实历史。 */
  mode: 'STORED_HISTORY' | 'FRESH_CACHE' | 'FETCHED';
  /** 该项证据的实际供应商或原始来源标识。 */
  source: string;
  /** 本次完整性验证区间的起始业务日期，包含该日。 */
  validatedFrom: string;
  /** 本次完整性验证区间的截止业务日期，包含该日。 */
  validatedThrough: string;
  /** 该标的验证区间内真实保存的行情条数。 */
  rows: number;
  /** 本窗口最早的原始采集时间；无记录时为空。 */
  oldestFetchedAt: string | null;
  /** 本窗口最新的原始采集时间；无记录时为空。 */
  newestFetchedAt: string | null;
  /** 获取方式及未联网复核等边界说明。 */
  note: string;
}

export interface MarketObservation {
  /** 独立沪深分项观察模型标识；不代表完整全A评级。 */
  version: string;
  /** 本次研究采用的业务日期，使用上海时区。 */
  businessDate: string;
  /** 研究覆盖范围；固定样本、沪深A股与完整全A不能相互替代。 */
  scope: string;
  /** 分项整体可用状态，覆盖不足时保留PARTIAL或INSUFFICIENT_DATA等状态。 */
  status: string;
  /** 沪深300的价格、均线及趋势判断分项。 */
  trend: {
    /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
    status: string;
    /** 分项中文判断；证据不足时明确保留不足状态。 */
    judgment: string;
    /** 沪深300当日未复权收盘价；用于独立指数趋势观测。 */
    close: number | null;
    /** 沪深300最近20个交易日收盘均价；历史不足时为空。 */
    ma20: number | null;
    /** 沪深300最近60个交易日收盘均价；历史不足时为空。 */
    ma60: number | null;
    /** 当前MA20减前一交易日同锚点MA20，单位元；不是百分比斜率。 */
    ma20Slope: number | null;
    /** 相邻两个有效收盘的同源复权收益，以小数存储。 */
    dayReturn: number | null;
    /** 当前状态、判断依据或数据不足原因的文字说明。 */
    explanation: string;
  };
  /** 沪深成交额相对历史均额的量能分项，缺失历史时保留不足。 */
  turnover: {
    /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
    status: string;
    /** 分项中文判断；证据不足时明确保留不足状态。 */
    judgment: string;
    /** 成交金额，单位人民币元；缺失时不补造0。 */
    amount: number | null;
    /** 包含业务日的最近5个实际交易日成交额均值，单位元；必须满足完整20日窗口。 */
    average5: number | null;
    /** 包含业务日的最近20个实际交易日成交额均值，单位元；窗口不完整时为空。 */
    average20: number | null;
    /** 当日成交额除以5日成交额均值，单位倍；该均值包含业务日。 */
    ratio5: number | null;
    /** 当日成交额除以包含业务日的20日均额，单位倍；历史不完整时为空。 */
    ratio20: number | null;
    /** 最近20个实际交易日窗口内取得完整市场截面的天数。 */
    completeDays: number;
    /** 当前状态、判断依据或数据不足原因的文字说明。 */
    explanation: string;
  };
  /** 沪深交易股票的均线广度、有效分母及覆盖率分项。 */
  breadth: {
    /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
    status: string;
    /** 分项中文判断；证据不足时明确保留不足状态。 */
    judgment: string;
    /** 当前市场截面中取得有效日线的交易股票家数，不含明确停牌。 */
    trading: number;
    /** 当前截面中供应商明确标记停牌的股票家数。 */
    suspended: number;
    /** 具备至少20个有效复权收盘的当日交易股票数。 */
    valid20: number;
    /** 有效MA20样本中当前收盘高于MA20的股票数。 */
    above20: number;
    /** 有效MA20样本数除以当日交易股票数，范围0至1。 */
    coverage20: number | null;
    /** 有效MA20样本中收盘高于MA20的比例；覆盖不足时为空。 */
    ratio20: number | null;
    /** 具备至少60个有效复权收盘的当日交易股票数。 */
    valid60: number;
    /** 有效MA60样本中当前收盘高于MA60的股票数。 */
    above60: number;
    /** 有效MA60样本数除以当日交易股票数，范围0至1。 */
    coverage60: number | null;
    /** 有效MA60样本中收盘高于MA60的比例，以0至1小数表示；覆盖不足时为空。 */
    ratio60: number | null;
    /** 上涨家数除以取得有效日线的交易股票数，范围0至1。 */
    advancingRatio: number | null;
    /** 当前状态、判断依据或数据不足原因的文字说明。 */
    explanation: string;
  };
  /** 本次计算实际采用的阈值参数，须随冻结证据一起保存。 */
  parameters: Record<string, number>;
  /** 当前范围、数据时点和统计方法的适用边界。 */
  limitations: string[];
}

export interface MarketObservationView {
  /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
  status: string;
  /** 已保存不可变观察快照的主键；未取得快照时为空。 */
  snapshotId: number | null;
  /** 原始数据实际采集时间；复用缓存时不改成当前时间。 */
  fetchedAt: string | null;
  /** 该市场快照已经保存的独立分项分析；尚无可用观察时为空。 */
  analysis: MarketObservation | null;
  /** 采集状态及失败原因；只读页面不触发新的上游请求。 */
  collection: {
    /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
    status?: string;
    /** 实际保存的采集诊断 JSON。 */
    evidence_json?: string;
  };
}

/** 本机免费节点请求统计，不代表同一出口IP的全部请求。 */
export interface BaoStockPolicy {
  date: string | null;
  limit: number;
  used: number | null;
  remaining: number | null;
  accessProtected: boolean | null;
  accessCode?: string | null;
  accessObservedAt?: string | null;
  accessError?: import('./common').ProviderError | null;
  error: string | null;
}

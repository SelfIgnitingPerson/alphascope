/** 风险信号及历史效果统计类型；保持现有 REST 字段名称。 */

export interface Signal {
  /** 当前资源记录的数据库主键。 */
  id: number;
  /** 行情或指标所属交易日期，使用上海时区。 */
  trade_date: string;
  /** 稳定规则代码，例如TREND_BREAK_20或PROFIT_SLOWDOWN。 */
  rule_code: string;
  /** 规则严重度：HIGH高、MEDIUM中、LOW低。 */
  severity: string;
  /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
  status: string;
  /** 规则的原始证据JSON；旧接口可能以字符串返回，展示前须按实际类型解析。 */
  evidence_json: string | Record<string, unknown>;
}

export interface SignalStatistic {
  /** 收益中位数，小数。 */
  medianReturn?: number | null;
  /** 同日期沪深300平均收益，缺失不按0处理。 */
  meanBenchmarkReturn?: number | null;
  /** 同基准子样本的标的收益减基准收益均值。 */
  meanExcessReturn?: number | null;
  /** 有效对齐基准样本数。 */
  benchmarkSampleCount?: number;
  /** 该规则已保存记录数。 */
  recordCount?: number;
  /** 明确触发的记录数。 */
  triggeredCount?: number;
  /** 排除原因与数量；未触发、未成熟、重叠及时间不可得分别记录。 */
  exclusions?: Record<string, number>;
  /** 实际出现的规则版本，不能无提示合并。 */
  ruleVersions?: string[];
  /** 符合日期及后续收益窗口条件的实际样本数量。 */
  sampleCount: number;
  /** 样本后续收盘收益的均值，以小数存储，未计交易成本和滑点。 */
  meanReturn?: number | null;
  /** 样本中收益大于0的比例，范围0至1；不等于可成交胜率。 */
  positiveRatio?: number | null;
  /** 数据完整性状态；COMPLETE表示完整，证据或历史不足时不能按零值解释。 */
  dataStatus: string;
}

export interface BacktestResult {
  /** 本次验证方法版本。 */
  methodVersion?: string;
  /** 实际数据可用时点口径。 */
  availabilityMode?: 'RETROSPECTIVE' | 'POINT_IN_TIME';
  /** 不使用该日期之后的收益行情。 */
  evaluationDate?: string;
  /** 较晚留出区间首日。 */
  splitDate?: string | null;
  /** 小样本提示阈值，不代表方法已验证有效。 */
  minimumSamples?: number;
  /** 早期和较晚区间独立统计；早期收益不能跨越留出起点。 */
  validationWindows?: {
    label: string;
    start: string;
    end: string;
    statistics: Record<string, Record<string, SignalStatistic>>;
  }[];
  /** 数据完整性状态；COMPLETE表示完整，证据或历史不足时不能按零值解释。 */
  dataStatus: string;
  /** 当前判定成立或无法判定的具体原因。 */
  reason?: string | null;
  /** 所选信号后续收盘收益的基础统计，不含成本、滑点和可成交性。 */
  statistics?: Record<string, Record<string, SignalStatistic>>;
  /** 当前范围、数据时点和统计方法的适用边界。 */
  limitations?: string[];
}

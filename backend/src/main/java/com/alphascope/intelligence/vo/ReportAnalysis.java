package com.alphascope.intelligence.vo;

import com.alphascope.fundamental.vo.ForecastView;
import com.alphascope.market.vo.MarketAcquisition;
import com.alphascope.market.vo.MarketObservation;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;

/**
 * 仅从本修订冻结输入推导的可读分析，不读取后续行情或修改历史正文。
 *
 * @author chenxuefeng
 */
public record ReportAnalysis(
    /** 冻结分析结构标识；旧修订保留其生成时的标识。 */
    String version,

    /** 结构化事实状态：AVAILABLE可用、PARTIAL部分、DEMO演示或LEGACY_NO_MARKET_SNAPSHOT旧版缺少行情。 */
    String status,

    /** 基于当前冻结证据形成的研究摘要标题。 */
    String headline,

    /** 同一研究期间沪深300的复权收益，以小数存储；无证据时为空。 */
    Double benchmarkReturn,

    /** 当前报告关注标的的冻结行情事实列表。 */
    List<Stock> stocks,

    /** 各标的逐条规则评估，分别保留触发、未触发、证据不足和不适用。 */
    List<Assessment> assessments,

    /** 后续需要人工核对的事项列表。 */
    List<Check> checks,

    /** 当前报告尚未覆盖的数据或方法缺项。 */
    List<String> missingData,

    /** 价格口径、时点和方法适用范围的说明。 */
    List<String> methodNotes,

    /** 截至报告业务时点可用的已披露累计财报证据。 */
    List<FinancialFact> financials,

    /** 与报告业务日期对应的市场截面事实。 */
    MarketFact market,

    /** 研究模式：WATCHLIST只研究自选，PORTFOLIO启用持仓风险；LEGACY表示旧报告未保存。 */
    String researchMode,

    /** 有效主题研究样本或其证据诊断列表。 */
    List<ThemeResearch> themes,

    /** 基于冻结证据形成的研究核查建议，不是交易指令。 */
    List<ResearchAdvice> advice,

    /** 独立沪深市场分项观察；旧报告或尚无快照时可为空。 */
    MarketObservation marketObservation,

    /** 业务截止时点之前可见的公开盈利预测快照。 */
    List<ForecastView> forecasts,

    /** 本次各标的行情获取方式、校验区间与原采集时间。 */
    List<MarketAcquisition> marketAcquisition,

    /** 新方法声明后冻结的近两月量价与条件式减仓研究；旧修订为空且不追加JSON字段。 */
    @JsonInclude(JsonInclude.Include.NON_NULL) List<PriceVolumeStudy> priceVolume) {
  /**
   * 本报告采用的累计财报事实，按业务截止时点筛选。
   *
   * @author chenxuefeng
   */
  public record FinancialFact(
      /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
      String symbol,

      /** 标的中文名称，用于页面展示。 */
      String name,

      /** 累计财报的报告期末日期，按同报告期进行同比比较。 */
      String periodEnd,

      /** 截至报告业务日可用的累计财报类型，不混用单季度口径。 */
      String reportType,

      /** 材料公开发布时间；仅知日期时按供应商的保守可用时点处理。 */
      String publishedAt,

      /** 该证据最早可用于研究的时刻；不能倒填为未观测到的历史时间。 */
      String availableAt,

      /** 该项证据的实际供应商或原始来源标识。 */
      String source,

      /** 累计营业收入，单位人民币元；为空表示尚无可用证据。 */
      Double revenue,

      /** 累计归属于母公司股东的净利润，单位人民币元；允许亏损，空值表示缺失。 */
      Double netProfit,

      /** 累计经营活动现金流量净额，单位人民币元；需与历年相同累计期间比较。 */
      Double operatingCashflow,

      /** 累计毛利率，以小数存储；例如0.30表示30%，空值表示缺失。 */
      Double grossMargin,

      /** 累计营业收入同比增速，以小数存储；例如0.20表示20%。 */
      Double revenueYoy,

      /** 累计归母净利润同比增速，以小数存储；例如-0.10表示下降10%。 */
      Double profitYoy) {}

  /**
   * 业务日可用的沪深市场截面事实，明确区分样本覆盖与完整全A。
   *
   * @author chenxuefeng
   */
  public record MarketFact(
      /** 沪深截面覆盖状态，COMPLETE完整不等于已完成全A评级。 */
      String status,

      /** 研究覆盖范围；固定样本、沪深A股与完整全A不能相互替代。 */
      String scope,

      /** 该项证据的实际供应商或原始来源标识。 */
      String source,

      /** 成交金额，单位人民币元；缺失时不补造0。 */
      Double amount,

      /** 当日收盘上涨的有效交易股票家数。 */
      Integer advancers,

      /** 当日收盘下跌的有效交易股票家数。 */
      Integer decliners,

      /** 当日收盘持平的有效交易股票家数。 */
      Integer unchanged,

      /** 当日供应商明确标记停牌的股票家数。 */
      Integer suspended,

      /** 当日证券清单中应覆盖的交易股票数量。 */
      Integer expected,

      /** 实际取得有效日线的交易股票数量。 */
      Integer observed,

      /** 有效数据数量相对于规定覆盖分母的比例，以0至1小数表示。 */
      Double coverage) {}

  /**
   * 报告中单只关注标的的冻结价格、收益和规则数量。
   *
   * @author chenxuefeng
   */
  public record Stock(
      /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
      String symbol,

      /** 标的中文名称，用于页面展示。 */
      String name,

      /** 当前事实真正对应的交易日期；不能用报告生成日期替代。 */
      LocalDate asOf,

      /** 行情事实状态：TRADING当日有效、SUSPENDED证实停牌、MISSING无记录或STALE仅有旧日行情。 */
      String status,

      /** 该项证据的实际供应商或原始来源标识。 */
      String source,

      /** 原始行情写入批次，用于追溯本条事实。 */
      String batchId,

      /** 未复权收盘价，单位人民币元；缺失时不能当作0。 */
      Double close,

      /** 相邻两个有效收盘的同源复权收益，以小数存储。 */
      Double dayReturn,

      /** 报告研究期间的同源复权收益；日报取当日，周报按有效交易日区间计算。 */
      Double periodReturn,

      /** 最近5个有K线交易日的复权收益，以小数存储；历史不足时为空。 */
      Double return5,

      /** 最近20个有K线交易日的复权收益，以小数存储；历史不足时为空。 */
      Double return20,

      /** 最近20个有K线交易日的同锚点复权收盘均价，单位元；含当日，历史不足时为空。 */
      Double ma20,

      /** 最近60个有K线交易日的同锚点复权收盘均价，单位元；含当日，历史不足时为空。 */
      Double ma60,

      /** 收盘价相对动态MA20的偏离比例，以小数存储。 */
      Double distanceMa20,

      /** 收盘价相对动态MA60的偏离比例，以小数存储。 */
      Double distanceMa60,

      /** 当日成交量相对前20个有K线交易日均量的倍数；证据不足时为空。 */
      Double volumeRatio,

      /** 20期标的收益比除以同期沪深300收益比再减1，以小数存储；日期不齐时为空。 */
      Double rs20,

      /** 分析日前20个有K线交易日的最低复权收盘价，使用当前分析锚点，单位元。 */
      Double previous20Low,

      /** 当前收盘与动态均线形成的趋势描述，不是固定买卖价格。 */
      String trend,

      /** 已触发的规则记录数量。 */
      int triggered,

      /** 有足够证据可评估的规则记录数量；不含证据不足或不适用。 */
      int reviewed,

      /** 缺少必要输入、无法评估的规则记录数量。 */
      int insufficient) {}

  /**
   * 单条规则依据的可展示指标；数值与单位必须一起解释。
   *
   * @author chenxuefeng
   */
  public record Metric(
      /** 指标的中文名称。 */
      String label,

      /** 指标数值；单位由unit给出，缺失时不得补成0。 */
      Double value,

      /** 指标计量单位，例如元、股、倍或小数比例。 */
      String unit) {}

  /**
   * 逐条规则评估及其可复核依据，区分未触发、证据不足和不适用。
   *
   * @author chenxuefeng
   */
  public record Assessment(
      /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
      String symbol,

      /** 标的中文名称，用于页面展示。 */
      String name,

      /** 该记录对应的业务交易日期。 */
      LocalDate date,

      /** 稳定规则代码，例如TREND_BREAK_20或PROFIT_SLOWDOWN。 */
      String ruleCode,

      /** 供研究页面展示的规则中文名称。 */
      String ruleName,

      /** 规则状态：TRIGGERED触发、NOT_TRIGGERED未触发、INSUFFICIENT_DATA不足或NOT_APPLICABLE不适用。 */
      String status,

      /** 规则严重度：HIGH高、MEDIUM中、LOW低。 */
      String severity,

      /** 本条判断采用的具体指标值或证据列表。 */
      List<Metric> evidence,

      /** 评估该规则仍缺少的输入名称，不能把缺失值当作未触发。 */
      List<String> missingInputs,

      /** 当前状态、判断依据或数据不足原因的文字说明。 */
      String explanation) {}

  /**
   * 用户后续可以执行的具体证据核查事项。
   *
   * @author chenxuefeng
   */
  public record Check(
      /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
      String symbol,

      /** 供研究页面展示的中文标题。 */
      String title,

      /** 待核查事项的具体操作说明。 */
      String detail) {}
}

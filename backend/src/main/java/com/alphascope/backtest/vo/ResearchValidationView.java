package com.alphascope.backtest.vo;

import com.alphascope.backtest.calculator.MarketScoreValidationCalculator;
import com.alphascope.backtest.dto.PortfolioReplaySettings;
import com.alphascope.backtest.dto.ResearchValidationRequest;
import com.alphascope.market.vo.ScopedScoreView;
import com.alphascope.market.vo.UniverseCoverageView;
import com.alphascope.signal.dto.RuleDefinitionSnapshot;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 一次真实研究验证的完整结果：已有信号、回顾重建和全A评分各自得出结论。
 *
 * @author chenxuefeng
 */
public record ResearchValidationView(
    /** 本次实际核查时间。 */
    Instant checkedAt,
    /** 冻结验证口径，禁止把后续方法静默套用到本次结果。 */
    String protocolVersion,
    /** 本次明确请求的验证类型。 */
    ResearchValidationRequest.Scope scope,
    /** 信号研究区间起点。 */
    LocalDate start,
    /** 收盘数据与收益评估截止日。 */
    LocalDate end,
    /** 全A效果验证结论，不由软件测试通过或局部样本收益代替。 */
    String fullMarketVerdict,
    /** 请求结束日的实际分项与覆盖缺口。 */
    ScopedScoreView scoreAtEnd,
    /** 结束日实际清单覆盖核验；未请求市场验证时为空。 */
    UniverseCoverageView coverageAtEnd,
    /** 研究区间内现行MARKET_V3已保存的完整全A评分天数。 */
    long qualifiedScoreDays,
    /** 五维评分对未来基准表现的完整冻结检验，不用自选技术规则冒充。 */
    MarketReplay marketReplay,
    /** 本次固定规则参数，未根据本次结果搜索或调整。 */
    RuleDefinitionSnapshot ruleDefinition,
    /** 本次开始前已在自选登记的股票；不冒充完整历史股票池。 */
    List<StockValidation> stocks,
    /** 真实不足及为什么不能直接判为有效。 */
    List<String> blockers,
    /** 解释回顾样本、交易成本和样本选择等局限。 */
    List<String> limitations,
    /** 共用资金口径，按同一批证券的冻结输入复算。 */
    @JsonInclude(JsonInclude.Include.NON_NULL) PortfolioReplay portfolioReplay) {

  /**
   * 共用资金模拟的冻结参数与结果指纹。
   *
   * @author chenxuefeng
   */
  public record PortfolioReplay(
      PortfolioReplaySettings settings, StrategySimulation simulation, String sha256) {}

  /**
   * 全A评分时间序列检验的冻结输入和输出。
   *
   * @author chenxuefeng
   */
  public record MarketReplay(
      /** 当前已保存评分版本回顾；不是严格时点证明。 */
      String origin,
      /** 评分、基准、日历及区间的精确JSON。 */
      String inputJson,
      /** 输入文本SHA256。 */
      String inputSha256,
      /** 两个区间及三个交易日窗口的配对结果。 */
      List<MarketScoreValidationCalculator.Result> results,
      /** 冻结结果SHA256。 */
      String resultSha256) {}

  /**
   * 单只自选股的已有信号核验及独立回顾实验。
   *
   * @author chenxuefeng
   */
  public record StockValidation(
      /** 证券统一代码。 */
      String symbol,
      /** 实际可用真实日线数量，含指标预热。 */
      int priceDays,
      /** 本次真实数据第一天。 */
      LocalDate firstPriceDate,
      /** 本次真实数据最后一天。 */
      LocalDate lastPriceDate,
      /** 查询区间已有的规则记录数，不含本次重建。 */
      int savedRuleRecords,
      /** 已有信号的当前版本回顾。 */
      BacktestView savedRetrospective,
      /** 已有不可变信号及行情按历史保存时刻重建的严格统计。 */
      BacktestView savedPointInTime,
      /** 五条技术规则逐日重建结果；失败时为空并提供具体原因。 */
      Replay replay,
      /** 日历或输入不满足时的明确失败原因；正常时为空。 */
      String failure) {}

  /**
   * 独立回顾实验的精确文本和指纹，不写业务信号表。
   *
   * @author chenxuefeng
   */
  public record Replay(
      /** RETROSPECTIVE_RECONSTRUCTION，不能标成严格时点可得。 */
      String origin,
      /** 完整统计输入的精确JSON文本，用于脱离现有数据库复算。 */
      String inputJson,
      /** 精确输入文本的SHA256。 */
      String inputSha256,
      /** 从原价生成的逐日指标证据精确文本。 */
      String indicatorJson,
      /** 逐日指标证据SHA256。 */
      String indicatorSha256,
      /** 从指标和阈值生成的逐条规则结果精确文本。 */
      String signalJson,
      /** 逐条规则结果SHA256。 */
      String signalSha256,
      /** 实际冻结的统计输出。 */
      BacktestView result,
      /** 规范序列化结果的SHA256。 */
      String resultSha256,
      /** 可选策略执行扩展；旧任务为空，不在读取时补算。 */
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          StrategySimulation strategy,
      /** 策略输出文本指纹，方法版本包含在输出中。 */
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          String strategySha256) {
    /** 兼容已有冻结任务构造，不追加尚未执行的策略扩展。 */
    public Replay(
        String origin,
        String inputJson,
        String inputSha256,
        String indicatorJson,
        String indicatorSha256,
        String signalJson,
        String signalSha256,
        BacktestView result,
        String resultSha256) {
      this(
          origin,
          inputJson,
          inputSha256,
          indicatorJson,
          indicatorSha256,
          signalJson,
          signalSha256,
          result,
          resultSha256,
          null,
          null);
    }
  }
}

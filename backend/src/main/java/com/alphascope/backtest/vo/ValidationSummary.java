package com.alphascope.backtest.vo;

import com.alphascope.backtest.calculator.MarketScoreValidationCalculator;
import com.alphascope.backtest.dto.ResearchValidationRequest;
import com.alphascope.market.vo.ScopedScoreView;
import com.alphascope.market.vo.UniverseCoverageView;
import com.alphascope.signal.dto.RuleDefinitionSnapshot;
import java.time.LocalDate;
import java.util.List;

/**
 * 验证页面的轻量结果，不返回原价、逐日指标或大信号数组。
 *
 * @author chenxuefeng
 */
public record ValidationSummary(
    /** 明确的验证范围。 */
    ResearchValidationRequest.Scope scope,
    /** 开始日期。 */
    LocalDate start,
    /** 收益截止。 */
    LocalDate end,
    /** 冻结协议。 */
    String protocolVersion,
    /** 实际冻结规则。 */
    RuleDefinitionSnapshot ruleDefinition,
    /** 全A方法结论，未请求时NOT_REQUESTED。 */
    String fullMarketVerdict,
    /** 请求日的实际分项。 */
    ScopedScoreView scoreAtEnd,
    /** 实测清单范围和缺项。 */
    UniverseCoverageView coverageAtEnd,
    /** 当前模型完整评分日期数。 */
    long qualifiedScoreDays,
    /** 分区间/窗口市场描述检验。 */
    List<MarketScoreValidationCalculator.Result> marketResults,
    /** 自选技术结果，不含原始输入。 */
    List<Stock> stocks,
    /** 本次无法确立有效性的具体原因。 */
    List<String> blockers,
    /** 方法局限。 */
    List<String> limitations,
    /** 价格、指标、规则、统计四层复算核对结果。 */
    ValidationVerification verification) {

  /**
   * 单只自选的统计摘要。
   *
   * @author chenxuefeng
   */
  public record Stock(
      /** 股票代码。 */
      String symbol,
      /** 含预热的实际价格数量。 */
      int priceDays,
      /** 实际行情开始日期。 */
      LocalDate firstPriceDate,
      /** 实际行情结束日期。 */
      LocalDate lastPriceDate,
      /** 原有规则评估数量。 */
      int savedRuleRecords,
      /** 原有信号的回顾统计状态。 */
      String savedRetrospectiveStatus,
      /** 严格时点状态。 */
      String savedPointInTimeStatus,
      /** 当前固定技术规则的逐区间描述统计。 */
      BacktestView reconstructed,
      /** 该证券缺项。 */
      String failure,
      /** 新任务才有的策略执行扩展，旧任务保持为空。 */
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          StrategySimulation strategy) {}

  /**
   * 显式裁剪原始输入；禁止页面接口意外返回数兆字节证据。
   *
   * @param report 完整冻结报告
   * @param verification 四层核对结果
   * @return 页面摘要
   */
  public static ValidationSummary from(
      ResearchValidationView report, ValidationVerification verification) {
    return new ValidationSummary(
        report.scope(),
        report.start(),
        report.end(),
        report.protocolVersion(),
        report.ruleDefinition(),
        report.fullMarketVerdict(),
        report.scoreAtEnd(),
        report.coverageAtEnd(),
        report.qualifiedScoreDays(),
        report.marketReplay() == null ? List.of() : report.marketReplay().results(),
        report.stocks().stream()
            .map(
                stock ->
                    new Stock(
                        stock.symbol(),
                        stock.priceDays(),
                        stock.firstPriceDate(),
                        stock.lastPriceDate(),
                        stock.savedRuleRecords(),
                        stock.savedRetrospective().dataStatus(),
                        stock.savedPointInTime().dataStatus(),
                        stock.replay() == null ? null : stock.replay().result(),
                        stock.failure(),
                        stock.replay() == null ? null : stock.replay().strategy()))
            .toList(),
        report.blockers(),
        report.limitations(),
        verification);
  }
}

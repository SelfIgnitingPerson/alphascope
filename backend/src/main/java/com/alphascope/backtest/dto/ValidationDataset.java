package com.alphascope.backtest.dto;

import com.alphascope.backtest.calculator.MarketScoreValidationCalculator;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.vo.ScopedScoreView;
import com.alphascope.market.vo.UniverseCoverageView;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 一致读完成后脱离数据库事务的验证数据，CPU计算阶段不长期占用数据库连接。
 *
 * @author chenxuefeng
 */
public record ValidationDataset(
    /** 排队时冻结的条件。 */
    ValidationCommand command,
    /** 结束日评分，未请求市场验证时为空。 */
    ScopedScoreView score,
    /** 实际清单覆盖证明。 */
    UniverseCoverageView coverage,
    /** 市场评分检验输入，未请求时为空。 */
    MarketScoreValidationCalculator.Input market,
    /** 当前模型完整合格评分日期数。 */
    long qualifiedScoreDays,
    /** 本次固定名单的每只证券资料。 */
    List<Stock> stocks) {
  /**
   * 同一数据库快照中读取的一只股票资料。
   *
   * @author chenxuefeng
   */
  public record Stock(
      /** 证券代码。 */
      String symbol,
      /** 含固定预热的同源股票行情。 */
      List<MarketBarDO> bars,
      /** 同一预热范围的基准行情。 */
      List<MarketBarDO> benchmark,
      /** 市场开市日。 */
      Set<LocalDate> calendar,
      /** 同源证据排除确认停牌后的应有股票日期。 */
      Set<LocalDate> stockDates,
      /** 原有信号的回顾输入。 */
      SignalEvaluationInput retrospective,
      /** 原有不可变信号的严格时点输入。 */
      SignalEvaluationInput pointInTime,
      /** 不能获取可核验重建输入的具体原因。 */
      String failure) {}
}

package com.alphascope.backtest.calculator;

import com.alphascope.backtest.dto.TechnicalReplayInput;
import com.alphascope.intelligence.calculator.MediumTermResearchCalculator;
import com.alphascope.intelligence.dto.HoldingResearchInput;
import com.alphascope.intelligence.dto.StockEvidence;
import com.alphascope.intelligence.vo.HoldingResearchView.Horizon;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import com.alphascope.market.dto.MarketBarDO;
import java.time.LocalDate;
import java.util.NavigableMap;

/**
 * 收盘时可得的候选信号。各对照组复用同一天的研究计算。
 *
 * @author chenxuefeng
 */
final class ReplaySignals {
  private final PriceVolumeStudy study;
  private final HoldingResearchInput mediumInput;
  private Horizon mediumOriginal;
  private MediumTermResearchCalculator.TrendConditions mediumTrend;
  private boolean mediumCalculated;

  ReplaySignals(
      LocalDate date, NavigableMap<LocalDate, MarketBarDO> prices, TechnicalReplayInput input) {
    study = StrategyReplayCalculator.study(date, prices, input);
    var history = prices.headMap(date, true).descendingMap().values().stream().limit(65).toList();
    mediumInput =
        HoldingResearchInput.builder()
            .asOf(date)
            .expectedDates(input.stockTradingDates())
            .stock(StockEvidence.builder().history(history).build())
            .build();
  }

  PriceVolumeStudy study() {
    return study;
  }

  /** 中期原版的四个行动复用产品计算器，退出优先于减仓和加仓。 */
  Order medium(ReplayRule rule, boolean holding) {
    if (rule == ReplayRule.MEDIUM_ORIGINAL) {
      if (mediumOriginal == null)
        mediumOriginal = MediumTermResearchCalculator.calculate(mediumInput, study);
      var actions = mediumOriginal.actions();
      if (holding
          && actions.stream()
              .anyMatch(a -> a.code().equals("MEDIUM_EXIT") && a.status().equals("WATCH")))
        return new Order(-2, null);
      if (holding
          && actions.stream()
              .anyMatch(a -> a.code().equals("MEDIUM_REDUCE") && a.status().equals("WATCH")))
        return new Order(-1, null);
      String action = holding ? "MEDIUM_ADD" : "MEDIUM_OPEN";
      return actions.stream()
          .filter(a -> a.code().equals(action) && a.status().equals("CONDITIONAL_MATCH"))
          .findFirst()
          .map(a -> new Order(1, a.stopReference()))
          .orElse(Order.NONE);
    }
    if (!mediumCalculated) {
      mediumTrend = MediumTermResearchCalculator.trendConditions(mediumInput, study);
      mediumCalculated = true;
    }
    if (mediumTrend == null) return Order.NONE;
    if (holding) return mediumTrend.exit() ? new Order(-2, null) : Order.NONE;
    boolean accepted =
        mediumTrend.trend()
            && switch (rule) {
              case MEDIUM_TREND -> true;
              case MEDIUM_RETEST -> mediumTrend.retest();
              case MEDIUM_VOLUME -> mediumTrend.retest() && mediumTrend.volume();
              case MEDIUM_RISK ->
                  mediumTrend.retest()
                      && mediumTrend.volume()
                      && mediumTrend.structureRiskAllowed();
              default -> false;
            };
    return accepted && mediumTrend.sizingStop() != null
        ? new Order(1, mediumTrend.sizingStop())
        : Order.NONE;
  }

  /** 委托方向统一为加仓或建仓、减仓、退出、等待。 @author chenxuefeng */
  record Order(int direction, Double stop) {
    static final Order NONE = new Order(0, null);
  }
}

package com.alphascope.backtest.calculator;

import com.alphascope.market.dto.MarketBarDO;
import java.time.LocalDate;
import java.util.NavigableMap;

/**
 * 日线回放的开盘估算。容量仅采用前序同源成交额。
 *
 * @author chenxuefeng
 */
final class ReplayOpening {
  private static final int LOOKBACK = 20;
  private static final double GAP_LIMIT = .045;

  private ReplayOpening() {}

  /** 使用开盘价与前收或已核验除权参考价做保守跳空检查。 */
  static boolean restricted(double open, double reference) {
    return !Double.isFinite(reference)
        || reference <= 0
        || Math.abs(open / reference - 1) >= GAP_LIMIT;
  }

  /** 返回收盘前已知的日均成交额，供次日容量估算，缺历史时为零。 */
  static double priorTurnover(NavigableMap<LocalDate, MarketBarDO> prices, MarketBarDO bar) {
    return prices.headMap(bar.getTradeDate(), false).descendingMap().values().stream()
        .limit(LOOKBACK)
        .filter(
            row ->
                Boolean.FALSE.equals(row.getSynthetic())
                    && java.util.Objects.equals(row.getSource(), bar.getSource())
                    && row.getAmount() != null
                    && row.getAmount().signum() > 0)
        .mapToDouble(row -> row.getAmount().doubleValue())
        .average()
        .orElse(0);
  }
}

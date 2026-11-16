package com.alphascope.market.calculator;

import com.alphascope.market.vo.MarketBreadthView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 同日供应商证券清单与未复权日K线交叉核验。沪深A股统计不包含北交所，不输出全A评分。
 *
 * @author chenxuefeng
 */
public final class MarketBreadthCalculator {
  private MarketBreadthCalculator() {}

  public static boolean stock(String code) {
    return code != null && code.matches("sh\\.6[0-9]{5}|sz\\.(?:00|30)[0-9]{4}");
  }

  public static MarketBreadthView calculate(
      LocalDate date, List<Map<String, String>> universe, List<Map<String, String>> rows) {
    Map<String, String> expected = new TreeMap<>();
    for (var r : universe) {
      if (stock(r.get("code"))) {
        String status = r.get("tradeStatus");
        if (!Set.of("0", "1", "UNKNOWN").contains(status == null ? "" : status)) {
          throw new IllegalArgumentException("UNKNOWN_UNIVERSE_STATUS");
        }
        if (expected.putIfAbsent(r.get("code"), status) != null) {
          throw new IllegalArgumentException("DUPLICATE_UNIVERSE_CODE");
        }
      }
    }
    if (expected.size() < 4000) {
      throw new IllegalArgumentException("UNIVERSE_INCOMPLETE");
    }
    BigDecimal amount = BigDecimal.ZERO;
    int up = 0, down = 0, flat = 0, suspended = 0;
    Set<String> seen = new HashSet<>();
    for (var r : rows) {
      String code = r.get("code");
      if (!stock(code)) {
        continue;
      }
      if (!date.toString().equals(r.get("date"))
          || !expected.containsKey(code)
          || !seen.add(code)) {
        throw new IllegalArgumentException("MARKET_SNAPSHOT_SCOPE_MISMATCH");
      }
      if ("0".equals(r.get("tradestatus"))) {
        suspended++;
        continue;
      }
      if (!"1".equals(r.get("tradestatus"))
          || !"1".equals(expected.get(code))
          || !"3".equals(r.get("adjustflag"))) {
        throw new IllegalArgumentException("INVALID_DAILY_TRADE_STATUS");
      }
      var close = new BigDecimal(r.get("close"));
      var previous = new BigDecimal(r.get("preclose"));
      var turnover = new BigDecimal(r.get("amount"));
      if (close.signum() <= 0 || previous.signum() <= 0 || turnover.signum() < 0) {
        throw new IllegalArgumentException("INVALID_MARKET_NUMERIC");
      }
      amount = amount.add(turnover);
      int change = close.compareTo(previous);
      if (change > 0) {
        up++;
      } else if (change < 0) {
        down++;
      } else {
        flat++;
      }
    }
    // 部分供应商只在清单中返回停牌状态；该缺席属于有证据的停牌，不是缺失交易行情。
    int confirmedAbsentSuspended = 0;
    List<String> missing = new ArrayList<>();
    for (var e : expected.entrySet()) {
      if (!seen.contains(e.getKey())) {
        if (e.getValue().equals("0")) {
          confirmedAbsentSuspended++;
        } else {
          missing.add(e.getKey());
        }
      }
    }
    suspended += confirmedAbsentSuspended;
    var result = new MarketBreadthView();
    result.setBusinessDate(date);
    result.setUniverseScope("SH_SZ_A");
    result.setSource("BAOSTOCK");
    result.setStatus(missing.isEmpty() ? "COMPLETE" : "PARTIAL");
    result.setExpected(expected.size());
    result.setObserved(up + down + flat);
    result.setSuspended(suspended);
    result.setAdvancers(up);
    result.setDecliners(down);
    result.setUnchanged(flat);
    result.setAmount(amount);
    result.setCoverage((expected.size() - missing.size()) / (double) expected.size());
    result.setMissingCount(missing.size());
    result.setMissingSymbols(missing.stream().limit(30).toList());
    result.setLimitations(
        List.of(
            "仅沪深A股，不含北交所；按供应商当日证券清单核对覆盖率",
            "涨跌采用同日未复权收盘与供应商前收盘，停牌不计入涨跌家数",
            "尚无全市场MA20/MA60广度、新低家数及成交额5/20日均值"));
    return result;
  }
}

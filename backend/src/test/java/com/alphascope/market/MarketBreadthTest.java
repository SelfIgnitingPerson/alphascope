package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.market.calculator.MarketBreadthCalculator;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * 沪深市场截面覆盖、停牌及涨跌家数统计的验证。
 *
 * @author chenxuefeng
 */
class MarketBreadthTest {
  final LocalDate date = LocalDate.of(2026, 10, 30);

  List<Map<String, String>> universe() {
    var out = new ArrayList<Map<String, String>>();
    for (int i = 0; i < 4000; i++)
      out.add(Map.of("code", "sh." + (600000 + i), "tradeStatus", "1"));
    return out;
  }

  Map<String, String> row(String code) {
    return Map.of(
        "code",
        code,
        "date",
        date.toString(),
        "close",
        "11",
        "preclose",
        "10",
        "amount",
        "1000",
        "tradestatus",
        "1",
        "adjustflag",
        "3");
  }

  @Test
  void calculatesAllExpectedStocksAndExcludesFundsAndOtherMarkets() {
    var u = universe();
    u.add(Map.of("code", "sh.000001", "tradeStatus", "1"));
    u.add(Map.of("code", "sz.159001", "tradeStatus", "1"));
    u.add(Map.of("code", "sz.399001", "tradeStatus", "1"));
    u.add(Map.of("code", "bj.920001", "tradeStatus", "1"));
    var rows = universe().stream().map(r -> row(r.get("code"))).toList();
    var result = MarketBreadthCalculator.calculate(date, u, rows);
    assertEquals("COMPLETE", result.getStatus());
    assertEquals("SH_SZ_A", result.getUniverseScope());
    assertEquals(4000, result.getAdvancers());
    assertEquals(1.0, result.getCoverage());
    assertEquals(4000000, ((Number) result.getAmount()).doubleValue());
  }

  @Test
  void missingTradingStocksArePartialNotZeroDecliners() {
    var result = MarketBreadthCalculator.calculate(date, universe(), List.of(row("sh.600000")));
    assertEquals("PARTIAL", result.getStatus());
    assertEquals(3999, result.getMissingCount());
    assertEquals(1, result.getAdvancers());
    assertEquals(0, result.getDecliners());
  }

  @Test
  void absentButConfirmedSuspendedStockIsNotADataGap() {
    var u = universe();
    u.set(1, Map.of("code", "sh.600001", "tradeStatus", "0"));
    var rows =
        u.stream()
            .filter(r -> r.get("tradeStatus").equals("1"))
            .map(r -> row(r.get("code")))
            .toList();
    var result = MarketBreadthCalculator.calculate(date, u, rows);
    assertEquals("COMPLETE", result.getStatus());
    assertEquals(1, result.getSuspended());
    assertEquals(3999, result.getObserved());
  }

  @Test
  void unverifiedTradeStatusRemainsAMissingRecordNotAConfirmedSuspension() {
    var u = universe();
    u.set(0, Map.of("code", "sh.600000", "tradeStatus", "UNKNOWN"));
    var rows = u.stream().skip(1).map(r -> row(r.get("code"))).toList();
    var result = MarketBreadthCalculator.calculate(date, u, rows);
    assertEquals("PARTIAL", result.getStatus());
    assertEquals(1, result.getMissingCount());
    assertEquals(0, result.getSuspended());
    assertEquals(3999, result.getObserved());
  }

  @Test
  void rejectsDuplicateRowsDateMismatchAndUndersizedUniverse() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MarketBreadthCalculator.calculate(
                date, universe(), List.of(row("sh.600000"), row("sh.600000"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MarketBreadthCalculator.calculate(
                date.plusDays(1), universe(), List.of(row("sh.600000"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MarketBreadthCalculator.calculate(
                date,
                List.of(Map.of("code", "sh.600000", "tradeStatus", "1")),
                List.of(row("sh.600000"))));
  }
}

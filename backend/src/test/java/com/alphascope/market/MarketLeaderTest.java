package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.market.calculator.MarketLeaderCalculator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * 全截面领先名单由历史成交额确定，缺少全市场历史或中间基准不缩小分母。
 *
 * @author chenxuefeng
 */
class MarketLeaderTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void actualTopTurnoverDecileIsSelectedAndConfirmed() {
    var fixture = fixture();
    var result =
        MarketLeaderCalculator.calculate(
            fixture.dates().getLast(),
            fixture.dates(),
            fixture.snapshots(),
            fixture.factors(),
            fixture.benchmark());
    assertEquals("AVAILABLE", result.status());
    assertEquals(List.of("sz.000010"), result.leaders());
    assertEquals(1, result.confirmed());
    assertEquals(10, result.coverage().expected());
  }

  @Test
  void missingStockHistoryDoesNotShrinkTheMarketDenominator() {
    var fixture = fixture();
    fixture.factors().remove("sz.000001");
    var result =
        MarketLeaderCalculator.calculate(
            fixture.dates().getLast(),
            fixture.dates(),
            fixture.snapshots(),
            fixture.factors(),
            fixture.benchmark());
    assertEquals("INSUFFICIENT_DATA", result.status());
    assertEquals(10, result.coverage().expected());
    assertEquals(9, result.coverage().observed());
  }

  @Test
  void missingMiddleBenchmarkIsRejected() {
    var fixture = fixture();
    ((com.fasterxml.jackson.databind.node.ArrayNode) fixture.benchmark()).remove(4);
    var result =
        MarketLeaderCalculator.calculate(
            fixture.dates().getLast(),
            fixture.dates(),
            fixture.snapshots(),
            fixture.factors(),
            fixture.benchmark());
    assertEquals("INSUFFICIENT_DATA", result.status());
  }

  private Fixture fixture() {
    List<LocalDate> dates = new ArrayList<>();
    Map<LocalDate, JsonNode> snapshots = new TreeMap<>();
    Map<String, NavigableMap<LocalDate, Double>> factors = new TreeMap<>();
    var benchmark = json.createArrayNode();
    for (int i = 0; i < 21; i++) {
      LocalDate day = LocalDate.of(2026, 1, 1).plusDays(i);
      dates.add(day);
      var snapshot = json.createObjectNode();
      snapshot.putObject("metrics").put("status", "COMPLETE");
      var daily = snapshot.putObject("raw").putArray("daily");
      for (int j = 1; j <= 10; j++) {
        String code = "sz." + String.format("%06d", j);
        daily
            .addObject()
            .put("code", code)
            .put("tradestatus", "1")
            .put("close", 10 + i * .3)
            .put("amount", j * 1000);
        var event = new TreeMap<LocalDate, Double>();
        event.put(LocalDate.MIN, 1d);
        factors.put(code, event);
      }
      snapshots.put(day, snapshot);
      benchmark
          .addObject()
          .put("trade_date", day.toString())
          .put("close", 100 + i * .1)
          .put("factor", 1);
    }
    return new Fixture(dates, snapshots, factors, benchmark);
  }

  /**
   * 无网络固定输入。
   *
   * @author chenxuefeng
   */
  private record Fixture(
      List<LocalDate> dates,
      Map<LocalDate, JsonNode> snapshots,
      Map<String, NavigableMap<LocalDate, Double>> factors,
      JsonNode benchmark) {}
}

package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.market.calculator.MarketObservationCalculator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * 市场趋势、量能、广度分项及证据不足边界的验证。
 *
 * @author chenxuefeng
 */
class MarketObservationTest {
  final ObjectMapper json = new ObjectMapper();
  final LocalDate end = LocalDate.of(2026, 10, 30);
  final List<LocalDate> dates = new ArrayList<>();
  final Map<LocalDate, JsonNode> data = new TreeMap<>();
  final Map<String, NavigableMap<LocalDate, Double>> factors = new HashMap<>();
  final ArrayNode benchmark = json.createArrayNode();
  final MarketObservationCalculator.Parameters params =
      new MarketObservationCalculator.Parameters(.98, 1.1, .9, .6, .4);

  MarketObservationTest() {
    for (var d = end.minusDays(100); !d.isAfter(end); d = d.plusDays(1))
      if (d.getDayOfWeek().getValue() < 6) dates.add(d);
    while (dates.size() > 62) dates.removeFirst();
    for (int i = 0; i < dates.size(); i++) {
      var root = json.createObjectNode();
      root.putObject("metrics")
          .put("status", "COMPLETE")
          .put("observed", 2)
          .put("suspended", 1)
          .put("advancers", 1)
          .put("amount", i == 61 ? 200 : 100);
      var raw = root.putObject("raw").putArray("daily");
      raw.addObject()
          .put("code", "sh.600000")
          .put("date", dates.get(i).toString())
          .put("tradestatus", "1")
          .put("close", i >= 60 ? (100 + i) / 2d : 100 + i);
      raw.addObject()
          .put("code", "sz.000001")
          .put("date", dates.get(i).toString())
          .put("tradestatus", "1")
          .put("close", 200 - i);
      data.put(dates.get(i), root);
      benchmark
          .addObject()
          .put("trade_date", dates.get(i).toString())
          .put("close", i == 61 ? 50 : 100 + i)
          .put("factor", 1)
          .put("source", "BAOSTOCK")
          .put("is_synthetic", false);
    }
    var events = json.createArrayNode();
    events.addObject().put("dividOperateDate", dates.get(60).toString()).put("backAdjustFactor", 2);
    factors.put("sh.600000", MarketObservationCalculator.factors(events, end));
    factors.put("sz.000001", MarketObservationCalculator.factors(json.createArrayNode(), end));
  }

  @Test
  void fullMarketIncludesBeijingInBothMovingAverageDenominators() {
    for (int i = 0; i < dates.size(); i++) {
      var node = (ObjectNode) data.get(dates.get(i));
      ((ObjectNode) node.path("metrics")).put("observed", 3);
      ((ArrayNode) node.path("raw").path("daily"))
          .addObject()
          .put("code", "bj.920000")
          .put("date", dates.get(i).toString())
          .put("tradestatus", "1")
          .put("close", 100 + i);
    }
    factors.put("bj.920000", MarketObservationCalculator.factors(json.createArrayNode(), end));
    var input =
        new com.alphascope.market.dto.MarketObservationInput(
            end, dates, data, factors, benchmark, params, "FULL_A");
    var result = MarketObservationCalculator.calculateScope(input);
    assertEquals("FULL_A", result.scope());
    assertEquals("FULL_A_OBSERVATION_V1", result.version());
    assertEquals(3, result.breadth().trading());
    assertEquals(3, result.breadth().valid20());
    assertEquals(3, result.breadth().valid60());
    assertEquals(2d / 3, result.breadth().ratio20(), 1e-12);
    factors.remove("bj.920000");
    var missing = MarketObservationCalculator.calculateScope(input);
    assertNull(missing.breadth().ratio20());
    assertEquals(2d / 3, missing.breadth().coverage20(), 1e-12);
  }

  @Test
  void splitAdjustedBreadthAndTradingDenominatorAreCorrect() {
    var r = MarketObservationCalculator.calculate(end, dates, data, factors, benchmark, params);
    assertEquals("AVAILABLE", r.status());
    assertEquals(2, r.breadth().trading());
    assertEquals(1, r.breadth().suspended());
    assertEquals(2, r.breadth().valid20());
    assertEquals(1, r.breadth().above20());
    assertEquals(.5, r.breadth().ratio20());
    assertEquals(.5, r.breadth().ratio60());
    assertEquals(1, r.breadth().coverage20());
  }

  @Test
  void risingTurnoverWithFallingIndexIsNotCalledPositive() {
    var r = MarketObservationCalculator.calculate(end, dates, data, factors, benchmark, params);
    assertEquals(120, r.turnover().average5());
    assertEquals(105, r.turnover().average20());
    assertEquals(200d / 105, r.turnover().ratio20(), 1e-12);
    assertEquals("放量下跌", r.turnover().judgment());
    assertEquals("趋势偏弱", r.trend().judgment());
  }

  @Test
  void missingFactorDoesNotBecomeAnUnadjustedObservationOrZero() {
    factors.remove("sh.600000");
    var r = MarketObservationCalculator.calculate(end, dates, data, factors, benchmark, params);
    assertEquals(1, r.breadth().valid20());
    assertEquals(.5, r.breadth().coverage20());
    assertNull(r.breadth().ratio20());
    assertEquals("INSUFFICIENT_DATA", r.breadth().status());
  }

  @Test
  void aMissingTradingDateCannotBeReplacedByAnEarlierSnapshot() {
    data.remove(dates.get(58));
    var r = MarketObservationCalculator.calculate(end, dates, data, factors, benchmark, params);
    assertNull(r.turnover().average20());
    assertEquals(19, r.turnover().completeDays());
    assertNull(r.breadth().ratio20());
  }

  @Test
  void futureFactorEventsAreNotUsedAndInvalidFactorsAreRejected() {
    var rows = json.createArrayNode();
    rows.addObject()
        .put("dividOperateDate", end.plusDays(1).toString())
        .put("backAdjustFactor", 10);
    assertEquals(1, MarketObservationCalculator.factors(rows, end).floorEntry(end).getValue());
    ((ObjectNode) rows.get(0)).put("dividOperateDate", end.toString()).put("backAdjustFactor", 0);
    assertThrows(
        IllegalArgumentException.class, () -> MarketObservationCalculator.factors(rows, end));
  }

  @Test
  void rawRowDateMismatchIsRejected() {
    ((ObjectNode) data.get(end).path("raw").path("daily").get(0)).put("date", "2026-10-29");
    assertThrows(
        IllegalArgumentException.class,
        () -> MarketObservationCalculator.calculate(end, dates, data, factors, benchmark, params));
  }
}

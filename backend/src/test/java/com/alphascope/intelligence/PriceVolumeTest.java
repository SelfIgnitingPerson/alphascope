package com.alphascope.intelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alphascope.intelligence.calculator.PriceVolumeCalculator;
import com.alphascope.intelligence.calculator.ReportAnalysisBuilder;
import com.alphascope.intelligence.dto.PriceVolumeWindow;
import com.alphascope.market.dto.MarketBarDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 量价研究的窗口、复权、量纲、未来隔离和止损失守回归；全部为明确的测试行情。
 *
 * @author chenxuefeng
 */
class PriceVolumeTest {
  private static final LocalDate DATE = LocalDate.of(2026, 11, 10);
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

  @Test
  void structuralStopUsesPriorBarsAndDoesNotFollowTheBreakdownLower() {
    var bars = bars();
    var normal = calculate(bars);
    setPrices(bars.getLast(), 60, 62, 1);
    var broken = calculate(bars);
    assertEquals("AVAILABLE", broken.status());
    assertEquals(95.0, broken.stopReference());
    assertEquals(normal.stopReference(), broken.stopReference());
    assertTrue(broken.stopReference() > broken.close());
    assertEquals("BREACHED", broken.stopStatus());
    assertEquals("REDUCE_REVIEW", broken.decision());
    assertEquals("PRIOR_20_LOW", broken.support().basis());
    assertFalse(broken.evidence().isEmpty());
  }

  @Test
  void heavySellingBelowAverageCanSuggestReductionBeforeTheStop() {
    var bars = bars();
    for (var row : bars.subList(bars.size() - 5, bars.size())) {
      row.setVolume(BigDecimal.valueOf(200));
      row.setAmount(BigDecimal.valueOf(20000));
    }
    setPrices(bars.getLast(), 97, 99, 95);
    var result = calculate(bars);
    assertEquals("ABOVE_REFERENCE", result.stopStatus());
    assertEquals("REDUCE_REVIEW", result.decision());
    assertEquals(2.0, result.volumeRatio5());
    assertEquals(-0.03, result.return5());
  }

  @Test
  void shrinkingRallyNearResistanceSuggestsConditionalProfitProtection() {
    var bars = bars();
    bars.get(bars.size() - 15).setHigh(BigDecimal.valueOf(110));
    double[] closes = {101, 103, 104, 106, 108};
    for (int i = 0; i < 5; i++) {
      var row = bars.get(bars.size() - 5 + i);
      setPrices(row, closes[i], closes[i] + 1, closes[i] - 1);
      row.setVolume(BigDecimal.valueOf(50));
      row.setAmount(BigDecimal.valueOf(5000));
    }
    var result = calculate(bars);
    assertEquals("PROFIT_PROTECTION_REVIEW", result.decision());
    assertEquals(0.5, result.volumeRatio5());
    assertTrue(result.resistances().stream().anyMatch(z -> z.lower() <= 110 && z.upper() >= 110));
    assertTrue(
        result.resistances().stream()
            .allMatch(z -> z.dates().stream().allMatch(d -> d.isBefore(DATE))));
  }

  @Test
  void newHighDoesNotInventAnOverheadTargetAndFutureBarsDoNotChangeTheResult() {
    var bars = bars();
    setPrices(bars.getLast(), 130, 132, 128);
    var result = calculate(bars);
    assertTrue(result.resistances().isEmpty());
    assertEquals("OBSERVE", result.decision());
    var future = row(DATE.plusDays(1));
    setPrices(future, 1, 2, 0.5);
    bars.add(future);
    assertEquals(result, calculate(bars));
  }

  @Test
  void splitPricesAndVolumesAreNormalizedToTheSameAnchor() {
    var baseline = bars();
    var split = bars();
    for (int i = 0; i < split.size(); i++) {
      var row = split.get(i);
      if (i < split.size() / 2) {
        setPrices(row, 200, 204, 196);
        row.setVolume(BigDecimal.valueOf(50));
      } else {
        row.setFactor(BigDecimal.valueOf(2));
      }
    }
    var a = calculate(baseline);
    var b = calculate(split);
    assertEquals(a.close(), b.close());
    assertEquals(a.atr14(), b.atr14());
    assertEquals(a.stopReference(), b.stopReference());
    assertEquals(1.0, b.volumeRatio());
    assertEquals(1.0, b.volumeRatio5());
    assertEquals(0.0, b.return5());
  }

  @Test
  void priceAdjustmentDoesNotPretendToAdjustShareCapitalOrTurnover() {
    var bars = bars();
    for (int i = bars.size() / 2; i < bars.size(); i++) {
      bars.get(i).setFactor(BigDecimal.valueOf(1.02));
    }
    var result = calculate(bars);
    assertEquals("TURNOVER_AMOUNT", result.volumeBasis());
    assertEquals(1.0, result.volumeRatio());
    assertEquals(1.0, result.volumeRatio5());
    bars.getLast().setAmount(null);
    assertEquals("INSUFFICIENT_DATA", calculate(bars).status());
  }

  @Test
  void missingCalendarDaySourceOrFactorNeverBecomesARecommendation() {
    var bars = bars();
    bars.remove(5);
    var missing = calculate(bars);
    assertEquals("INSUFFICIENT_DATA", missing.status());
    assertTrue(missing.missing().getFirst().contains("缺少1日"));
    assertNull(missing.stopReference());
    bars = bars();
    bars.get(3).setSource("OTHER");
    assertEquals("UNAVAILABLE", calculate(bars).decision());
    bars = bars();
    bars.get(3).setFactor(null);
    assertEquals("INSUFFICIENT_DATA", calculate(bars).status());
    var noCalendar =
        PriceVolumeWindow.builder()
            .symbol("TEST.SZ")
            .name("测试")
            .date(DATE)
            .bars(bars())
            .expectedDates(Set.of())
            .build();
    assertEquals("INSUFFICIENT_DATA", PriceVolumeCalculator.calculate(noCalendar).status());
  }

  @Test
  void insufficientStaleDuplicateDemoAndSuspendedWindowsStayExplicit() {
    var bars = bars();
    var small = new ArrayList<>(bars.subList(bars.size() - 20, bars.size()));
    assertEquals(
        "INSUFFICIENT_DATA", PriceVolumeCalculator.calculate(window(small, dates(small))).status());
    bars.removeLast();
    assertEquals("STALE", calculate(bars).status());
    bars = bars();
    bars.add(bars.get(2));
    assertEquals("INSUFFICIENT_DATA", calculate(bars).status());
    var demo =
        PriceVolumeWindow.builder()
            .symbol("TEST.SZ")
            .name("测试")
            .date(DATE)
            .bars(bars())
            .expectedDates(dates(bars()))
            .synthetic(true)
            .build();
    assertEquals("DEMO", PriceVolumeCalculator.calculate(demo).status());
    var suspended =
        PriceVolumeWindow.builder()
            .symbol("TEST.SZ")
            .name("测试")
            .date(DATE)
            .bars(bars())
            .expectedDates(dates(bars()))
            .suspended(true)
            .build();
    assertEquals("SUSPENDED", PriceVolumeCalculator.calculate(suspended).status());
  }

  @Test
  void frozenMethodMarkerControlsNewAnalysisAndLegacyJsonRemainsUnchanged() {
    var input = JSON.createObjectNode();
    var stock = input.putArray("watchlist").addObject();
    stock.put("symbol", "TEST.SZ").put("name", "测试").put("suspended", false);
    stock.set("history", JSON.valueToTree(bars()));
    stock.set("expectedTradingDates", JSON.valueToTree(dates(bars())));
    var legacy = ReportAnalysisBuilder.build(input, "DAILY", DATE, false);
    assertNull(legacy.priceVolume());
    assertFalse(JSON.valueToTree(legacy).has("priceVolume"));
    input.put("priceVolumeMethod", PriceVolumeCalculator.VERSION);
    var before = input.deepCopy();
    var result = ReportAnalysisBuilder.build(input, "DAILY", DATE, false);
    assertEquals("AVAILABLE", result.priceVolume().getFirst().status());
    assertEquals(before, input);
    var frozen = JSON.valueToTree(result);
    var parsed = JSON.convertValue(frozen, com.alphascope.intelligence.vo.ReportAnalysis.class);
    assertEquals(result.priceVolume(), parsed.priceVolume());
  }

  @Test
  void olderFrozenVolumeProxyDoesNotAcquireANewTurnoverBasis() {
    var old = JSON.valueToTree(calculate(bars()));
    ((com.fasterxml.jackson.databind.node.ObjectNode) old).remove("volumeBasis");
    ((com.fasterxml.jackson.databind.node.ObjectNode) old).remove("strategies");
    ((com.fasterxml.jackson.databind.node.ObjectNode) old).put("version", "PRICE_VOLUME_2M_V1");
    var parsed = JSON.convertValue(old, com.alphascope.intelligence.vo.PriceVolumeStudy.class);
    assertNull(parsed.volumeBasis());
    assertEquals(old, JSON.valueToTree(parsed));
  }

  @Test
  void olderTurnoverStudyKeepsItsBasisWithoutAcquiringPositionStrategies() {
    var old = JSON.valueToTree(calculate(bars()));
    ((com.fasterxml.jackson.databind.node.ObjectNode) old).remove("strategies");
    ((com.fasterxml.jackson.databind.node.ObjectNode) old).put("version", "PRICE_VOLUME_2M_V2");
    var parsed = JSON.convertValue(old, com.alphascope.intelligence.vo.PriceVolumeStudy.class);
    assertEquals("TURNOVER_AMOUNT", parsed.volumeBasis());
    assertNull(parsed.strategies());
    assertEquals(old, JSON.valueToTree(parsed));
  }

  private static com.alphascope.intelligence.vo.PriceVolumeStudy calculate(List<MarketBarDO> bars) {
    return PriceVolumeCalculator.calculate(window(bars, dates(bars())));
  }

  private static PriceVolumeWindow window(List<MarketBarDO> bars, Set<LocalDate> expected) {
    return PriceVolumeWindow.builder()
        .symbol("TEST.SZ")
        .name("测试")
        .date(DATE)
        .bars(bars)
        .expectedDates(expected)
        .build();
  }

  private static Set<LocalDate> dates(List<MarketBarDO> bars) {
    return bars.stream().map(MarketBarDO::getTradeDate).collect(Collectors.toSet());
  }

  private static List<MarketBarDO> bars() {
    var rows = new ArrayList<MarketBarDO>();
    DATE.minusMonths(2)
        .datesUntil(DATE.plusDays(1))
        .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY)
        .forEach(d -> rows.add(row(d)));
    return rows;
  }

  private static MarketBarDO row(LocalDate date) {
    var row = new MarketBarDO();
    row.setTradeDate(date);
    row.setSource("TEST");
    row.setSynthetic(false);
    row.setFactor(BigDecimal.ONE);
    row.setVolume(BigDecimal.valueOf(100));
    row.setAmount(BigDecimal.valueOf(10000));
    setPrices(row, 100, 102, 98);
    return row;
  }

  private static void setPrices(MarketBarDO row, double close, double high, double low) {
    row.setOpen(BigDecimal.valueOf(close));
    row.setClose(BigDecimal.valueOf(close));
    row.setHigh(BigDecimal.valueOf(high));
    row.setLow(BigDecimal.valueOf(low));
  }
}

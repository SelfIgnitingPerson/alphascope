package com.alphascope.intelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.intelligence.calculator.HoldingResearchCalculator;
import com.alphascope.intelligence.dto.HoldingResearchInput;
import com.alphascope.intelligence.dto.StockEvidence;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.market.dto.MarketBarDO;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 多周期参考的真实模式、日期、同源、结构失效与长期估值缺项隔离。
 *
 * @author chenxuefeng
 */
class HoldingResearchTest {
  private final LocalDate date = LocalDate.of(2026, 11, 12);

  @Test
  void separatesPeriodsAndNeverTurnsMissingValuationIntoLongTermBuy() {
    var value = HoldingResearchCalculator.calculate(input(300));
    assertEquals(
        List.of("SHORT", "MEDIUM", "LONG"),
        value.horizons().stream().map(HoldingResearchView.Horizon::code).toList());
    var medium = value.horizons().get(1);
    assertEquals(4, medium.actions().size());
    assertNotNull(medium.actions().getFirst().observationZone());
    var longTerm = value.horizons().get(2);
    assertEquals("WAIT", longTerm.status());
    assertNull(longTerm.actions().getFirst().observationZone());
    assertTrue(
        longTerm.actions().getFirst().conditions().stream()
            .anyMatch(c -> c.label().equals("长期合理买价") && c.met() == null));
    assertTrue(longTerm.missing().stream().anyMatch(s -> s.contains("估值")));
  }

  @Test
  void demoStaleAndSuspendedCannotProduceCurrentTradePrices() {
    var base = input(300);
    var demo =
        HoldingResearchCalculator.calculate(
            HoldingResearchInput.builder()
                .stock(base.stock())
                .asOf(date)
                .expectedDates(base.expectedDates())
                .synthetic(true)
                .build());
    assertNull(demo.close());
    assertTrue(demo.horizons().stream().allMatch(h -> h.actions().isEmpty()));
    var stale =
        HoldingResearchCalculator.calculate(
            HoldingResearchInput.builder()
                .stock(base.stock())
                .asOf(date.plusDays(1))
                .expectedDates(base.expectedDates())
                .build());
    assertTrue(stale.horizons().stream().allMatch(h -> h.status().equals("UNAVAILABLE")));
    var stock = base.stock();
    var suspended =
        HoldingResearchCalculator.calculate(
            HoldingResearchInput.builder()
                .asOf(date)
                .expectedDates(base.expectedDates())
                .stock(
                    StockEvidence.builder()
                        .symbol(stock.symbol())
                        .name(stock.name())
                        .history(stock.history())
                        .signals(stock.signals())
                        .financials(stock.financials())
                        .indicators(stock.indicators())
                        .expectedTradingDates(stock.expectedTradingDates())
                        .suspended(true)
                        .build())
                .build());
    assertTrue(suspended.horizons().stream().allMatch(h -> h.actions().isEmpty()));
  }

  @Test
  void missingCalendarAndMixedMediumHistoryAreNotZeroFilled() {
    var base = input(300);
    var without =
        HoldingResearchCalculator.calculate(
            HoldingResearchInput.builder()
                .stock(base.stock())
                .asOf(date)
                .expectedDates(Set.of())
                .build());
    assertTrue(without.horizons().stream().allMatch(h -> h.status().equals("UNAVAILABLE")));
    base.stock().history().get(240).setSource("OTHER");
    var mixed = HoldingResearchCalculator.calculate(base);
    assertEquals("UNAVAILABLE", mixed.horizons().get(1).status());
  }

  @Test
  void oneMissingOlderTradingDayBlocksMediumButPreservesValidShortWindow() {
    var base = input(300);
    base.stock().history().remove(240);
    var value = HoldingResearchCalculator.calculate(base);
    assertNotEquals("UNAVAILABLE", value.horizons().getFirst().status());
    assertEquals("UNAVAILABLE", value.horizons().get(1).status());
  }

  @Test
  void currentCrashCannotLowerOriginalMediumExitReference() {
    var base = input(300);
    var before = HoldingResearchCalculator.calculate(base);
    var last = base.stock().history().getLast();
    last.setClose(BigDecimal.valueOf(50));
    last.setLow(BigDecimal.valueOf(49));
    last.setOpen(BigDecimal.valueOf(51));
    last.setHigh(BigDecimal.valueOf(52));
    var after = HoldingResearchCalculator.calculate(base);
    assertEquals(
        before.horizons().get(1).actions().getFirst().stopReference(),
        after.horizons().get(1).actions().getFirst().stopReference());
    assertEquals("RISK_BLOCKED", after.horizons().get(1).actions().getFirst().status());
    assertEquals("WATCH", after.horizons().get(1).actions().get(3).status());
  }

  @Test
  void futureBarsAndFutureFinancialRevisionsDoNotChangeHistoricalResult() {
    var base = input(300);
    var before = HoldingResearchCalculator.calculate(base);
    var future = bar(date.plusDays(1), 9999);
    base.stock().history().add(future);
    var later = financial();
    later.setPeriodEnd(date);
    later.setPublishedAt(date.plusDays(1).atStartOfDay());
    later.setAvailableAt(date.plusDays(1).atStartOfDay());
    later.setNetProfit(BigDecimal.valueOf(-99));
    base.stock().financials().add(later);
    assertEquals(before, HoldingResearchCalculator.calculate(base));
  }

  @Test
  void missingFactorInLongHistoryCannotProduceAnnualReference() {
    var base = input(300);
    base.stock().history().get(60).setFactor(null);
    var value = HoldingResearchCalculator.calculate(base);
    assertNotEquals("UNAVAILABLE", value.horizons().get(1).status());
    assertEquals("UNAVAILABLE", value.horizons().get(2).status());
    assertNull(value.horizons().get(2).actions().get(2).referencePrice());
  }

  @Test
  void unknownFinancialEvidenceDoesNotBecomeNoRisk() {
    var base = input(300);
    base.stock().financials().clear();
    var value = HoldingResearchCalculator.calculate(base);
    var risk = value.horizons().get(2).actions().get(2).conditions().getFirst();
    assertNull(risk.met());
    assertTrue(risk.observed().contains("待核验"));
    assertEquals("WAIT", value.horizons().get(2).actions().getFirst().status());
  }

  @Test
  void aMatchedReductionMustNeverBeSummarizedAsAnEntryMatch() {
    var base = input(300);
    var last = base.stock().history().getLast();
    var close = last.getClose().multiply(BigDecimal.valueOf(.85));
    last.setOpen(close);
    last.setClose(close);
    last.setHigh(close.add(BigDecimal.ONE));
    last.setLow(close.subtract(BigDecimal.ONE));
    last.setAmount(BigDecimal.valueOf(10000000));
    var result = HoldingResearchCalculator.calculate(base);
    var shortTerm = result.horizons().getFirst();
    assertTrue(
        shortTerm.actions().stream()
            .anyMatch(
                a -> "ANY".equals(a.conditionLogic()) && "CONDITIONAL_MATCH".equals(a.status())),
        "本例必须实际触发减仓方案");
    assertTrue(
        shortTerm.actions().stream()
            .filter(a -> "ALL".equals(a.conditionLogic()))
            .allMatch(a -> !"CONDITIONAL_MATCH".equals(a.status())));
    assertEquals("WATCH", shortTerm.status());
    assertTrue(shortTerm.conclusion().contains("暂停新增风险"));
    assertFalse(shortTerm.conclusion().contains("有方案满足本期行情条件"));
  }

  @Test
  void longExitNeedsConsecutivePublicSameSourceReports() {
    var base = input(300);
    var newer = base.stock().financials().getFirst();
    newer.setReportType("Q3_CUMULATIVE");
    newer.setSourceUrl("https://example.test/q3");
    newer.setProfitYoy(BigDecimal.valueOf(-.2));
    var earlier = financial();
    earlier.setPeriodEnd(LocalDate.of(2026, 6, 30));
    earlier.setReportType("H1_CUMULATIVE");
    earlier.setSourceUrl("https://example.test/h1");
    earlier.setProfitYoy(BigDecimal.valueOf(-.15));
    earlier.setAvailableAt(date.plusDays(1).atStartOfDay());
    base.stock().financials().add(earlier);
    var thesis =
        new com.alphascope.fundamental.dto.ValuationThesisInput(
            "300476.SZ",
            date.minusDays(40),
            date.plusMonths(1),
            BigDecimal.ONE,
            BigDecimal.valueOf(2),
            BigDecimal.TEN,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            true,
            "已登记公开经营证据与情景",
            "连续两期利润同比低于下限时复核退出",
            "https://example.test/thesis",
            "EASTMONEY_FINANCIAL",
            null,
            List.of(),
            new com.alphascope.fundamental.dto.ValuationExitPolicy(
                BigDecimal.valueOf(-.1), false, 2));
    var withPolicy =
        HoldingResearchInput.builder()
            .stock(base.stock())
            .expectedDates(base.expectedDates())
            .asOf(date)
            .valuationThesis(thesis)
            .build();

    var unverified =
        HoldingResearchCalculator.calculate(withPolicy).horizons().getLast().actions().getLast();
    assertEquals("UNAVAILABLE", unverified.status());
    assertNull(unverified.conditions().getFirst().met());

    earlier.setAvailableAt(date.minusDays(2).atStartOfDay());
    var actual = HoldingResearchCalculator.calculate(withPolicy).horizons().getLast();
    assertEquals("WATCH", actual.actions().getLast().status());
    assertEquals("RISK_BLOCKED", actual.actions().getFirst().status());
    earlier.setSource("DIFFERENT");
    assertEquals(
        "UNAVAILABLE",
        HoldingResearchCalculator.calculate(withPolicy)
            .horizons()
            .getLast()
            .actions()
            .getLast()
            .status());
  }

  private HoldingResearchInput input(int size) {
    var dates = new ArrayList<LocalDate>();
    var cursor = date;
    while (dates.size() < size) {
      if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY && cursor.getDayOfWeek() != DayOfWeek.SUNDAY)
        dates.addFirst(cursor);
      cursor = cursor.minusDays(1);
    }
    var bars = new ArrayList<MarketBarDO>();
    for (int i = 0; i < dates.size(); i++) bars.add(bar(dates.get(i), 100 + i * .1));
    return HoldingResearchInput.builder()
        .asOf(date)
        .expectedDates(dates.stream().collect(Collectors.toSet()))
        .stock(
            StockEvidence.builder()
                .symbol("300476.SZ")
                .name("fixture")
                .history(bars)
                .financials(new ArrayList<>(List.of(financial())))
                .indicators(List.of())
                .signals(List.of())
                .expectedTradingDates(dates)
                .build())
        .build();
  }

  private FinancialReport financial() {
    var f = new FinancialReport();
    f.setPeriodEnd(LocalDate.of(2026, 9, 30));
    f.setPublishedAt(LocalDate.of(2026, 10, 30).atTime(8, 0));
    f.setAvailableAt(f.getPublishedAt());
    f.setVersion(1);
    f.setSource("FIXTURE");
    f.setNetProfit(BigDecimal.valueOf(100));
    f.setOperatingCashflow(BigDecimal.valueOf(110));
    f.setRevenueYoy(BigDecimal.valueOf(.2));
    f.setProfitYoy(BigDecimal.valueOf(.3));
    return f;
  }

  private MarketBarDO bar(LocalDate day, double close) {
    var b = new MarketBarDO();
    b.setTradeDate(day);
    b.setOpen(BigDecimal.valueOf(close));
    b.setClose(BigDecimal.valueOf(close));
    b.setHigh(BigDecimal.valueOf(close + 1));
    b.setLow(BigDecimal.valueOf(close - 1));
    b.setFactor(BigDecimal.ONE);
    b.setAmount(BigDecimal.valueOf(1000000));
    b.setVolume(BigDecimal.valueOf(10000));
    b.setSource("FIXTURE");
    b.setSynthetic(false);
    return b;
  }

  @Test
  void explicitUserValuationIsDatedAndCannotBypassOtherConditions() {
    var base = input(300);
    var thesis =
        new com.alphascope.fundamental.dto.ValuationThesisInput(
            base.stock().symbol(),
            date.minusDays(1),
            date.plusDays(30),
            BigDecimal.valueOf(2),
            BigDecimal.valueOf(3),
            BigDecimal.valueOf(20),
            BigDecimal.valueOf(30),
            BigDecimal.valueOf(.2),
            true,
            "公开财报核对盈利与股本，行业倍数按用户假设",
            "现金流持续恶化则撤销原假设",
            "https://example.test/financial-report",
            "EASTMONEY_FINANCIAL");
    var input =
        HoldingResearchInput.builder()
            .stock(base.stock())
            .asOf(date)
            .expectedDates(base.expectedDates())
            .valuationThesis(thesis)
            .build();
    var horizon = HoldingResearchCalculator.calculate(input).horizons().get(2);
    var action = horizon.actions().getFirst();
    assertNull(action.observationZone());
    assertEquals(32.0, action.referencePrice());
    assertNotEquals("CONDITIONAL_MATCH", action.status());
    assertTrue(
        action.conditions().stream().anyMatch(c -> "独立估值证据".equals(c.label()) && c.met() == null));
    assertTrue(action.referenceLabel().contains("观察上限"));
    assertTrue(horizon.evidence().stream().anyMatch(v -> v.contains("用户年化EPS")));
    var expired =
        HoldingResearchInput.builder()
            .stock(base.stock())
            .asOf(date.plusDays(31))
            .expectedDates(base.expectedDates())
            .valuationThesis(thesis)
            .build();
    assertNull(
        HoldingResearchCalculator.calculate(expired).horizons().get(2).actions().stream()
            .findFirst()
            .map(HoldingResearchView.Action::observationZone)
            .orElse(null));
  }
}

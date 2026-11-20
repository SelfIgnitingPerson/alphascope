package com.alphascope.intelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.fundamental.dto.*;
import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.intelligence.calculator.*;
import com.alphascope.intelligence.dto.*;
import com.alphascope.intelligence.vo.*;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.portfolio.dto.PositionDO;
import com.alphascope.portfolio.entity.PortfolioAccount;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * 风险失守、跨口径财务与冻结执行的金额和时间边界回归。
 *
 * @author chenxuefeng
 */
class HighPriorityInvestorTest {
  private final LocalDate date = LocalDate.of(2026, 11, 18);

  private BigDecimal n(double v) {
    return BigDecimal.valueOf(v);
  }

  private FinancialReport report(LocalDate period, double profit, String type) {
    var f = new FinancialReport();
    f.setPeriodEnd(period);
    f.setReportType(type);
    f.setPublishedAt(period.plusDays(20).atTime(10, 0));
    f.setAvailableAt(period.plusDays(21).atStartOfDay());
    f.setSource("EASTMONEY_FINANCIAL");
    f.setVersion(1);
    f.setNetProfit(n(profit));
    f.setGrossMargin(n(.3));
    f.setRevenueYoy(n(.2));
    f.setRawJson(
        "{\"income\":{\"NETPROFIT\":"
            + profit
            + ",\"BASIC_EPS\":1,\"CURRENCY\":\"CNY\",\"ORG_TYPE\":\"1\"},\"balance\":{\"SHARE_CAPITAL\":100}}");
    return f;
  }

  private List<FinancialReport> reports() {
    return List.of(
        report(LocalDate.of(2025, 12, 31), 100, "ANNUAL"),
        report(LocalDate.of(2026, 9, 30), 90, "Q3_CUMULATIVE"),
        report(LocalDate.of(2025, 9, 30), 60, "Q3_CUMULATIVE"));
  }

  private ValuationThesisInput thesis(double eps, double pe, ValuationShareBasis shares) {
    return new ValuationThesisInput(
        "600000.SH",
        date,
        date.plusMonths(1),
        n(eps),
        n(eps + 1),
        n(pe),
        n(pe + 10),
        n(.2),
        true,
        "有材料支持的盈利与同行比较说明，用户不能手填同行PE",
        "订单与现金流恶化时重新检查",
        "https://example.test/report",
        "EASTMONEY_FINANCIAL",
        shares,
        List.of(
            new ComparablePeer(
                "600001.SH", n(1), "https://example.test/peer1", "同产品及同币种经营模型可比，具体材料需用户逐项核对"),
            new ComparablePeer(
                "600002.SH", n(1), "https://example.test/peer2", "同产品及同币种经营模型可比，具体材料需用户逐项核对"),
            new ComparablePeer(
                "600003.SH", n(1), "https://example.test/peer3", "同产品及同币种经营模型可比，具体材料需用户逐项核对")));
  }

  private MarketBarDO bar(LocalDate day, double open, double close) {
    var b = new MarketBarDO();
    b.setTradeDate(day);
    b.setOpen(n(open));
    b.setClose(n(close));
    b.setHigh(n(Math.max(open, close) + 1));
    b.setLow(n(Math.min(open, close) - 1));
    b.setAmount(n(100000000));
    b.setVolume(n(1000000));
    b.setFactor(n(1));
    b.setSource("SINA");
    b.setSynthetic(false);
    return b;
  }

  @Test
  void breachedOrIntradayTouchedRiskIsNeverZeroOrWithinBudget() {
    var p = new PositionDO();
    p.setSymbol("600000.SH");
    p.setQuantity(n(1000));
    p.setCostPrice(n(50));
    p.setAsOfDate(date);
    var a = new PortfolioAccount();
    a.setAsOfDate(date);
    a.setTotalAssets(n(100000));
    a.setRiskBudget(n(5000));
    for (double close : List.of(39d, 40d, 42d)) {
      var v =
          HoldingResearchView.builder()
              .symbol(p.getSymbol())
              .asOf(date)
              .quoteDate(date)
              .close(close)
              .priceVolume(
                  PriceVolumeStudy.builder().stopReference(40d).stopStatus("BREACHED").build())
              .build();
      var result = PositionImpactCalculator.calculate(new PositionImpactCalculator.Input(p, a, v));
      assertEquals("BREACHED", result.referenceState());
      assertNull(result.referenceLoss());
      assertTrue(result.checks().stream().noneMatch(s -> s.contains("未超登记预算")));
    }
  }

  @Test
  void recordedHistoryCanBeCarriedOnlyAsAnEstimateAndNotFromFuture() {
    var p = new PositionDO();
    p.setSymbol("600000.SH");
    p.setAsOfDate(date.minusDays(1));
    p.setQuantity(n(100));
    p.setCostPrice(n(30));
    p.setHistoryVerified(true);
    var v =
        HoldingResearchView.builder()
            .symbol(p.getSymbol())
            .asOf(date)
            .quoteDate(date)
            .close(50d)
            .turnover20(10000d)
            .priceVolume(PriceVolumeStudy.builder().stopReference(40d).build())
            .build();
    var result = PositionImpactCalculator.calculate(new PositionImpactCalculator.Input(p, null, v));
    assertEquals("ESTIMATED", result.state());
    assertEquals(5000, result.marketValue());
    assertEquals(50, result.liquidityDays());
    p.setAsOfDate(date.plusDays(1));
    assertEquals(
        "STALE",
        PositionImpactCalculator.calculate(new PositionImpactCalculator.Input(p, null, v)).state());
  }

  @Test
  void partialPortfolioCannotConfirmReferenceBudgetIsWithinLimit() {
    var position = new PositionDO();
    position.setSymbol("600000.SH");
    position.setAsOfDate(date);
    position.setQuantity(n(100));
    var account = new PortfolioAccount();
    account.setAsOfDate(date);
    account.setRiskBudget(n(1000));
    var impact = new PositionImpact("HELD", date, 5000.0, null, null, 300.0, List.of());
    var item =
        ResearchFocusView.Item.builder()
            .symbol(position.getSymbol())
            .positionImpact(impact)
            .build();
    var input =
        new PortfolioImpactCalculator.Input(date, List.of(position), account, List.of(item));
    assertEquals("UNKNOWN", PortfolioImpactCalculator.calculate(input).referenceBudgetStatus());
    account.setPositionsComplete(true);
    assertEquals(
        "WITHIN_REFERENCE_ONLY",
        PortfolioImpactCalculator.calculate(input).referenceBudgetStatus());
  }

  @Test
  void realisticPeersAndShareArithmeticCompleteAuditButOptimismCannotRaiseBound() {
    var basis = new ValuationShareBasis(n(100), n(100), n(1), "https://example.test/shares");
    var peers =
        List.of(
            new ValuationAuditInput.PeerInput("600001.SH", reports(), bar(date, 26, 26)),
            new ValuationAuditInput.PeerInput("600002.SH", reports(), bar(date, 39, 39)),
            new ValuationAuditInput.PeerInput("600003.SH", reports(), bar(date, 52, 52)));
    var checked =
        ValuationAuditCalculator.calculate(
            new ValuationAuditInput(date, thesis(3, 50, basis), reports(), peers));
    assertEquals("CHECKED_WITH_LIMITS", checked.status());
    assertEquals(1.3, checked.observedTtmEps());
    assertEquals(20, checked.peerPeLowerQuartile());
    assertEquals(20.8, checked.conservativeCap(), 1e-8);
    var optimistic =
        ValuationAuditCalculator.calculate(
            new ValuationAuditInput(date, thesis(8, 100, basis), reports(), peers));
    assertEquals(checked.conservativeCap(), optimistic.conservativeCap());
    var bad = new ValuationShareBasis(n(10000), n(10000), n(1), "https://example.test/wrong-unit");
    var rejected =
        ValuationAuditCalculator.calculate(
            new ValuationAuditInput(date, thesis(3, 50, bad), reports(), peers));
    assertEquals("INCONSISTENT", rejected.status());
    assertNull(rejected.conservativeCap());
  }

  @Test
  void incompatibleOrMissingPeersCannotCreateAnIndependentPass() {
    var basis = new ValuationShareBasis(n(100), n(100), n(1), "https://example.test/shares");
    var incompatible = reports();
    incompatible.get(1).setGrossMargin(n(.9));
    var peers =
        List.of(
            new ValuationAuditInput.PeerInput("600001.SH", reports(), bar(date, 26, 26)),
            new ValuationAuditInput.PeerInput("600002.SH", incompatible, bar(date, 39, 39)),
            new ValuationAuditInput.PeerInput("600003.SH", reports(), null));
    var result =
        ValuationAuditCalculator.calculate(
            new ValuationAuditInput(date, thesis(3, 50, basis), reports(), peers));
    assertEquals("INCOMPLETE", result.status());
    assertNull(result.conservativeCap());
    assertEquals(2, result.peers().stream().filter(v -> "EXCLUDED".equals(v.status())).count());
  }

  private ForwardInput forward(List<MarketBarDO> bars, List<LocalDate> days) {
    return new ForwardInput(
        "fixture-hash",
        "600000.SH",
        LocalDate.of(2026, 10, 30),
        "SINA",
        100.0,
        1.0,
        5,
        ForwardProtocol.current(),
        days,
        bars,
        List.of(),
        java.util.stream.IntStream.rangeClosed(1, 7)
            .mapToObj(
                i -> {
                  var c = new com.alphascope.market.dto.CalendarRevisionDO();
                  var d = LocalDate.of(2026, 10, 30).plusDays(i);
                  c.setTradeDate(d);
                  c.setOpen(
                      d.getDayOfWeek() != DayOfWeek.SATURDAY
                          && d.getDayOfWeek() != DayOfWeek.SUNDAY);
                  c.setSource("EXCHANGE_CALENDAR");
                  return c;
                })
            .toList());
  }

  private List<LocalDate> days() {
    return List.of(
        LocalDate.of(2026, 11, 2),
        LocalDate.of(2026, 11, 3),
        LocalDate.of(2026, 11, 4),
        LocalDate.of(2026, 11, 5),
        LocalDate.of(2026, 11, 6));
  }

  private List<MarketBarDO> bars() {
    var bars = new ArrayList<MarketBarDO>();
    bars.add(bar(LocalDate.of(2026, 10, 30), 100, 100));
    int i = 0;
    for (var d : days()) {
      bars.add(bar(d, 100 + 2 * i, 102 + 2 * i));
      i++;
    }
    return bars;
  }

  @Test
  void forwardWaitsForTradingSessionsAndChargesBothSides() {
    assertEquals(
        "WAITING_WINDOW",
        ForwardWindowCalculator.calculate(forward(bars(), days().subList(0, 4))).status());
    var calculated = ForwardWindowCalculator.calculate(forward(bars(), days()));
    assertEquals("CALCULATED", calculated.status());
    assertTrue(calculated.netReturn() > 0 && calculated.netReturn() < .1);
    assertTrue(calculated.fees() > 0);
    assertNull(calculated.benchmarkReturn());
    assertEquals(days().getFirst(), calculated.entryDate());
  }

  @Test
  void forwardNeverSkipsMissingOrBlockedEntryAndCannotGuessCorporateActions() {
    var revised = bars();
    revised.getFirst().setClose(n(101));
    assertTrue(
        ForwardWindowCalculator.calculate(forward(revised, days())).reason().contains("已修订"));
    var missing = bars();
    missing.remove(2);
    assertEquals(
        "INSUFFICIENT_DATA", ForwardWindowCalculator.calculate(forward(missing, days())).status());
    var flat = bars();
    var first = flat.get(1);
    first.setHigh(n(100));
    first.setLow(n(100));
    first.setClose(n(100));
    var result = ForwardWindowCalculator.calculate(forward(flat, days()));
    assertEquals("UNFILLED", result.status());
    assertNull(result.netReturn());
    var action = bars();
    action.get(3).setFactor(n(2));
    assertEquals(
        "INSUFFICIENT_DATA", ForwardWindowCalculator.calculate(forward(action, days())).status());
  }

  @Test
  void cashQualityUsesConsolidatedProfitAndKeepsActualWorkingCapitalSigns() {
    var f = report(LocalDate.of(2026, 9, 30), 100, "Q3_CUMULATIVE");
    f.setOperatingCashflow(n(80));
    f.setRawJson(
        "{\"income\":{\"NETPROFIT\":200,\"DEDUCT_PARENT_NETPROFIT\":60},\"cashflow\":{\"CONSTRUCT_LONG_ASSET\":30,\"OPERATE_RECE_REDUCE\":-40,\"INVENTORY_REDUCE\":-10,\"OPERATE_PAYABLE_ADD\":20},\"balance\":{\"ACCOUNTS_RECE\":120,\"INVENTORY\":90}}");
    var result =
        BusinessEvidenceCalculator.calculate(
            new BusinessEvidenceCalculator.Input(date, List.of(f), null));
    assertEquals(.4, result.cashConversion());
    assertEquals("CONSOLIDATED_PROFIT", result.operatingQuality().cashBasis());
    assertTrue(
        result.operatingQuality().metrics().stream()
            .anyMatch(v -> v.label().equals("经营性应收对现金影响") && v.value() == -40));
    assertTrue(
        result.operatingQuality().metrics().stream()
            .anyMatch(v -> v.label().equals("经营现金流扣长期资产购建") && v.value() == 50));
  }
}

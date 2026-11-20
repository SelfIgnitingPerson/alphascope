package com.alphascope.intelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.fundamental.dto.ValuationThesisInput;
import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.intelligence.calculator.BusinessEvidenceCalculator;
import com.alphascope.intelligence.calculator.PositionImpactCalculator;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import com.alphascope.portfolio.dto.PositionDO;
import com.alphascope.portfolio.entity.PortfolioAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 日期、来源与私人假设不能污染事实或推测空仓。
 *
 * @author chenxuefeng
 */
class InvestorEvidenceTest {
  private final LocalDate date = LocalDate.of(2026, 11, 18);

  private HoldingResearchView research() {
    return HoldingResearchView.builder()
        .symbol("600000.SH")
        .asOf(date)
        .quoteDate(date)
        .close(50.0)
        .priceVolume(PriceVolumeStudy.builder().stopReference(40.0).build())
        .build();
  }

  private PositionDO position() {
    var p = new PositionDO();
    p.setSymbol("600000.SH");
    p.setQuantity(new BigDecimal("1000"));
    p.setCostPrice(new BigDecimal("45"));
    p.setAsOfDate(date);
    return p;
  }

  @Test
  void sameDayHoldingsHaveImpactButMissingAndFuturePositionsCannotBecomeFlat() {
    var account = new PortfolioAccount();
    account.setAsOfDate(date);
    account.setTotalAssets(new BigDecimal("100000"));
    var p = position();
    var view =
        PositionImpactCalculator.calculate(
            new PositionImpactCalculator.Input(p, account, research()));
    assertEquals("HELD", view.state());
    assertEquals(.5, view.weight());
    assertEquals(5000, view.unrealizedPnl());
    assertEquals(10000, view.referenceLoss());
    account.setRiskBudget(new BigDecimal("1000"));
    account.setLiquidityReserve(new BigDecimal("30000"));
    account.setFundsNeededOn(date.plusMonths(1));
    view =
        PositionImpactCalculator.calculate(
            new PositionImpactCalculator.Input(p, account, research()));
    assertTrue(view.checks().stream().anyMatch(c -> c.contains("超过用户组合风险预算")));
    assertTrue(view.checks().stream().anyMatch(c -> c.contains("30000")));
    p.setAsOfDate(date.plusDays(1));
    view =
        PositionImpactCalculator.calculate(
            new PositionImpactCalculator.Input(p, account, research()));
    assertEquals("STALE", view.state());
    assertNull(view.marketValue());
    assertEquals(
        "UNKNOWN",
        PositionImpactCalculator.calculate(
                new PositionImpactCalculator.Input(null, account, research()))
            .state());
    p = position();
    p.setSymbol("600001.SH");
    assertEquals(
        "UNKNOWN",
        PositionImpactCalculator.calculate(
                new PositionImpactCalculator.Input(p, account, research()))
            .state());
    p = position();
    account.setAsOfDate(date.minusDays(1));
    view =
        PositionImpactCalculator.calculate(
            new PositionImpactCalculator.Input(p, account, research()));
    assertNull(view.weight());
    assertEquals(50000, view.marketValue());
  }

  private FinancialReport report(LocalDate period, String source, int version) {
    var f = new FinancialReport();
    f.setPeriodEnd(period);
    f.setPublishedAt(period.plusDays(20).atTime(10, 0));
    f.setSource(source);
    f.setReportType(period.getMonthValue() == 12 ? "ANNUAL" : "Q3_CUMULATIVE");
    f.setVersion(version);
    f.setNetProfit(new BigDecimal("100"));
    f.setOperatingCashflow(new BigDecimal("50"));
    f.setGrossMargin(new BigDecimal("0.25"));
    return f;
  }

  @Test
  void futureRevisionsAndOtherSourcesCannotChangeHistoricalComparison() {
    var current = report(LocalDate.of(2026, 9, 30), "EASTMONEY_FINANCIAL", 1);
    var prior = report(LocalDate.of(2025, 9, 30), "EASTMONEY_FINANCIAL", 1);
    prior.setGrossMargin(new BigDecimal(".30"));
    var future = report(prior.getPeriodEnd(), prior.getSource(), 2);
    future.setPublishedAt(date.plusDays(1).atStartOfDay());
    future.setGrossMargin(new BigDecimal(".99"));
    var other = report(prior.getPeriodEnd(), "BAOSTOCK", 5);
    other.setGrossMargin(BigDecimal.ONE);
    var result =
        BusinessEvidenceCalculator.calculate(
            new BusinessEvidenceCalculator.Input(
                date, List.of(current, future, other, prior), null));
    assertEquals(.5, result.cashConversion());
    assertEquals(-.05, result.marginChange(), 1e-10);
    assertEquals("UNVERIFIED", result.valuationStatus());
    assertTrue(result.unknowns().stream().anyMatch(s -> s.contains("应收")));
  }

  @Test
  void optimisticAssumptionIsFlaggedAndNeverPassesIndependentEvidence() {
    var annual = report(LocalDate.of(2025, 12, 31), "EASTMONEY_FINANCIAL", 1);
    annual.setRawJson("{\"income\":{\"BASIC_EPS\":1.0}}");
    var thesis =
        new ValuationThesisInput(
            "600000.SH",
            date,
            date.plusMonths(3),
            new BigDecimal("3"),
            new BigDecimal("4"),
            new BigDecimal("30"),
            new BigDecimal("50"),
            new BigDecimal(".2"),
            true,
            "用户乐观增长及可比估值假设",
            "订单下降重新复核",
            "https://example.com/announcement",
            "EASTMONEY_FINANCIAL");
    var result =
        BusinessEvidenceCalculator.calculate(
            new BusinessEvidenceCalculator.Input(date, List.of(annual), thesis));
    assertEquals(1, result.disclosedAnnualEps());
    assertEquals("UNVERIFIED", result.valuationStatus());
    assertTrue(result.findings().stream().anyMatch(s -> s.contains("1.5倍")));
    annual.setPublicationPrecision("DAY");
    annual.setPublishedAt(date.atTime(10, 0));
    result =
        BusinessEvidenceCalculator.calculate(
            new BusinessEvidenceCalculator.Input(date, List.of(annual), thesis));
    assertNull(result.disclosedAnnualEps());
    assertNull(result.cashConversion());
  }

  @Test
  void negativeProfitIsNotAValidCashConversionDenominator() {
    var f = report(LocalDate.of(2026, 9, 30), "EASTMONEY_FINANCIAL", 1);
    f.setNetProfit(new BigDecimal("-1"));
    assertNull(
        BusinessEvidenceCalculator.calculate(
                new BusinessEvidenceCalculator.Input(date, List.of(f), null))
            .cashConversion());
  }
}

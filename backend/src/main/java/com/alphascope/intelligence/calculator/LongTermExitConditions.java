package com.alphascope.intelligence.calculator;

import static com.alphascope.intelligence.calculator.HoldingResearchSupport.percent;

import com.alphascope.fundamental.dto.ValuationExitPolicy;
import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.intelligence.vo.PositionStrategy.Condition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 用当日已公开的同源累计财报核对冻结的长期失效条件。
 *
 * @author chenxuefeng
 */
final class LongTermExitConditions {
  private LongTermExitConditions() {}

  static List<Condition> evaluate(
      ValuationExitPolicy policy,
      List<FinancialReport> visible,
      FinancialReport latest,
      LocalDate asOf) {
    if (policy == null) return List.of();
    var reports = consecutiveReports(visible, latest, asOf, policy.consecutiveReports());
    var checks = new ArrayList<Condition>();
    if (policy.minimumProfitYoy() != null)
      checks.add(
          check(
              "连续归母利润同比低于下限",
              percent(policy.minimumProfitYoy()),
              reports,
              FinancialReport::getProfitYoy,
              value -> value.compareTo(policy.minimumProfitYoy()) < 0,
              policy.consecutiveReports()));
    if (policy.checkNegativeCashflow())
      checks.add(
          check(
              "连续经营现金流为负",
              "低于0元",
              reports,
              FinancialReport::getOperatingCashflow,
              value -> value.signum() < 0,
              policy.consecutiveReports()));
    return List.copyOf(checks);
  }

  private static List<FinancialReport> consecutiveReports(
      List<FinancialReport> visible, FinancialReport latest, LocalDate asOf, int count) {
    if (latest == null
        || latest.getPeriodEnd().isBefore(asOf.minusDays(200))
        || latest.getSource() == null
        || count < 1
        || count > 4) return List.of();
    var results = new ArrayList<FinancialReport>();
    var end = YearMonth.from(latest.getPeriodEnd());
    for (int index = 0; index < count; index++) {
      var date = end.minusMonths(index * 3L).atEndOfMonth();
      var report =
          visible.stream()
              .filter(
                  r -> date.equals(r.getPeriodEnd()) && latest.getSource().equals(r.getSource()))
              .filter(
                  r ->
                      switch (date.getMonthValue()) {
                        case 3 -> "Q1_CUMULATIVE".equals(r.getReportType());
                        case 6 -> "H1_CUMULATIVE".equals(r.getReportType());
                        case 9 -> "Q3_CUMULATIVE".equals(r.getReportType());
                        case 12 -> "ANNUAL".equals(r.getReportType());
                        default -> false;
                      })
              .max(Comparator.comparingInt(r -> r.getVersion() == null ? 0 : r.getVersion()))
              .orElse(null);
      if (report == null || report.getSourceUrl() == null || report.getSourceUrl().isBlank())
        return List.of();
      results.add(report);
    }
    return List.copyOf(results);
  }

  private static Condition check(
      String label,
      String threshold,
      List<FinancialReport> reports,
      Function<FinancialReport, BigDecimal> metric,
      Predicate<BigDecimal> breached,
      int count) {
    if (reports.size() != count || reports.stream().map(metric).anyMatch(java.util.Objects::isNull))
      return new Condition(label, "已披露连续同源财报或指标不足", "连续" + count + "期达到失效条件 " + threshold, null);
    String evidence =
        reports.stream()
            .map(r -> r.getPeriodEnd() + " " + metric.apply(r).toPlainString())
            .collect(java.util.stream.Collectors.joining(" · "));
    return new Condition(
        label,
        evidence,
        "连续" + count + "期达到失效条件 " + threshold,
        reports.stream().map(metric).allMatch(breached));
  }
}

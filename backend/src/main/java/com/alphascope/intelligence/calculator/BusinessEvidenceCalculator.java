package com.alphascope.intelligence.calculator;

import com.alphascope.common.util.Jsons;
import com.alphascope.fundamental.dto.ValuationThesisInput;
import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.intelligence.vo.BusinessEvidenceView;
import com.alphascope.operations.policy.SourceEvidence;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 仅用截止日前同源、同累计期间比较经营质量；财务比率不能证明业务原因。
 *
 * @author chenxuefeng
 */
public final class BusinessEvidenceCalculator {
  private BusinessEvidenceCalculator() {}

  /**
   * 生成事实、反证和仍需核验的事项。
   *
   * @param input 已按公开时点过滤的财报及私人假设
   * @return 明确缺项的证据检查
   */
  public static BusinessEvidenceView calculate(Input input) {
    var facts = new ArrayList<String>();
    var references = new ArrayList<BusinessEvidenceView.Reference>();
    var missing = new ArrayList<String>();
    var visible = input.reports().stream().filter(f -> visibleAt(f, input.date())).toList();
    var ordering =
        Comparator.comparing(FinancialReport::getPeriodEnd)
            .thenComparing(f -> f.getVersion() == null ? 0 : f.getVersion());
    var latest = visible.stream().max(ordering).orElse(null);
    var sources = new TreeSet<String>(SourceEvidence.inspect(visible).sources());
    Double conversion = null, margin = null, eps = null;
    if (latest != null) {
      references.add(reference("本期经营事实", latest));
      if (latest.getPeriodEnd().isBefore(input.date().minusDays(200)))
        missing.add("最近财报期末距研究日已超过200天，经营材料时效待复核");
      var consolidated =
          OperatingQualityCalculator.number(
              OperatingQualityCalculator.raw(latest), "income", "NETPROFIT");
      if (consolidated != null && consolidated > 0 && latest.getOperatingCashflow() != null) {
        conversion = latest.getOperatingCashflow().doubleValue() / consolidated;
        facts.add(
            conversion < 1 ? "同合并口径经营现金流低于净利润，应检查营运资金调节项与一次性变化" : "同合并口径经营现金流不低于净利润，仍需核对现金来源的持续性");
      } else if (latest.getNetProfit() != null
          && latest.getNetProfit().signum() > 0
          && latest.getOperatingCashflow() != null) {
        conversion =
            latest.getOperatingCashflow().doubleValue() / latest.getNetProfit().doubleValue();
        facts.add("归母利润与合并现金流的权属口径不同，当前比率仅作金额对照，不能单独判断现金兑现质量");
      } else missing.add("缺少正利润或经营现金流，不计算现金兑现比例，也不放大解释负利润基数");
      var prior =
          visible.stream()
              .filter(
                  f ->
                      latest.getPeriodEnd().minusYears(1).equals(f.getPeriodEnd())
                          && Objects.equals(latest.getSource(), f.getSource())
                          && Objects.equals(latest.getReportType(), f.getReportType()))
              .max(ordering)
              .orElse(null);
      if (prior != null && prior.getGrossMargin() != null && latest.getGrossMargin() != null) {
        references.add(reference("上年同累计期毛利率", prior));
        margin = latest.getGrossMargin().subtract(prior.getGrossMargin()).doubleValue();
        facts.add(margin < 0 ? "同口径毛利率较上年下降；需核查价格、产品组合及成本，当前不能确认具体原因" : "同口径毛利率较上年未下降；需核对增长可持续性");
      } else missing.add("缺少同源同累计期间毛利率，不能将一季报与年报直接环比");
      var annual =
          visible.stream()
              .filter(
                  f ->
                      "ANNUAL".equals(f.getReportType())
                          && Objects.equals(f.getSource(), latest.getSource())
                          && !f.getPeriodEnd().isAfter(input.date()))
              .max(ordering)
              .orElse(null);
      if (annual != null && annual.getRawJson() != null)
        try {
          var n = Jsons.read(annual.getRawJson(), JsonNode.class).path("income").path("BASIC_EPS");
          if (n.isNumber() && n.doubleValue() > 0) {
            eps = n.doubleValue();
            references.add(reference("最近年度基本EPS", annual));
          }
        } catch (RuntimeException invalid) {
          missing.add("年度EPS原始证据无法解析，不用净利润猜测股本");
        }
    } else missing.add("尚无截止日前可用财报，不能生成经营好坏结论");
    if (eps == null) missing.add("缺少可核对的年度基本EPS及其股本口径");
    if (input.thesis() != null
        && !input.thesis().asOf().isAfter(input.date())
        && !input.thesis().validUntil().isBefore(input.date())
        && eps != null
        && input.thesis().epsLow() != null
        && input.thesis().epsLow().doubleValue() > eps * 1.5) {
      facts.add("用户保守EPS假设已超过最近年度披露值的1.5倍，需补充增长依据；这是复核阈值，不是合理估值标准");
      sources.add(SourceEvidence.canonical(input.thesis().financialSource()));
    }
    var quality = OperatingQualityCalculator.calculate(latest);
    missing.addAll(quality.missing());
    return new BusinessEvidenceView(
        input.date(),
        latest == null ? null : latest.getPeriodEnd(),
        latest == null ? null : latest.getSource(),
        latest == null ? null : latest.getSourceUrl(),
        conversion,
        margin,
        eps,
        List.copyOf(facts),
        List.copyOf(missing),
        "UNVERIFIED",
        Set.copyOf(sources),
        List.copyOf(references),
        quality);
  }

  private static BusinessEvidenceView.Reference reference(String purpose, FinancialReport report) {
    return new BusinessEvidenceView.Reference(
        purpose,
        report.getPeriodEnd(),
        report.getVersion(),
        report.getPublishedAt(),
        report.getAvailableAt(),
        report.getSourceUrl(),
        report.getContentHash());
  }

  private static boolean visibleAt(FinancialReport report, LocalDate date) {
    if (report.getPeriodEnd() == null
        || report.getPeriodEnd().isAfter(date)
        || report.getPublishedAt() == null) return false;
    var available =
        report.getAvailableAt() != null
            ? report.getAvailableAt()
            : "DAY".equals(report.getPublicationPrecision())
                ? report.getPublishedAt().toLocalDate().plusDays(1).atStartOfDay()
                : report.getPublishedAt();
    return !report.getPublishedAt().isAfter(date.atTime(16, 10))
        && !available.isAfter(date.atTime(16, 10));
  }

  /**
   * 已按可得性过滤的研究输入。
   *
   * @author chenxuefeng
   * @param date 截止日
   * @param reports 财报
   * @param thesis 当前用户情景，可为空
   */
  public record Input(LocalDate date, List<FinancialReport> reports, ValuationThesisInput thesis) {}
}

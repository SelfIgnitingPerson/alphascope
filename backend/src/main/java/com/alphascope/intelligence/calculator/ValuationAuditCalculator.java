package com.alphascope.intelligence.calculator;

import com.alphascope.fundamental.dto.ComparablePeer;
import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.intelligence.dto.ValuationAuditInput;
import com.alphascope.intelligence.vo.ValuationAuditView;
import com.alphascope.operations.policy.SourceEvidence;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 股本算术、同口径TTM与同行价格的独立来源核对，乐观情景不能抬高实际约束上限。
 *
 * @author chenxuefeng
 */
public final class ValuationAuditCalculator {
  private ValuationAuditCalculator() {}

  public static final String VERSION = "VALUATION_EVIDENCE_V1";

  /**
   * 返回逐项核验和可复算的保守观察上限，缺资料不会固定假装通过或永久写死待核验。
   *
   * @param input 真实材料与用户明确范围
   * @return 资料核对结果
   */
  public static ValuationAuditView calculate(ValuationAuditInput input) {
    var checks = new ArrayList<ValuationAuditView.Check>();
    var peers = new ArrayList<ValuationAuditView.Peer>();
    var sources = new TreeSet<String>(SourceEvidence.inspect(input.reports()).sources());
    var thesis = input.thesis();
    Double eps = null, quartile = null, cap = null;
    var limits =
        List.of(
            "同行范围和每股面值依据由用户选择，系统核对来源数值与口径；不自动认证行业分类或预测未来盈利。",
            "当前股本以同源资产负债表实收资本÷有公开依据的面值交叉检查；加权股本以年度EPS×股数与归母利润核对，允许1%报表舍入误差。",
            "同行须同报告期、同原币、同机构报表类型，毛利率差≤15个百分点、营收同比差≤30个百分点；这些冻结阈值是方法边界，不证明经济可比性。",
            "至少3个有效同行；观察上限以用户保守EPS与实际TTM EPS较低值、用户保守PE与同行下四分位较低值计算，再扣用户安全边际，不因乐观输入升高。",
            "股本、财报或同行材料变化后重新核验，旧计划和每日判断保留旧结果；资料齐全不等于公司便宜或可以买入。");
    if (thesis == null
        || thesis.asOf().isAfter(input.date())
        || thesis.validUntil().isBefore(input.date()))
      return new ValuationAuditView(
          VERSION,
          input.date(),
          "NEEDS_INPUT",
          null,
          null,
          null,
          List.of(new ValuationAuditView.Check("有效估值情景", "缺少有效期内的本人情景", null)),
          List.of(),
          limits,
          Set.copyOf(sources));
    sources.add(thesis.financialSource());
    var visible = visible(input.reports(), input.date());
    var latest = latest(visible);
    var annual = annual(visible, latest);
    var basis = thesis.shareBasis();
    boolean stale = latest == null || latest.getPeriodEnd().isBefore(input.date().minusDays(200));
    checks.add(
        new ValuationAuditView.Check(
            "财报时效",
            stale ? "最近200天内的公开财报不足" : "财报期末" + latest.getPeriodEnd(),
            stale ? null : true));
    var profit = ttm(visible, latest);
    checks.add(
        new ValuationAuditView.Check(
            "同源滚动盈利",
            profit == null ? "缺年度、当期或上年同累计期归母利润，不能拼接来源" : "同源TTM归母利润" + profit + "元",
            profit == null ? null : profit > 0));
    boolean aligned =
        latest != null
            && Objects.equals(
                thesis.financialSource(), SourceEvidence.canonical(latest.getSource()));
    checks.add(
        new ValuationAuditView.Check(
            "假设依据来源",
            aligned ? "与实际财报来源一致" : "用户EPS依据来源与实际材料不同，需统一来源",
            latest == null ? null : aligned));
    if (basis == null)
      checks.add(new ValuationAuditView.Check("股本交叉核对", "请补当前股数、年度加权股数、面值和公开材料", null));
    else {
      var annualEps =
          OperatingQualityCalculator.number(
              OperatingQualityCalculator.raw(annual), "income", "BASIC_EPS");
      var capital =
          OperatingQualityCalculator.number(
              OperatingQualityCalculator.raw(latest), "balance", "SHARE_CAPITAL");
      boolean valid =
          positive(basis.currentShares())
              && positive(basis.annualWeightedShares())
              && positive(basis.parValue());
      if (annual == null || annualEps == null || capital == null)
        checks.add(
            new ValuationAuditView.Check(
                "股本交叉核对", "缺年度EPS、归母利润或当前资产负债表SHARE_CAPITAL；不能从净利润猜股本", null));
      else {
        boolean passed =
            valid
                && annual.getNetProfit() != null
                && close(
                    annualEps * basis.annualWeightedShares().doubleValue(),
                    annual.getNetProfit().doubleValue())
                && close(
                    basis.currentShares().doubleValue() * basis.parValue().doubleValue(), capital);
        checks.add(
            new ValuationAuditView.Check(
                "股本交叉核对",
                passed ? "年度EPS×加权股数及当前股数×面值均与真实财报相符" : "股数、面值、年度EPS或实收资本不一致，请核对是否混用万股或总股本",
                passed));
        if (passed && profit != null && profit > 0)
          eps = profit / basis.currentShares().doubleValue();
      }
    }
    var seen = new HashSet<String>();
    for (var chosen : thesis.peers() == null ? List.<ComparablePeer>of() : thesis.peers()) {
      if (chosen.symbol().equals(thesis.symbol()) || !seen.add(chosen.symbol())) {
        peers.add(
            new ValuationAuditView.Peer(
                chosen.symbol(), null, "EXCLUDED", "自身或重复标的不能作为独立同行", null, chosen.sourceUrl()));
        continue;
      }
      var actual =
          input.peers().stream()
              .filter(p -> chosen.symbol().equals(p.symbol()))
              .findFirst()
              .orElse(null);
      Double pe = null;
      String reason = "缺少已保存的同行财报或当日行情";
      String url = null;
      if (actual != null) {
        sources.addAll(SourceEvidence.inspect(actual.reports()).sources());
        if (actual.quote() != null && actual.quote().getSource() != null)
          sources.add(actual.quote().getSource());
        var reports = visible(actual.reports(), input.date());
        var peer = latest(reports);
        var yearly = annual(reports, peer);
        var ttm = ttm(reports, peer);
        var q = actual.quote();
        url = peer == null ? null : peer.getSourceUrl();
        if (latest != null
            && peer != null
            && yearly != null
            && ttm != null
            && ttm > 0
            && q != null
            && input.date().equals(q.getTradeDate())
            && !Boolean.TRUE.equals(q.getSynthetic())
            && q.getClose() != null
            && q.getClose().signum() > 0
            && chosen.parValue() != null
            && chosen.parValue().signum() > 0) {
          var raw = OperatingQualityCalculator.raw(peer);
          var annualRaw = OperatingQualityCalculator.raw(yearly);
          var capital = OperatingQualityCalculator.number(raw, "balance", "SHARE_CAPITAL");
          var yearlyCapital =
              OperatingQualityCalculator.number(annualRaw, "balance", "SHARE_CAPITAL");
          var annualEps = OperatingQualityCalculator.number(annualRaw, "income", "BASIC_EPS");
          var ownRaw = OperatingQualityCalculator.raw(latest);
          boolean accounting =
              latest.getPeriodEnd().equals(peer.getPeriodEnd())
                  && latest.getReportType().equals(peer.getReportType())
                  && Objects.equals(
                      ownRaw.path("income").path("CURRENCY").asText(null),
                      raw.path("income").path("CURRENCY").asText(null))
                  && ownRaw.path("income").hasNonNull("CURRENCY")
                  && Objects.equals(
                      ownRaw.path("income").path("ORG_TYPE").asText(null),
                      raw.path("income").path("ORG_TYPE").asText(null));
          boolean operating =
              latest.getGrossMargin() != null
                  && peer.getGrossMargin() != null
                  && Math.abs(
                          latest.getGrossMargin().doubleValue()
                              - peer.getGrossMargin().doubleValue())
                      <= .15
                  && latest.getRevenueYoy() != null
                  && peer.getRevenueYoy() != null
                  && Math.abs(
                          latest.getRevenueYoy().doubleValue() - peer.getRevenueYoy().doubleValue())
                      <= .30;
          boolean shareProof =
              capital != null
                  && capital > 0
                  && yearlyCapital != null
                  && yearlyCapital > 0
                  && annualEps != null
                  && annualEps > 0
                  && yearly.getNetProfit() != null
                  && close(
                      annualEps * yearlyCapital / chosen.parValue().doubleValue(),
                      yearly.getNetProfit().doubleValue());
          if (!accounting) reason = "报告期间、币种或机构报表类型不可比";
          else if (!operating) reason = "毛利率或营收增速超过冻结可比性范围，不能强行并入高PE样本";
          else if (!shareProof) reason = "面值、年度EPS与实收资本不能交叉核验，或年内股本变化需另行核对";
          else {
            double shares = capital / chosen.parValue().doubleValue();
            pe = q.getClose().doubleValue() / (ttm / shares);
            reason = "同期间、币种、机构类型、盈利与股本算术检查通过；同行业务依据仍由用户负责核对";
          }
        }
      }
      peers.add(
          new ValuationAuditView.Peer(
              chosen.symbol(),
              pe,
              pe == null ? "EXCLUDED" : "CHECKED",
              reason,
              url,
              chosen.sourceUrl()));
    }
    var validPeers =
        peers.stream()
            .filter(p -> p.pe() != null && Double.isFinite(p.pe()) && p.pe() > 0)
            .mapToDouble(ValuationAuditView.Peer::pe)
            .sorted()
            .toArray();
    checks.add(
        new ValuationAuditView.Check(
            "有效同行范围",
            "有效" + validPeers.length + "家，需至少3家真实同口径同行",
            validPeers.length >= 3 ? true : null));
    if (validPeers.length >= 3)
      quartile = validPeers[(int) Math.floor((validPeers.length - 1) * .25)];
    if (eps != null
        && quartile != null
        && thesis.epsLow() != null
        && thesis.peLow() != null
        && thesis.safetyMargin() != null)
      cap =
          Math.min(eps, thesis.epsLow().doubleValue())
              * Math.min(quartile, thesis.peLow().doubleValue())
              * (1 - thesis.safetyMargin().doubleValue());
    boolean failed = checks.stream().anyMatch(c -> Boolean.FALSE.equals(c.passed()));
    boolean complete = checks.stream().allMatch(c -> Boolean.TRUE.equals(c.passed()));
    return new ValuationAuditView(
        VERSION,
        input.date(),
        failed ? "INCONSISTENT" : complete && cap != null ? "CHECKED_WITH_LIMITS" : "INCOMPLETE",
        eps,
        quartile,
        complete ? cap : null,
        List.copyOf(checks),
        List.copyOf(peers),
        limits,
        Set.copyOf(sources));
  }

  private static boolean positive(java.math.BigDecimal n) {
    return n != null && n.signum() > 0;
  }

  private static boolean close(double a, double b) {
    return Double.isFinite(a)
        && Double.isFinite(b)
        && Math.abs(a - b) <= Math.max(1, Math.abs(b) * .01);
  }

  static List<FinancialReport> visible(List<FinancialReport> reports, LocalDate date) {
    return reports.stream()
        .filter(
            f ->
                f.getPeriodEnd() != null
                    && !f.getPeriodEnd().isAfter(date)
                    && f.getPublishedAt() != null
                    && !f.getPublishedAt().isAfter(date.atTime(16, 10))
                    && (f.getAvailableAt() == null
                            ? "DAY".equals(f.getPublicationPrecision())
                                ? f.getPublishedAt().toLocalDate().plusDays(1).atStartOfDay()
                                : f.getPublishedAt()
                            : f.getAvailableAt())
                        .isBefore(date.atTime(16, 10).plusNanos(1)))
        .toList();
  }

  static FinancialReport latest(List<FinancialReport> reports) {
    return reports.stream()
        .max(
            Comparator.comparing(FinancialReport::getPeriodEnd)
                .thenComparing(f -> f.getVersion() == null ? 0 : f.getVersion()))
        .orElse(null);
  }

  private static FinancialReport annual(List<FinancialReport> reports, FinancialReport latest) {
    if (latest == null) return null;
    return reports.stream()
        .filter(
            f ->
                "ANNUAL".equals(f.getReportType())
                    && Objects.equals(f.getSource(), latest.getSource()))
        .max(
            Comparator.comparing(FinancialReport::getPeriodEnd)
                .thenComparing(f -> f.getVersion() == null ? 0 : f.getVersion()))
        .orElse(null);
  }

  private static Double ttm(List<FinancialReport> reports, FinancialReport latest) {
    if (latest == null || latest.getNetProfit() == null) return null;
    if ("ANNUAL".equals(latest.getReportType())) return latest.getNetProfit().doubleValue();
    var annual = annual(reports, latest);
    var prior =
        reports.stream()
            .filter(
                f ->
                    latest.getPeriodEnd().minusYears(1).equals(f.getPeriodEnd())
                        && Objects.equals(f.getSource(), latest.getSource())
                        && Objects.equals(f.getReportType(), latest.getReportType()))
            .max(Comparator.comparing(f -> f.getVersion() == null ? 0 : f.getVersion()))
            .orElse(null);
    if (annual == null
        || annual.getNetProfit() == null
        || prior == null
        || prior.getNetProfit() == null
        || annual.getPeriodEnd().getYear() != latest.getPeriodEnd().getYear() - 1) return null;
    return annual.getNetProfit().doubleValue()
        + latest.getNetProfit().doubleValue()
        - prior.getNetProfit().doubleValue();
  }
}

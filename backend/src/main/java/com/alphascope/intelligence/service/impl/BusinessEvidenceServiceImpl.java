package com.alphascope.intelligence.service.impl;

import com.alphascope.fundamental.dto.EvidenceQuery;
import com.alphascope.fundamental.service.FundamentalService;
import com.alphascope.fundamental.service.InvestmentEvidenceService;
import com.alphascope.intelligence.calculator.BusinessEvidenceCalculator;
import com.alphascope.intelligence.service.BusinessEvidenceService;
import com.alphascope.intelligence.vo.BusinessEvidenceView;
import org.springframework.stereotype.Service;

/**
 * 复用公开时点过滤和租户隔离的个人估值读取，不把个人假设写成事实。
 *
 * @author chenxuefeng
 */
@Service
public class BusinessEvidenceServiceImpl implements BusinessEvidenceService {
  @org.springframework.beans.factory.annotation.Autowired
  private com.alphascope.intelligence.service.ValuationAuditService valuation;

  private final FundamentalService financials;
  private final InvestmentEvidenceService evidence;

  public BusinessEvidenceServiceImpl(
      FundamentalService financials, InvestmentEvidenceService evidence) {
    this.financials = financials;
    this.evidence = evidence;
  }

  /** {@inheritDoc} */
  @Override
  public BusinessEvidenceView read(EvidenceQuery query) {
    if (query.asOf() == null
        || query.asOf().isAfter(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))))
      throw new IllegalArgumentException("研究日期不得晚于上海当前日期");
    var value =
        BusinessEvidenceCalculator.calculate(
            new BusinessEvidenceCalculator.Input(
                query.asOf(),
                financials.reports(query.symbol(), query.asOf()),
                evidence.thesis(query)));
    if (valuation == null) return value;
    var audit = valuation.read(query);
    var sources = new java.util.TreeSet<String>(value.inputSources());
    sources.addAll(audit.inputSources());
    var unknowns = new java.util.ArrayList<>(value.unknowns());
    audit.checks().stream()
        .filter(c -> !Boolean.TRUE.equals(c.passed()))
        .forEach(c -> unknowns.add(c.label() + "：" + c.observed()));
    return new BusinessEvidenceView(
        value.date(),
        value.period(),
        value.source(),
        value.sourceUrl(),
        value.cashConversion(),
        value.marginChange(),
        value.disclosedAnnualEps(),
        value.findings(),
        java.util.List.copyOf(unknowns),
        audit.status(),
        java.util.Set.copyOf(sources),
        value.references(),
        value.operatingQuality());
  }
}

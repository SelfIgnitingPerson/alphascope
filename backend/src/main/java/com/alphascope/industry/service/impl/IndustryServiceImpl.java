package com.alphascope.industry.service.impl;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.dto.OptionalDateRangeRequest;
import com.alphascope.common.util.Jsons;
import com.alphascope.common.validation.DateRangeValidator;
import com.alphascope.indicator.service.IndicatorService;
import com.alphascope.industry.dto.CohortDetailDO;
import com.alphascope.industry.dto.DivergenceEvidence;
import com.alphascope.industry.dto.ThemeContext;
import com.alphascope.industry.dto.ThemeDiagnostic;
import com.alphascope.industry.dto.ThemeMemberDO;
import com.alphascope.industry.dto.ThemeScoreQuery;
import com.alphascope.industry.entity.Theme;
import com.alphascope.industry.entity.ThemeScore;
import com.alphascope.industry.mapper.IndustryMapper;
import com.alphascope.industry.service.IndustryService;
import com.alphascope.industry.vo.CohortView;
import com.alphascope.industry.vo.ThemeMemberView;
import com.alphascope.industry.vo.ThemeScoreView;
import com.alphascope.industry.vo.ThemeStrengthView;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.service.MarketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * IndustryService 的业务实现。
 *
 * <p>按有效成分计算等权主题收益、均线广度及多成员连续三日证据。
 *
 * @author chenxuefeng
 */
@Service
public class IndustryServiceImpl implements IndustryService {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final IndustryMapper mapper;
  private final MarketService market;
  private final IndicatorService indicators;
  private final double minimumCoverage;

  public IndustryServiceImpl(
      IndustryMapper mapper,
      MarketService market,
      IndicatorService indicators,
      @Value("${alphascope.scoring.minimum-theme-coverage:0.8}") double minimumCoverage) {
    this.mapper = mapper;
    this.market = market;
    this.indicators = indicators;
    this.minimumCoverage = minimumCoverage;
  }

  /** {@inheritDoc} */
  @Override
  public List<Theme> themes() {
    return mapper.themes();
  }

  /** {@inheritDoc} */
  @Override
  public ThemeStrengthView strength(String code, OptionalDateRangeRequest request) {
    LocalDate end =
        request.getEnd() == null ? LocalDate.now(ZoneId.of("Asia/Shanghai")) : request.getEnd();
    LocalDate start = request.getStart() == null ? end.minusMonths(3) : request.getStart();

    DateRangeValidator.requireValid(start, end);
    var cohort = selectedCohort(code, end);
    return new ThemeStrengthView(
        mapper
            .scores(
                ThemeScoreQuery.builder()
                    .themeCode(code)
                    .startDate(start)
                    .endDate(end)
                    .synthetic(market.synthetic())
                    .build())
            .stream()
            .map(ThemeScoreView::from)
            .toList(),
        selectedMembers(code, end).stream().map(ThemeMemberView::from).toList(),
        "EQUAL_RETURN_V1",
        cohort == null ? "LEGACY_REGISTERED_SAMPLE" : cohort.getScope(),
        cohort == null ? null : CohortView.from(cohort),
        "UNKNOWN");
  }

  /** {@inheritDoc} */
  @Override
  public void calculateRange(LocalDate start, LocalDate end) {
    for (var date :
        new TreeSet<>(
            market.expectedDates(
                TradingDatesQuery.builder()
                    .symbol("000300.SH")
                    .start(start)
                    .end(end)
                    .source(null)
                    .build()))) {
      calculate(date);
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<ThemeContext> snapshot(LocalDate date) {
    if (market.synthetic()) {
      return List.of();
    }
    List<ThemeContext> contexts = new ArrayList<>();
    for (Theme theme : mapper.themes()) {
      String code = theme.getCode();
      var cohort = mapper.cohort(code, date);
      if (cohort == null) {
        continue;
      }
      var members =
          mapper.members(code, date).stream()
              .map(
                  member ->
                      new ThemeContext.Member(
                          member.getSymbol(),
                          member.getName(),
                          member.getCohortId(),
                          member.getScope(),
                          member.getSourceDocumentId(),
                          member.getSourceUrl(),
                          market.bars(
                              member.getSymbol(),
                              DateRangeRequest.builder()
                                  .start(date.minusDays(115))
                                  .end(date)
                                  .build())))
              .toList();
      contexts.add(
          new ThemeContext(
              code,
              theme.getName(),
              cohort,
              members,
              mapper.scores(
                  ThemeScoreQuery.builder()
                      .themeCode(code)
                      .startDate(date.minusDays(10))
                      .endDate(date)
                      .synthetic(market.synthetic())
                      .build()),
              new TreeSet<>(
                  market.expectedDates(
                      TradingDatesQuery.builder()
                          .symbol("000300.SH")
                          .start(date.minusDays(115))
                          .end(date)
                          .source(null)
                          .build()))));
    }
    return contexts;
  }

  /** {@inheritDoc} */
  @Override
  public void calculate(LocalDate date) {
    for (var theme : mapper.themes()) {
      String code = theme.getCode();
      var members = selectedMembers(code, date);
      var cohort = selectedCohort(code, date);
      var inputSources = new java.util.TreeSet<String>();
      int valid = 0, above = 0;
      double sumRs = 0, sumDayReturn = 0;
      for (var member : members) {
        var rows =
            indicators.list(
                member.getSymbol(), DateRangeRequest.builder().start(date).end(date).build());
        if (rows.isEmpty()) {
          continue;
        }
        var snapshot = rows.get(0);
        if (snapshot.getRs20() == null || snapshot.getMa20() == null) {
          continue;
        }
        var bars =
            market.bars(
                member.getSymbol(), DateRangeRequest.builder().start(date).end(date).build());
        if (bars.isEmpty()) {
          continue;
        }
        var recent =
            market.bars(
                member.getSymbol(),
                DateRangeRequest.builder().start(date.minusDays(10)).end(date).build());
        if (recent.size() > 1 && date.equals(recent.getLast().getTradeDate())) {
          var previous = recent.get(recent.size() - 2);
          var current = recent.get(recent.size() - 1);
          sumDayReturn +=
              current.getClose().doubleValue()
                      * current.getFactor().doubleValue()
                      / (previous.getClose().doubleValue() * previous.getFactor().doubleValue())
                  - 1;
        } else {
          continue;
        } // 无前一有效收盘，不能把未观察到的收益当作0计入均值。
        recent.stream()
            .map(MarketBarDO::getSource)
            .filter(java.util.Objects::nonNull)
            .forEach(inputSources::add);
        valid++;
        sumRs += snapshot.getRs20().doubleValue();
        if (bars.get(0).getClose().doubleValue() > snapshot.getMa20().doubleValue()) {
          above++;
        }
      }
      double coverage = members.isEmpty() ? 0 : (double) valid / members.size();
      boolean complete = coverage >= minimumCoverage && valid > 0 && !market.synthetic();
      ThemeScore row = new ThemeScore();
      row.setThemeId(theme.getId());
      row.setSynthetic(market.synthetic());
      row.setTradeDate(date);
      row.setCoverage(BigDecimal.valueOf(coverage));
      row.setDayReturn(valid > 0 ? BigDecimal.valueOf(sumDayReturn / valid) : null);
      row.setRs20(valid > 0 ? BigDecimal.valueOf(sumRs / valid) : null);
      row.setBreadth(valid > 0 ? BigDecimal.valueOf((double) above / valid) : null);
      row.setScore(
          complete
              ? BigDecimal.valueOf(Math.max(0, Math.min(100, 50 + sumRs / valid * 200)))
              : null);
      var themeEvidence =
          new java.util.LinkedHashMap<String, Object>(
              Map.of(
                  "dataStatus",
                  complete ? "COMPLETE" : "INSUFFICIENT_DATA",
                  "validMembers",
                  valid,
                  "totalMembers",
                  members.size(),
                  "isSynthetic",
                  market.synthetic(),
                  "weighting",
                  "equal period returns",
                  "scope",
                  cohort == null ? "LEGACY_REGISTERED_SAMPLE" : cohort.getScope(),
                  "cohortId",
                  cohort == null ? 0 : cohort.getId(),
                  "sourceDocumentId",
                  cohort == null ? 0 : cohort.getSourceDocumentId(),
                  "industryCoverage",
                  "UNKNOWN"));
      themeEvidence.put("inputSources", inputSources);
      row.setEvidenceJson(Jsons.write(themeEvidence));
      mapper.save(row);
    }
  }

  /** {@inheritDoc} */
  @Override
  public DivergenceEvidence divergenceEvidence(String symbol, LocalDate date) {
    List<LocalDate> dates;
    try {
      var open =
          new TreeSet<>(
              market.expectedDates(
                  TradingDatesQuery.builder()
                      .symbol("000300.SH")
                      .start(date.minusDays(20))
                      .end(date)
                      .source(null)
                      .build()));
      dates = open.stream().skip(Math.max(0, open.size() - 3)).toList();
    } catch (IllegalStateException e) {
      return new DivergenceEvidence(Map.of(), List.of(), "交易日历不足，无法核对连续三个交易日；先补齐日历。");
    }
    if (dates.size() != 3 || !dates.getLast().equals(date)) {
      return new DivergenceEvidence(Map.of(), List.of(), "业务日缺少连续三个交易日的日历证据。");
    }
    List<ThemeDiagnostic> diagnostics = new ArrayList<>();
    List<String> reasons = new ArrayList<>();
    for (var theme : mapper.themes()) {
      String code = theme.getCode();
      var members = selectedMembers(code, date);
      var cohort = selectedCohort(code, date);
      if (members.stream().noneMatch(m -> symbol.equals(m.getSymbol()))) {
        continue;
      }
      Map<LocalDate, ThemeScore> byDate = new HashMap<>();
      for (var row :
          mapper.scores(
              ThemeScoreQuery.builder()
                  .themeCode(code)
                  .startDate(dates.getFirst())
                  .endDate(date)
                  .synthetic(market.synthetic())
                  .build())) {
        if (!"EQUAL_RETURN_V1".equals(row.getModelVersion())) {
          continue;
        }
        byDate.put(row.getTradeDate(), row);
      }
      int recorded = 0, eligible = 0;
      List<String> missingDates = new ArrayList<>();
      for (LocalDate d : dates) {
        var row = byDate.get(d);
        if (!hasObservation(row)) {
          missingDates.add(d.toString());
          continue;
        }
        recorded++;
        if (usableNumbers(row) && multiMemberEvidence(row, cohort)) {
          eligible++;
        }
      }
      var since = mapper.membershipSince(code, symbol, date);
      var diagnostic = new ThemeDiagnostic();
      diagnostic.setCode(code);
      diagnostic.setName(theme.getName());
      diagnostic.setRegisteredMembers(members.size());
      diagnostic.setRequiredMembers(2);
      diagnostic.setRecordedDays(recorded);
      diagnostic.setEligibleDays(eligible);
      diagnostic.setRequiredDays(3);
      diagnostic.setRequiredDates(dates.stream().map(LocalDate::toString).toList());
      diagnostic.setMissingDates(missingDates);
      diagnostic.setMembershipSince(since == null ? "UNKNOWN" : since.toString());
      diagnostic.setScope(cohort == null ? "LEGACY_REGISTERED_SAMPLE" : cohort.getScope());
      diagnostic.setCohortId(cohort == null ? 0 : cohort.getId());
      diagnostic.setSourceUrl(cohort == null ? "" : cohort.getSourceUrl());
      diagnostic.setIndustryCoverage("UNKNOWN");
      diagnostics.add(diagnostic);
      if (members.size() < 2 || eligible < 3 || market.synthetic()) {
        String reason =
            theme.getName()
                + "：当前登记"
                + members.size()
                + "只成员（至少2只）；最近3个交易日已保存"
                + recorded
                + "/3天收益与广度记录，满足多成员和覆盖条件的为"
                + eligible
                + "/3天。";
        if (since != null) {
          reason += "该股主题归属自" + since + "起有效。";
        }
        if (!missingDates.isEmpty()) {
          reason += "缺少日期：" + String.join("、", missingDates) + "。";
        }
        reasons.add(reason);
        continue;
      }
      var a = byDate.get(dates.get(2));
      var b = byDate.get(dates.get(1));
      var c = byDate.get(dates.get(0));
      return new DivergenceEvidence(
          Map.of(
              "themeReturn",
              a.getDayReturn().doubleValue(),
              "themeBreadth",
              a.getBreadth().doubleValue(),
              "previousThemeBreadth",
              b.getBreadth().doubleValue(),
              "twoDaysAgoThemeBreadth",
              c.getBreadth().doubleValue()),
          diagnostics,
          "同一主题研究样本的多成员证据覆盖最近三个交易日；样本覆盖不等于全行业覆盖。");
    }
    String reason =
        reasons.isEmpty()
            ? "未登记该股当前有效的主题归属，无法评估主题分化。"
            : String.join(" ", reasons) + "请补齐同主题真实成员与历史记录；不能倒填成分生效日期。";
    return new DivergenceEvidence(Map.of(), diagnostics, reason);
  }

  private boolean finite(Number value) {
    return value != null && Double.isFinite(value.doubleValue());
  }

  private boolean usableNumbers(ThemeScore row) {
    if (row == null) {
      return false;
    }
    if (!finite(row.getScore())
        || !finite(row.getBreadth())
        || !finite(row.getDayReturn())
        || !finite(row.getCoverage())) {
      return false;
    }
    double breadth = row.getBreadth().doubleValue();
    return breadth >= 0 && breadth <= 1 && row.getCoverage().doubleValue() >= minimumCoverage;
  }

  private boolean hasObservation(ThemeScore row) {
    if (row == null) {
      return false;
    }
    if (!finite(row.getBreadth()) || !finite(row.getDayReturn())) {
      return false;
    }
    double breadth = row.getBreadth().doubleValue();
    return breadth >= 0 && breadth <= 1;
  }

  private List<ThemeMemberDO> selectedMembers(String code, LocalDate date) {
    return market.synthetic() ? mapper.legacyMembers(code, date) : mapper.members(code, date);
  }

  private CohortDetailDO selectedCohort(String code, LocalDate date) {
    return market.synthetic() ? null : mapper.cohort(code, date);
  }

  private boolean multiMemberEvidence(ThemeScore row, CohortDetailDO cohort) {
    try {
      Object raw = row.getEvidenceJson();
      var evidence = raw instanceof String s ? JSON.readTree(s) : JSON.valueToTree(raw);
      return (cohort == null || evidence.path("cohortId").asLong() == cohort.getId())
          && evidence.path("validMembers").asInt() >= 2
          && evidence.path("totalMembers").asInt() >= 2
          && !evidence.path("isSynthetic").asBoolean()
          && evidence.path("dataStatus").asText().equals("COMPLETE");
    } catch (IOException | IllegalArgumentException e) {
      return false;
    }
  }
}

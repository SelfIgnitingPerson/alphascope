package com.alphascope.industry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.indicator.service.IndicatorService;
import com.alphascope.industry.dto.CohortDetailDO;
import com.alphascope.industry.dto.ThemeMemberDO;
import com.alphascope.industry.dto.ThemeScoreQuery;
import com.alphascope.industry.entity.Theme;
import com.alphascope.industry.entity.ThemeScore;
import com.alphascope.industry.mapper.IndustryMapper;
import com.alphascope.industry.service.IndustryService;
import com.alphascope.industry.service.impl.IndustryServiceImpl;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.service.MarketService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * 同一主题多成员、连续交易日及覆盖率规则的验证。
 *
 * @author chenxuefeng
 */
class ThemeDivergenceTest {
  final IndustryMapper mapper = mock(IndustryMapper.class);
  final MarketService market = mock(MarketService.class);
  final IndustryService service =
      new IndustryServiceImpl(mapper, market, mock(IndicatorService.class), .8);
  final LocalDate date = LocalDate.of(2026, 10, 30);

  Theme theme(String code, String name) {
    var theme = new Theme();
    theme.setCode(code);
    theme.setName(name);
    return theme;
  }

  ThemeMemberDO member(String symbol) {
    var member = new ThemeMemberDO();
    member.setSymbol(symbol);
    return member;
  }

  void fixture(LocalDate end, List<LocalDate> dates, int members) {
    when(market.expectedDates(
            TradingDatesQuery.builder()
                .symbol("000300.SH")
                .start(end.minusDays(20))
                .end(end)
                .source(null)
                .build()))
        .thenReturn(new TreeSet<>(dates));
    when(mapper.themes()).thenReturn(List.of(theme("PCB", "PCB/CCL")));
    var list = new ArrayList<ThemeMemberDO>();
    list.add(member("300476.SZ"));
    if (members > 1) list.add(member("OTHER"));
    when(mapper.members("PCB", end)).thenReturn(list);
    when(mapper.membershipSince("PCB", "300476.SZ", end)).thenReturn(LocalDate.of(2026, 10, 30));
  }

  ThemeScore row(LocalDate date, int members) {
    var r = new ThemeScore();
    r.setModelVersion("EQUAL_RETURN_V1");
    r.setTradeDate(date);
    r.setScore(BigDecimal.valueOf(50));
    r.setBreadth(BigDecimal.valueOf(.5));
    r.setDayReturn(BigDecimal.valueOf(.01));
    r.setCoverage(BigDecimal.valueOf(1.0));
    r.setEvidenceJson(
        "{\"dataStatus\":\"COMPLETE\",\"isSynthetic\":false,\"totalMembers\":"
            + members
            + ",\"validMembers\":"
            + members
            + "}");
    return r;
  }

  List<LocalDate> weekdays() {
    return List.of(date.minusDays(2), date.minusDays(1), date);
  }

  @Test
  void singleMemberIsIneligibleEvenWhenThreeRecordsExist() {
    fixture(date, weekdays(), 1);
    when(mapper.scores(
            ThemeScoreQuery.builder()
                .themeCode("PCB")
                .startDate(date.minusDays(2))
                .endDate(date)
                .synthetic(false)
                .build()))
        .thenReturn(weekdays().stream().map(d -> row(d, 1)).toList());
    var evidence = service.divergenceEvidence("300476.SZ", date);
    assertTrue(evidence.values().isEmpty());
    assertEquals(3, evidence.themes().getFirst().getRecordedDays());
    assertEquals(0, evidence.themes().getFirst().getEligibleDays());
    assertTrue(evidence.reason().contains("当前登记1只成员（至少2只）"));
  }

  @Test
  void nonConsecutiveRowsCannotFillMissingTradingDate() {
    fixture(date, weekdays(), 2);
    when(mapper.scores(
            ThemeScoreQuery.builder()
                .themeCode("PCB")
                .startDate(date.minusDays(2))
                .endDate(date)
                .synthetic(false)
                .build()))
        .thenReturn(List.of(row(date.minusDays(4), 2), row(date.minusDays(1), 2), row(date, 2)));
    var e = service.divergenceEvidence("300476.SZ", date);
    assertTrue(e.values().isEmpty());
    assertEquals(List.of("2026-10-28"), e.themes().getFirst().getMissingDates());
  }

  @Test
  void exchangeCalendarAllowsWeekendGapAndPreservesSameThemeValues() {
    var end = LocalDate.of(2026, 11, 3);
    var dates = List.of(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2), end);
    fixture(end, dates, 2);
    var rows = dates.stream().map(d -> row(d, 2)).toList();
    rows.get(0).setBreadth(BigDecimal.valueOf(.9));
    rows.get(1).setBreadth(BigDecimal.valueOf(.7));
    rows.get(2).setBreadth(BigDecimal.valueOf(.5));
    when(mapper.scores(
            ThemeScoreQuery.builder()
                .themeCode("PCB")
                .startDate(dates.getFirst())
                .endDate(end)
                .synthetic(false)
                .build()))
        .thenReturn(rows);
    var e = service.divergenceEvidence("300476.SZ", end);
    assertEquals(.01, e.values().get("themeReturn"));
    assertEquals(.5, e.values().get("themeBreadth"));
    assertEquals(.7, e.values().get("previousThemeBreadth"));
    assertEquals(.9, e.values().get("twoDaysAgoThemeBreadth"));
    assertEquals("2026-10-30", e.themes().getFirst().getMembershipSince());
  }

  @Test
  void coverageAndUnverifiableMemberCountsAreRejected() {
    fixture(date, weekdays(), 2);
    var rows = weekdays().stream().map(d -> row(d, 2)).toList();
    rows.get(0).setCoverage(BigDecimal.valueOf(.79));
    rows.get(1).setEvidenceJson("{}");
    when(mapper.scores(
            ThemeScoreQuery.builder()
                .themeCode("PCB")
                .startDate(date.minusDays(2))
                .endDate(date)
                .synthetic(false)
                .build()))
        .thenReturn(rows);
    var e = service.divergenceEvidence("300476.SZ", date);
    assertTrue(e.values().isEmpty());
    assertEquals(1, e.themes().getFirst().getEligibleDays());
  }

  @Test
  void differentThemesCannotBeCombinedIntoOneCompleteWindow() {
    fixture(date, weekdays(), 2);
    when(mapper.themes()).thenReturn(List.of(theme("PCB", "PCB"), theme("COOLING", "液冷")));
    when(mapper.members("COOLING", date))
        .thenReturn(List.of(member("300476.SZ"), member("SECOND")));
    when(mapper.scores(
            ThemeScoreQuery.builder()
                .themeCode("PCB")
                .startDate(date.minusDays(2))
                .endDate(date)
                .synthetic(false)
                .build()))
        .thenReturn(List.of(row(date.minusDays(2), 2), row(date, 2)));
    when(mapper.scores(
            ThemeScoreQuery.builder()
                .themeCode("COOLING")
                .startDate(date.minusDays(2))
                .endDate(date)
                .synthetic(false)
                .build()))
        .thenReturn(List.of(row(date.minusDays(1), 2), row(date, 2)));
    var e = service.divergenceEvidence("300476.SZ", date);
    assertTrue(e.values().isEmpty());
    assertEquals(2, e.themes().size());
  }

  @Test
  void scoresFromAnOlderCohortCannotQualifyTheCurrentCohort() {
    fixture(date, weekdays(), 2);
    when(mapper.cohort("PCB", date)).thenReturn(fixtureCohortDetailDO1());
    var rows = weekdays().stream().map(d -> row(d, 2)).toList();
    when(mapper.scores(
            ThemeScoreQuery.builder()
                .themeCode("PCB")
                .startDate(date.minusDays(2))
                .endDate(date)
                .synthetic(false)
                .build()))
        .thenReturn(rows);
    var result = service.divergenceEvidence("300476.SZ", date);
    assertTrue(result.values().isEmpty());
    assertEquals(0, result.themes().getFirst().getEligibleDays());
  }

  @Test
  void missingCalendarHasExplicitReason() {
    when(market.expectedDates(
            TradingDatesQuery.builder()
                .symbol("000300.SH")
                .start(date.minusDays(20))
                .end(date)
                .source(null)
                .build()))
        .thenThrow(new IllegalStateException("缺日历"));
    var e = service.divergenceEvidence("300476.SZ", date);
    assertTrue(e.values().isEmpty());
    assertTrue(e.reason().contains("日历不足"));
  }

  @Test
  void noCurrentMembershipHasExplicitReason() {
    fixture(date, weekdays(), 2);
    when(mapper.members("PCB", date)).thenReturn(List.of());
    assertTrue(service.divergenceEvidence("300476.SZ", date).reason().contains("未登记"));
  }

  /** 明确设置该用例需要的字段，避免JSON转换掩盖测试数据类型。 */
  private CohortDetailDO fixtureCohortDetailDO1() {
    var row = new CohortDetailDO();
    row.setId(99L);
    row.setScope("DISCLOSED_RESEARCH_SAMPLE");
    row.setSourceUrl("https://example.test");
    return row;
  }
}

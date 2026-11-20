package com.alphascope.intelligence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.intelligence.calculator.ResearchFocusCalculator;
import com.alphascope.intelligence.dto.ResearchFocusInput;
import com.alphascope.intelligence.dto.ResearchFocusQuery;
import com.alphascope.intelligence.service.HoldingResearchService;
import com.alphascope.intelligence.service.impl.ResearchFocusServiceImpl;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.market.service.MarketService;
import com.alphascope.planning.service.PlanFollowupService;
import com.alphascope.planning.vo.PlanSummary;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.portfolio.vo.WatchlistView;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 两期变化必须有完整同源对照；当前身份自选与单标的失败保持隔离。
 *
 * @author chenxuefeng
 */
class ResearchFocusTest {
  private final LocalDate date = LocalDate.of(2026, 11, 17), previous = date.minusDays(1);

  private HoldingResearchView view(LocalDate at, String state, boolean risk) {
    var open =
        HoldingResearchView.Action.builder()
            .code("OPEN_PULLBACK")
            .title("回踩建仓")
            .conditionLogic("ALL")
            .status(state)
            .build();
    var reduce =
        HoldingResearchView.Action.builder()
            .code("REDUCE")
            .title("风险退出")
            .conditionLogic("ANY")
            .status(risk ? "CONDITIONAL_MATCH" : "WAIT")
            .conclusion("已触发放量走弱")
            .build();
    return HoldingResearchView.builder()
        .version("TEST_V1")
        .symbol("600000.SH")
        .name("测试")
        .asOf(at)
        .quoteDate(at)
        .source("TEST")
        .close(100.0)
        .horizons(
            List.of(
                HoldingResearchView.Horizon.builder()
                    .code("SHORT")
                    .title("短期")
                    .status(state)
                    .actions(List.of(open, reduce))
                    .missing(List.of())
                    .build()))
        .build();
  }

  @Test
  void freshConditionsRequireSameMethodSourceAndCompletePreviousQuote() {
    var now = view(date, "CONDITIONAL_MATCH", false);
    var old = view(previous, "WAIT", false);
    assertTrue(
        ResearchFocusCalculator.compare(
                ResearchFocusInput.builder()
                    .current(now)
                    .previous(old)
                    .previousDate(previous)
                    .build())
            .newlyMatched());
    assertFalse(
        ResearchFocusCalculator.compare(
                ResearchFocusInput.builder()
                    .current(now)
                    .previous(null)
                    .previousDate(previous)
                    .build())
            .newlyMatched());
    assertFalse(
        ResearchFocusCalculator.compare(
                ResearchFocusInput.builder()
                    .current(now)
                    .previous(old.toBuilder().source("OTHER").build())
                    .previousDate(previous)
                    .build())
            .newlyMatched());
    assertFalse(
        ResearchFocusCalculator.compare(
                ResearchFocusInput.builder()
                    .current(now)
                    .previous(old.toBuilder().version("OLD").build())
                    .previousDate(previous)
                    .build())
            .newlyMatched());
    assertFalse(
        ResearchFocusCalculator.compare(
                ResearchFocusInput.builder()
                    .current(now)
                    .previous(old.toBuilder().quoteDate(previous.minusDays(1)).build())
                    .previousDate(previous)
                    .build())
            .newlyMatched());
    assertFalse(
        ResearchFocusCalculator.compare(
                ResearchFocusInput.builder()
                    .current(now)
                    .previous(view(previous, "CONDITIONAL_MATCH", false))
                    .previousDate(previous)
                    .build())
            .newlyMatched());
  }

  @Test
  void exitHasPriorityOverEntryAndStaleQuoteNeverConfirmsToday() {
    var now = view(date, "CONDITIONAL_MATCH", true);
    assertEquals(
        "RISK",
        ResearchFocusCalculator.compare(
                ResearchFocusInput.builder()
                    .current(now)
                    .previous(null)
                    .previousDate(previous)
                    .build())
            .category());
    var stale = now.toBuilder().quoteDate(previous).build();
    var result =
        ResearchFocusCalculator.compare(
            ResearchFocusInput.builder()
                .current(stale)
                .previous(null)
                .previousDate(previous)
                .build());
    assertEquals("DATA_GAP", result.category());
    assertFalse(result.newlyMatched());
    assertTrue(result.explanation().contains(previous.toString()));
  }

  @Test
  void oneFailureCannotHideOtherStocksAndDisabledWatchesAreNeverRead() {
    var portfolio = mock(PortfolioService.class);
    var research = mock(HoldingResearchService.class);
    var market = mock(MarketService.class);
    var service = new ResearchFocusServiceImpl(portfolio, research, market);
    when(market.isOpen(date)).thenReturn(true);
    when(portfolio.watchlistView())
        .thenReturn(
            List.of(
                new WatchlistView("600001.SH", "失败", 2, true),
                new WatchlistView("600000.SH", "正常", 1, true),
                new WatchlistView("600002.SH", "停用", 0, false)));
    when(research.read(eq("600001.SH"), any())).thenThrow(new IllegalStateException("内部细节不应公开"));
    when(research.read(eq("600000.SH"), any())).thenReturn(view(date, "WAIT", false));
    var query = new ResearchFocusQuery();
    query.setAsOf(date);
    query.setSize(1);
    var first = service.read(query);
    assertEquals(2, first.total());
    assertNull(first.portfolioImpact(), "没有可核验的持仓时不输出零仓位组合");
    assertEquals(1, first.items().size());
    assertEquals("DATA_GAP", first.items().getFirst().category());
    assertFalse(first.items().getFirst().explanation().contains("内部细节"));
    query.setPage(2);
    assertEquals("600000.SH", service.read(query).items().getFirst().symbol());
    verify(research, never()).read(eq("600002.SH"), any());
    query.setAsOf(LocalDate.of(2099, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> service.read(query));
  }

  @Test
  void confirmedHolidayDoesNotBecomeAMissingQuoteOrStartResearch() {
    var portfolio = mock(PortfolioService.class);
    var research = mock(HoldingResearchService.class);
    var market = mock(MarketService.class);
    when(portfolio.watchlistView())
        .thenReturn(List.of(new WatchlistView("600000.SH", "测试", 1, true)));
    when(market.isOpen(date)).thenReturn(false);
    var query = new ResearchFocusQuery();
    query.setAsOf(date);
    var result = new ResearchFocusServiceImpl(portfolio, research, market).read(query);
    assertEquals("WAIT", result.items().getFirst().category());
    assertTrue(result.items().getFirst().title().contains("休市"));
    verifyNoInteractions(research);
  }

  @Test
  void originalRiskRemainsVisibleWhenCurrentResearchFailsEvenAfterReading() {
    var portfolio = mock(PortfolioService.class);
    var research = mock(HoldingResearchService.class);
    var market = mock(MarketService.class);
    var plans = mock(PlanFollowupService.class);
    var service = new ResearchFocusServiceImpl(portfolio, research, market);
    ReflectionTestUtils.setField(service, "plans", plans);
    when(market.isOpen(date)).thenReturn(true);
    when(plans.originalRiskAlerts(date))
        .thenReturn(
            List.of(
                new PlanSummary(
                    6L,
                    "600001.SH",
                    "已有风险",
                    "SHORT",
                    "OPEN_PULLBACK",
                    false,
                    null,
                    previous,
                    previous,
                    "ORIGINAL_STOP_BREACHED",
                    "原风险线已触及",
                    true,
                    null,
                    "SINA",
                    java.util.Set.of("SINA"))));
    when(research.read(eq("600001.SH"), any())).thenThrow(new IllegalStateException("今日行情缺失"));
    var query = new ResearchFocusQuery();
    query.setAsOf(date);
    var item = service.read(query).items().getFirst();
    assertEquals("ORIGINAL_PLAN_ALERT", item.changeState());
    assertEquals(6L, item.originalPlanId());
    assertEquals("原风险线已触及", item.explanation());
  }

  @Test
  void riskOnLaterWatchlistPageMovesToFirstPageBeforePagination() {
    var portfolio = mock(PortfolioService.class);
    var research = mock(HoldingResearchService.class);
    var market = mock(MarketService.class);
    when(market.isOpen(date)).thenReturn(true);
    when(portfolio.watchlistView())
        .thenReturn(
            List.of(
                new WatchlistView("600001.SH", "普通", 0, true),
                new WatchlistView("600002.SH", "风险", 1, true)));
    when(research.read(eq("600001.SH"), any()))
        .thenReturn(view(date, "WAIT", false).toBuilder().symbol("600001.SH").build());
    when(research.read(eq("600002.SH"), any()))
        .thenReturn(view(date, "WAIT", true).toBuilder().symbol("600002.SH").build());
    var query = new ResearchFocusQuery();
    query.setAsOf(date);
    query.setSize(1);
    var service = new ResearchFocusServiceImpl(portfolio, research, market);
    assertEquals("600002.SH", service.read(query).items().getFirst().symbol());
    query.setPage(2);
    assertEquals("600001.SH", service.read(query).items().getFirst().symbol());
  }

  @Test
  void changeLabelsSeparateMethodSourceAndRecoveryFromMissingEvidence() {
    var now = view(date, "WAIT", false);
    var old = view(previous, "WAIT", true);
    var input =
        ResearchFocusInput.builder().current(now).previous(old).previousDate(previous).build();
    assertEquals("RECOVERED", ResearchFocusCalculator.compare(input).changeState());
    var incomplete =
        now.toBuilder()
            .horizons(
                List.of(
                    HoldingResearchView.Horizon.builder()
                        .code("SHORT")
                        .status("WAIT")
                        .actions(now.horizons().getFirst().actions())
                        .missing(List.of("经营规则尚缺"))
                        .build()))
            .build();
    assertNotEquals(
        "RECOVERED",
        ResearchFocusCalculator.compare(
                ResearchFocusInput.builder()
                    .current(incomplete)
                    .previous(old)
                    .previousDate(previous)
                    .build())
            .changeState());
    assertEquals(
        "METHOD_CHANGED",
        ResearchFocusCalculator.compare(
                ResearchFocusInput.builder()
                    .current(now.toBuilder().version("NEXT").build())
                    .previous(old)
                    .previousDate(previous)
                    .build())
            .changeState());
    assertEquals(
        "SOURCE_CHANGED",
        ResearchFocusCalculator.compare(
                ResearchFocusInput.builder()
                    .current(now.toBuilder().source("OTHER").build())
                    .previous(old)
                    .previousDate(previous)
                    .build())
            .changeState());
  }

  @Test
  void heldStocksOutsideWatchlistAreIncludedAndSameRiskUsesKnownWeight() {
    var portfolio = mock(PortfolioService.class);
    var research = mock(HoldingResearchService.class);
    var market = mock(MarketService.class);
    when(portfolio.watchlistView())
        .thenReturn(List.of(new WatchlistView("600001.SH", "自选", 1, true)));
    var p = new com.alphascope.portfolio.dto.PositionDO();
    p.setSymbol("600000.SH");
    p.setName("持仓");
    p.setAsOfDate(date);
    p.setQuantity(new java.math.BigDecimal("10"));
    when(portfolio.positionRiskEnabled()).thenReturn(true);
    when(portfolio.positions()).thenReturn(List.of(p));
    when(market.isOpen(date)).thenReturn(true);
    var account = new com.alphascope.portfolio.entity.PortfolioAccount();
    account.setAsOfDate(date);
    account.setTotalAssets(new java.math.BigDecimal("2000"));
    when(portfolio.account()).thenReturn(account);
    when(research.read(eq("600000.SH"), any())).thenReturn(view(date, "WAIT", false));
    when(research.read(eq("600001.SH"), any()))
        .thenReturn(view(date, "WAIT", false).toBuilder().symbol("600001.SH").build());
    var q = new ResearchFocusQuery();
    q.setAsOf(date);
    q.setSize(1);
    var result = new ResearchFocusServiceImpl(portfolio, research, market).read(q);
    assertEquals(2, result.total());
    assertEquals("600000.SH", result.items().getFirst().symbol());
    assertEquals(.5, result.portfolioImpact().knownWeight());
    assertTrue(result.items().getFirst().inputSources().contains("TEST"));
  }
}

package com.alphascope.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.context.TenantExecutor;
import com.alphascope.identity.vo.UserView;
import com.alphascope.market.service.MarketService;
import com.alphascope.portfolio.dto.WatchlistDO;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.scheduler.service.JobService;
import com.alphascope.scheduler.service.impl.ScheduledUpdateServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 收盘边界、周末补跑与调度用户归属，使用固定时间避免把当天盘中数据当作完整收盘。
 *
 * @author chenxuefeng
 */
class ScheduledUpdateServiceTest {
  @Test
  void mondayBeforeCloseUsesFridayAndRestoresUserScope() {
    var market = mock(MarketService.class);
    var portfolio = mock(PortfolioService.class);
    var jobs = mock(JobService.class);
    var tenants = mock(TenantExecutor.class);
    var stock = new WatchlistDO();
    stock.setEnabled(true);
    when(portfolio.watchlist()).thenReturn(List.of(stock));
    when(market.lastOpen(any(), eq(LocalDate.of(2026, 11, 8))))
        .thenReturn(LocalDate.of(2026, 11, 6));
    doAnswer(
            invocation -> {
              try (var ignored = TenantContext.bind(new UserView(7L, "fixture-user", "USER"))) {
                invocation.<Runnable>getArgument(0).run();
              }
              return null;
            })
        .when(tenants)
        .each(any());
    when(jobs.submit(any()))
        .thenAnswer(
            invocation -> {
              assertEquals(7L, TenantContext.id());
              return 12L;
            });
    new ScheduledUpdateServiceImpl(
            jobs,
            market,
            portfolio,
            tenants,
            Clock.fixed(Instant.parse("2026-11-09T07:00:00Z"), ZoneOffset.UTC))
        .daily();
    verify(jobs)
        .submit(
            argThat(
                command ->
                    command.automatic()
                        && command.businessDate().equals(LocalDate.of(2026, 11, 6))
                        && !command.force()));
    assertNull(TenantContext.user());
  }

  @Test
  void closeCutoffIncludesTodayButEmptyWatchlistCreatesNoJob() {
    var market = mock(MarketService.class);
    var portfolio = mock(PortfolioService.class);
    var jobs = mock(JobService.class);
    var tenants = mock(TenantExecutor.class);
    when(market.lastOpen(any(), eq(LocalDate.of(2026, 11, 9))))
        .thenReturn(LocalDate.of(2026, 11, 9));
    when(portfolio.watchlist()).thenReturn(List.of());
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return null;
            })
        .when(tenants)
        .each(any());
    new ScheduledUpdateServiceImpl(
            jobs,
            market,
            portfolio,
            tenants,
            Clock.fixed(Instant.parse("2026-11-09T08:10:00Z"), ZoneOffset.UTC))
        .daily();
    verify(market).syncCalendar(any(), eq(LocalDate.of(2026, 11, 9)));
    verifyNoInteractions(jobs);
  }

  @Test
  void missingCalendarDoesNotGuessWeekdaysOrBreakApplicationStartup() {
    var market = mock(MarketService.class);
    var jobs = mock(JobService.class);
    var tenants = mock(TenantExecutor.class);
    doThrow(new IllegalStateException("NO_VERIFIED_CALENDAR"))
        .when(market)
        .syncCalendar(any(), any());
    assertDoesNotThrow(
        () ->
            new ScheduledUpdateServiceImpl(
                    jobs, market, mock(PortfolioService.class), tenants, Clock.systemUTC())
                .daily());
    verifyNoInteractions(jobs, tenants);
  }
}

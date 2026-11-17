package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.fundamental.service.FinancialSyncService;
import com.alphascope.fundamental.service.ForecastService;
import com.alphascope.indicator.service.IndicatorService;
import com.alphascope.industry.service.IndustryService;
import com.alphascope.industry.service.ThemeCohortService;
import com.alphascope.intelligence.service.ReportService;
import com.alphascope.market.service.*;
import com.alphascope.notification.service.NotificationService;
import com.alphascope.portfolio.dto.WatchlistDO;
import com.alphascope.portfolio.entity.PortfolioAccount;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.scheduler.control.JobExecutionControl;
import com.alphascope.scheduler.dto.CompletedJobQuery;
import com.alphascope.scheduler.dto.JobInputState;
import com.alphascope.scheduler.dto.JobStatus;
import com.alphascope.scheduler.dto.JobSubmissionCommand;
import com.alphascope.scheduler.dto.StageExecution;
import com.alphascope.scheduler.dto.StageQuality;
import com.alphascope.scheduler.entity.JobExecution;
import com.alphascope.scheduler.mapper.*;
import com.alphascope.scheduler.quality.StageQualityInspector;
import com.alphascope.scheduler.service.impl.JobServiceImpl;
import com.alphascope.signal.service.SignalService;
import java.time.*;
import java.time.LocalDate;
import java.util.*;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.Test;

/**
 * 研究任务的数据源范围、执行顺序及输入指纹复用策略的验证。
 *
 * @author chenxuefeng
 */
class JobScopeTest {
  @Test
  void rejectsRealSameDayBeforeCloseWithoutOpeningQueueOrChangingDate() {
    var mapper = mock(JobMapper.class);
    var factory = mock(SqlSessionFactory.class);
    var market = mock(MarketService.class);
    var service =
        new JobServiceImpl(
            mapper,
            factory,
            market,
            mock(com.alphascope.scheduler.service.JobSnapshotService.class),
            mock(com.alphascope.scheduler.service.ResearchPipelineService.class),
            mock(JobExecutionControl.class));
    service.setClock(Clock.fixed(Instant.parse("2026-11-12T01:22:00Z"), ZoneOffset.UTC));
    var command =
        JobSubmissionCommand.builder()
            .reportType("DAILY")
            .businessDate(LocalDate.of(2026, 11, 12))
            .build();
    var error =
        assertThrows(
            com.alphascope.common.exception.BusinessException.class, () -> service.submit(command));
    assertTrue(error.getMessage().contains("15:30"));
    verifyNoInteractions(factory, mapper);
    var reached = new IllegalStateException("queue reached");
    when(factory.openSession(true)).thenThrow(reached);
    when(market.synthetic()).thenReturn(true);
    assertSame(reached, assertThrows(IllegalStateException.class, () -> service.submit(command)));
    when(market.synthetic()).thenReturn(false);
    service.setClock(Clock.fixed(Instant.parse("2026-11-12T07:30:00Z"), ZoneOffset.UTC));
    assertSame(reached, assertThrows(IllegalStateException.class, () -> service.submit(command)));
    verify(factory, times(2)).openSession(true); // 演示与15:30边界均允许继续进入队列校验。
  }

  @Test
  void forecastCollectionPrecedesMarketFailure() {
    var mapper = mock(JobMapper.class);
    var inputs = mock(JobInputMapper.class);
    var factory = mock(SqlSessionFactory.class);
    var session = mock(SqlSession.class);
    var locks = mock(JobMapper.class);
    var market = mock(MarketService.class);
    var portfolio = mock(PortfolioService.class);
    var forecasts = mock(ForecastService.class);
    when(factory.openSession(true)).thenReturn(session);
    when(session.getMapper(JobMapper.class)).thenReturn(locks);
    when(locks.lock(anyString())).thenReturn(1);
    when(mapper.claim(eq(99L), anyString())).thenReturn(1);
    when(mapper.get(99L)).thenReturn(fixtureJobExecution1());
    when(market.dataScope()).thenReturn("REAL:B");
    when(inputs.state(any(), anyBoolean())).thenReturn(fixtureJobInputState2());
    doThrow(new IllegalStateException("CALENDAR_UNAVAILABLE"))
        .when(market)
        .syncCalendar(any(), any());
    var jobs = fixtureJobs(mapper, inputs, factory, market, portfolio, forecasts);
    jobs.process(99L);
    var ordered = inOrder(forecasts, market);
    ordered.verify(forecasts).syncWatchlist();
    ordered.verify(market).syncCalendar(any(), any());
    verify(mapper)
        .complete(
            argThat(
                command ->
                    command.jobId() == 99L
                        && command.status() == JobStatus.FAILED
                        && "CALENDAR_UNAVAILABLE".equals(command.error())));
  }

  @Test
  void reuseRequiresFingerprintAndForceBypassesSuccess() {
    var mapper = mock(JobMapper.class);
    var inputs = mock(JobInputMapper.class);
    var factory = mock(SqlSessionFactory.class);
    var session = mock(SqlSession.class);
    var locks = mock(JobMapper.class);
    var market = mock(MarketService.class);
    var portfolio = mock(PortfolioService.class);
    when(factory.openSession(true)).thenReturn(session);
    when(session.getMapper(JobMapper.class)).thenReturn(locks);
    when(locks.lock(anyString())).thenReturn(1);
    when(market.dataScope()).thenReturn("REAL:B");
    when(inputs.state(any(), anyBoolean())).thenReturn(fixtureJobInputState2());
    var watchA = fixtureWatchlistDO4();
    var watchB = fixtureWatchlistDO5();
    when(portfolio.watchlist()).thenReturn(List.of(watchA, watchB), List.of(watchB, watchA));
    when(portfolio.positions()).thenReturn(List.of());
    when(portfolio.account()).thenReturn(new PortfolioAccount());
    when(mapper.active(anyString(), any(), anyString())).thenReturn(null);
    when(mapper.completed(any(CompletedJobQuery.class))).thenReturn(10L);
    doAnswer(
            a -> {
              ((JobExecution) a.getArgument(0)).setId(11L);
              return null;
            })
        .when(mapper)
        .start(any());
    var jobs = fixtureJobs(mapper, inputs, factory, market, portfolio, mock(ForecastService.class));
    var date = LocalDate.of(2026, 10, 30);
    assertEquals(
        10,
        jobs.submit(
            JobSubmissionCommand.builder()
                .reportType("DAILY")
                .businessDate(date)
                .force(false)
                .retryOf(null)
                .build()));
    assertEquals(
        10,
        jobs.submit(
            JobSubmissionCommand.builder()
                .reportType("DAILY")
                .businessDate(date)
                .force(false)
                .retryOf(null)
                .build()));
    assertEquals(
        11,
        jobs.submit(
            JobSubmissionCommand.builder()
                .reportType("DAILY")
                .businessDate(date)
                .force(true)
                .retryOf(null)
                .build()));
    var queries = org.mockito.ArgumentCaptor.forClass(CompletedJobQuery.class);
    verify(mapper, times(2)).completed(queries.capture());
    assertEquals("DAILY", queries.getValue().getJobName());
    assertEquals(date, queries.getValue().getBusinessDate());
    assertEquals("REAL:B", queries.getValue().getDataScope());
    assertNotNull(queries.getValue().getInputHash());
    assertEquals(
        queries.getAllValues().get(0).getInputHash(), queries.getAllValues().get(1).getInputHash());
    verify(locks, times(3)).unlock(anyString());
  }

  /** 明确设置该用例需要的字段，避免JSON转换掩盖测试数据类型。 */
  private JobServiceImpl fixtureJobs(
      JobMapper mapper,
      JobInputMapper inputs,
      SqlSessionFactory factory,
      MarketService market,
      PortfolioService portfolio,
      ForecastService forecasts) {
    var signals = mock(SignalService.class);
    var cohorts = mock(ThemeCohortService.class);
    var observations = mock(MarketObservationService.class);
    var snapshots =
        new com.alphascope.scheduler.service.impl.JobSnapshotServiceImpl(
            inputs, market, portfolio, signals, cohorts, observations);
    var control = testControl();
    var pipeline =
        new com.alphascope.scheduler.service.impl.ResearchPipelineServiceImpl(
            market,
            portfolio,
            mock(IndicatorService.class),
            signals,
            mock(IndustryService.class),
            mock(ScoreService.class),
            mock(ReportService.class),
            mock(NotificationService.class),
            mock(FinancialSyncService.class),
            mock(MarketBreadthService.class),
            cohorts,
            observations,
            forecasts,
            inputs,
            snapshots,
            control,
            mock(StageQualityInspector.class),
            mock(com.alphascope.market.service.MarketUniverseService.class));
    return new JobServiceImpl(mapper, factory, market, snapshots, pipeline, control);
  }

  private JobExecution fixtureJobExecution1() {
    var row = new JobExecution();
    row.setId(99L);
    row.setAttempt(1);
    row.setStatus("PENDING");
    row.setDataScope("REAL:B");
    row.setJobName("DAILY");
    row.setBusinessDate(LocalDate.parse("2026-10-30"));
    return row;
  }

  /** 明确设置该用例需要的字段，避免JSON转换掩盖测试数据类型。 */
  private JobInputState fixtureJobInputState2() {
    var row = new JobInputState();
    row.setPrices("1");
    return row;
  }

  /** 明确设置该用例需要的字段，避免JSON转换掩盖测试数据类型。 */
  private WatchlistDO fixtureWatchlistDO4() {
    var row = new WatchlistDO();
    row.setSymbol("300476.SZ");
    row.setPriority(0);
    return row;
  }

  /** 明确设置该用例需要的字段，避免JSON转换掩盖测试数据类型。 */
  private WatchlistDO fixtureWatchlistDO5() {
    var row = new WatchlistDO();
    row.setSymbol("002837.SZ");
    row.setPriority(0);
    return row;
  }

  /** 此用例只核对编排顺序，取消/截止机制由专门的控制器测试验证。 */
  private JobExecutionControl testControl() {
    var control = mock(JobExecutionControl.class);
    when(control.execute(any(StageExecution.class)))
        .thenAnswer(
            call -> {
              StageExecution stage = call.getArgument(0);
              stage.action().run();
              return StageQuality.available("编排顺序测试");
            });
    return control;
  }
}

package com.alphascope.scheduler.service.impl;

import com.alphascope.common.task.PipelineStage;
import com.alphascope.common.task.TaskStoppedException;
import com.alphascope.common.util.Jsons;
import com.alphascope.fundamental.service.FinancialSyncService;
import com.alphascope.fundamental.service.ForecastService;
import com.alphascope.indicator.service.IndicatorService;
import com.alphascope.industry.service.IndustryService;
import com.alphascope.industry.service.ThemeCohortService;
import com.alphascope.intelligence.service.ReportService;
import com.alphascope.market.service.FullMarketService;
import com.alphascope.market.service.MarketBreadthService;
import com.alphascope.market.service.MarketObservationService;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.service.MarketUniverseService;
import com.alphascope.market.service.ScoreService;
import com.alphascope.notification.service.NotificationService;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.scheduler.control.JobExecutionControl;
import com.alphascope.scheduler.dto.DataQualityStatus;
import com.alphascope.scheduler.dto.JobStatus;
import com.alphascope.scheduler.dto.PipelineCommand;
import com.alphascope.scheduler.dto.PipelineOutcome;
import com.alphascope.scheduler.dto.StageExecution;
import com.alphascope.scheduler.mapper.JobInputMapper;
import com.alphascope.scheduler.quality.StageQualityInspector;
import com.alphascope.scheduler.service.JobSnapshotService;
import com.alphascope.scheduler.service.ResearchPipelineService;
import com.alphascope.signal.service.SignalService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * ResearchPipelineService的实现；只编排业务阶段，各次执行上下文独立，不共享可变阶段字段。
 *
 * @author chenxuefeng
 */
@Service
public class ResearchPipelineServiceImpl implements ResearchPipelineService {
  private final MarketService market;
  private final PortfolioService portfolio;
  private final IndicatorService indicators;
  private final SignalService signals;
  private final IndustryService industry;
  private final ScoreService scores;
  private final ReportService reports;
  private final NotificationService notification;
  private final FinancialSyncService financialSync;
  private final MarketBreadthService breadth;
  private final ThemeCohortService cohorts;
  private final MarketObservationService observations;
  private final ForecastService forecasts;
  private final JobInputMapper inputs;
  private final JobSnapshotService snapshots;
  private final JobExecutionControl control;
  private final StageQualityInspector qualityInspector;
  private final MarketUniverseService universe;

  @org.springframework.beans.factory.annotation.Autowired private FullMarketService fullMarket;

  @org.springframework.beans.factory.annotation.Autowired
  private com.alphascope.planning.service.PlanFollowupService planFollowups;

  @org.springframework.beans.factory.annotation.Autowired
  private com.alphascope.intelligence.service.ResearchObservationService observationJournal;

  @org.springframework.beans.factory.annotation.Autowired
  private com.alphascope.intelligence.service.ForwardValidationService forwardValidation;

  public ResearchPipelineServiceImpl(
      MarketService market,
      PortfolioService portfolio,
      IndicatorService indicators,
      SignalService signals,
      IndustryService industry,
      ScoreService scores,
      ReportService reports,
      NotificationService notification,
      FinancialSyncService financialSync,
      MarketBreadthService breadth,
      ThemeCohortService cohorts,
      MarketObservationService observations,
      ForecastService forecasts,
      JobInputMapper inputs,
      JobSnapshotService snapshots,
      JobExecutionControl control,
      StageQualityInspector qualityInspector,
      MarketUniverseService universe) {
    this.market = market;
    this.portfolio = portfolio;
    this.indicators = indicators;
    this.signals = signals;
    this.industry = industry;
    this.scores = scores;
    this.reports = reports;
    this.notification = notification;
    this.financialSync = financialSync;
    this.breadth = breadth;
    this.cohorts = cohorts;
    this.observations = observations;
    this.forecasts = forecasts;
    this.inputs = inputs;
    this.snapshots = snapshots;
    this.control = control;
    this.qualityInspector = qualityInspector;
    this.universe = universe;
  }

  /** {@inheritDoc} */
  @Override
  public PipelineOutcome run(PipelineCommand command) {
    long id = command.jobId();
    String type = command.reportType();
    LocalDate requested = command.businessDate();
    var run = new Run(command);
    try {
      if ("MARKET_UNIVERSE".equals(type)) {
        if (market.synthetic()) throw new IllegalStateException("真实目录补齐不能在演示模式执行");
        run.stage("MARKET_UNIVERSE", () -> universe.sync(requested));
        var snapshot = universe.snapshot(requested);
        if (!"AVAILABLE".equals(snapshot.status())) throw new IllegalStateException("目录归档未通过核验");
        return new PipelineOutcome(
            JobStatus.SUCCESS, run.aggregateQuality(), null, snapshot.contentHash());
      }
      if ("MARKET_FULL".equals(type)) {
        run.stage("FULL_MARKET_ANALYSIS", () -> fullMarket.sync(requested));
        var observation = fullMarket.observation(requested);
        return new PipelineOutcome(
            JobStatus.SUCCESS,
            "AVAILABLE".equals(observation.status())
                ? DataQualityStatus.AVAILABLE
                : DataQualityStatus.PARTIAL,
            null,
            Jsons.hash(Jsons.write(observation)));
      }
      if ("MARKET_HISTORY".equals(type)) {
        run.stage("CALENDAR", () -> market.syncCalendar(requested.minusDays(140), requested));
        if (!market.isOpen(requested))
          return new PipelineOutcome(
              JobStatus.SKIPPED, DataQualityStatus.NOT_APPLICABLE, "目标日不是已核验交易日", null);
        run.stage("MARKET_OBSERVATION", () -> observations.sync(requested));
        var observation = observations.view(requested);
        if (observation.analysis() == null
            || !requested.equals(observation.analysis().businessDate())) {
          throw new IllegalStateException("历史补齐未取得目标日期的市场观察，请查看阶段缺项与通道原因");
        }
        return new PipelineOutcome(
            JobStatus.SUCCESS,
            run.aggregateQuality(),
            null,
            Jsons.hash(Jsons.write(observation.analysis())));
      }
      var state = snapshots.capture(requested);
      run.stage("FORECASTS", forecasts::syncWatchlist);
      run.stage("CALENDAR", () -> market.syncCalendar(requested.minusYears(2), requested));
      LocalDate date =
          type.equals("WEEKLY")
              ? market.lastOpen(requested.with(DayOfWeek.MONDAY), requested)
              : requested;
      if (date == null || !market.isOpen(date)) {
        return new PipelineOutcome(
            JobStatus.SKIPPED, DataQualityStatus.NOT_APPLICABLE, "非交易日", null);
      }
      run.date = date;
      run.stage("MARKET_UNIVERSE", () -> universe.refresh(date));
      run.stage("THEME_MEMBERS", () -> cohorts.prepare(date));
      Set<String> symbols = new LinkedHashSet<>();
      symbols.add("000300.SH");
      for (var stock : portfolio.watchlist()) {
        if (Boolean.TRUE.equals(stock.getEnabled())) {
          symbols.add(stock.getSymbol());
        }
      }
      Set<String> watchSymbols = new LinkedHashSet<>(symbols);
      symbols.addAll(cohorts.symbols(date));
      run.stage(
          "MARKET_SYNC",
          () -> {
            for (String symbol : symbols) {
              market.sync(
                  symbol,
                  watchSymbols.contains(symbol) ? date.minusYears(2) : date.minusDays(140),
                  date);
            }
          });
      run.stage(
          "DATA_QUALITY",
          () -> {
            for (String symbol : symbols) {
              if ("UNKNOWN".equals(market.dayStatus(symbol, date))) {
                throw new IllegalStateException("业务日期行情缺失: " + symbol);
              }
            }
          });
      run.stage(
          "RESEARCH_DATA",
          () -> {
            for (String symbol : watchSymbols) {
              if (!symbol.equals("000300.SH")) {
                financialSync.sync(symbol, date);
              }
            }
            breadth.sync(date);
          });
      run.stage("FULL_MARKET_ANALYSIS", () -> fullMarket.refresh(date));
      run.stage("MARKET_OBSERVATION", () -> observations.refreshAfterClose(date));
      var ingested = inputs.state(requested, market.synthetic());
      state.put("financials", ingested.getFinancials());
      state.put("marketBreadth", ingested.getMarketBreadth());
      state.put("cohorts", ingested.getCohorts());
      state.put("marketObservation", ingested.getMarketObservation());
      state.put("forecasts", ingested.getForecasts());
      run.stage(
          "INDICATORS",
          () -> {
            for (String symbol : symbols) {
              indicators.calculateRange(symbol, date.minusMonths(4), date);
            }
          });
      run.stage(
          "SCORE_RULES",
          () -> {
            industry.calculateRange(date.minusDays(10), date);
            scores.calculate(date);
            for (String symbol : watchSymbols) {
              if ("TRADING".equals(market.dayStatus(symbol, date))) {
                signals.calculate(symbol, date);
              }
            }
          });
      run.stage(
          "PLAN_REVIEW",
          () -> {
            var captured = observationJournal.capture(date);
            if (!captured.issues().isEmpty())
              org.slf4j.LoggerFactory.getLogger(getClass())
                  .warn("每日判断留档有缺项 date={} issues={}", date, captured.issues());
            planFollowups.reviewAll(date);
            forwardValidation.evaluate(date);
          });
      long[] report = {0};
      run.stage("REPORT", () -> report[0] = reports.generate(type, date, id));
      run.stage(
          "NOTIFICATION",
          () ->
              notification.notifyLocal(
                  report[0],
                  market.synthetic(),
                  market.dataScope() + ":" + type + ":" + requested + ":" + id));
      state.put("prices", inputs.state(requested, market.synthetic()).getPrices());
      return new PipelineOutcome(
          JobStatus.SUCCESS, run.aggregateQuality(), null, Jsons.hash(Jsons.write(state)));

    } catch (TaskStoppedException e) {
      return new PipelineOutcome(
          JobStatus.valueOf(e.getReason().name()),
          DataQualityStatus.UNAVAILABLE,
          e.getMessage(),
          null);
    } catch (Exception e) {
      // 采集失败也追加明确的资料不足待办；不把缺失行情留成无提醒的旧结论。
      if (java.util.Set.of("DAILY", "WEEKLY").contains(type) && !market.synthetic()) {
        try {
          run.stage("PLAN_REVIEW", () -> planFollowups.reviewAll(run.date));
        } catch (com.alphascope.common.task.TaskStoppedException stopped) {
          return new PipelineOutcome(
              JobStatus.valueOf(stopped.getReason().name()),
              DataQualityStatus.UNAVAILABLE,
              stopped.getMessage(),
              null);
        } catch (RuntimeException followupFailure) {
          org.slf4j.LoggerFactory.getLogger(getClass())
              .warn("采集失败后的计划跟踪未完成 job={}", id, followupFailure);
        }
      }
      String message = e.getMessage();
      var diagnostic = com.alphascope.common.diagnostic.FailureDiagnostics.from(e);
      if (diagnostic.code().startsWith("DATABASE_"))
        message = diagnostic.title() + "：" + diagnostic.reason() + "。" + diagnostic.action();
      return new PipelineOutcome(
          JobStatus.FAILED,
          run.aggregateQuality(),
          message == null
              ? e.getClass().getSimpleName()
              : message.substring(0, Math.min(500, message.length())),
          null);
    }
  }

  /**
   * 单次流水线的局部质量与业务日期，不泄漏到另一任务。
   *
   * @author chenxuefeng
   */
  private final class Run {
    /** 已领取任务上下文。 */
    private final PipelineCommand command;

    /** 实际研究交易日，周报在日历核验后更新。 */
    private LocalDate date;

    /** 本次已完成阶段质量。 */
    private final List<DataQualityStatus> qualities = new ArrayList<>();

    Run(PipelineCommand command) {
      this.command = command;
      this.date = command.businessDate();
    }

    void stage(String name, Runnable action) {
      var stage = PipelineStage.valueOf(name);
      var quality =
          control.execute(
              StageExecution.builder()
                  .jobId(command.jobId())
                  .ownerId(command.ownerId())
                  .stage(stage)
                  .retryOf(command.retryOf())
                  .action(action)
                  .quality(() -> qualityInspector.inspect(stage, date))
                  .build());
      qualities.add(quality.status());
    }

    private DataQualityStatus aggregateQuality() {
      var available = DataQualityStatus.AVAILABLE;
      var ignored = DataQualityStatus.NOT_APPLICABLE;
      if (qualities.isEmpty()) {
        return DataQualityStatus.UNKNOWN;
      }
      if (qualities.stream().allMatch(value -> value == ignored)) {
        return ignored;
      }
      if (qualities.stream().allMatch(value -> value == available || value == ignored)) {
        return available;
      }
      if (qualities.contains(available) || qualities.contains(DataQualityStatus.PARTIAL)) {
        return DataQualityStatus.PARTIAL;
      }
      return DataQualityStatus.UNAVAILABLE;
    }
  }
}

package com.alphascope.scheduler.quality;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.dto.OptionalDateRangeRequest;
import com.alphascope.common.task.PipelineStage;
import com.alphascope.fundamental.service.FinancialSyncService;
import com.alphascope.fundamental.service.ForecastService;
import com.alphascope.fundamental.service.FundamentalService;
import com.alphascope.indicator.service.IndicatorService;
import com.alphascope.market.service.FullMarketService;
import com.alphascope.market.service.MarketBreadthService;
import com.alphascope.market.service.MarketObservationService;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.service.MarketUniverseService;
import com.alphascope.portfolio.dto.WatchlistDO;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.scheduler.dto.DataQualityStatus;
import com.alphascope.scheduler.dto.StageQuality;
import com.alphascope.scheduler.dto.StageQualityIssue;
import com.alphascope.signal.service.SignalService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 只读核验阶段实际产物，不根据Runnable正常返回推断数据完整，也不触发上游请求。
 *
 * @author chenxuefeng
 */
@Component
public class StageQualityInspector {
  private final PortfolioService portfolio;
  private final MarketService market;
  private final ForecastService forecasts;
  private final FundamentalService financials;
  private final FinancialSyncService financialSync;
  private final MarketBreadthService breadth;
  private final MarketObservationService observations;
  private final IndicatorService indicators;
  private final SignalService signals;
  private final MarketUniverseService universe;

  @org.springframework.beans.factory.annotation.Autowired private FullMarketService fullMarket;

  @org.springframework.beans.factory.annotation.Autowired
  private com.alphascope.planning.service.PlanFollowupService planFollowups;

  public StageQualityInspector(
      PortfolioService portfolio,
      MarketService market,
      ForecastService forecasts,
      FundamentalService financials,
      FinancialSyncService financialSync,
      MarketBreadthService breadth,
      MarketObservationService observations,
      IndicatorService indicators,
      SignalService signals,
      MarketUniverseService universe) {
    this.portfolio = portfolio;
    this.market = market;
    this.forecasts = forecasts;
    this.financials = financials;
    this.financialSync = financialSync;
    this.breadth = breadth;
    this.observations = observations;
    this.indicators = indicators;
    this.signals = signals;
    this.universe = universe;
  }

  /**
   * 核查本阶段的时点可用性、覆盖率及缺项。
   *
   * @param stage 已执行阶段
   * @param date 实际研究业务日期，周报使用解析后的交易日
   * @return 独立数据质量和资源级原因；旧值或未知状态不提升为AVAILABLE
   */
  public StageQuality inspect(PipelineStage stage, LocalDate date) {
    if (market.synthetic()
        && Set.of(
                PipelineStage.FORECASTS,
                PipelineStage.RESEARCH_DATA,
                PipelineStage.MARKET_UNIVERSE,
                PipelineStage.FULL_MARKET_ANALYSIS,
                PipelineStage.MARKET_OBSERVATION)
            .contains(stage)) {
      return new StageQuality(DataQualityStatus.NOT_APPLICABLE, 0, 0, List.of(), "演示模式不采集真实增强证据");
    }
    List<StageQualityIssue> issues = new ArrayList<>();
    int expected = 0, available = 0;
    switch (stage) {
      case PLAN_REVIEW -> {
        var todo = planFollowups.todos(date);
        return new StageQuality(
            todo.total() == 0
                ? DataQualityStatus.NOT_APPLICABLE
                : todo.reviewed() == todo.total()
                    ? DataQualityStatus.AVAILABLE
                    : DataQualityStatus.PARTIAL,
            todo.reviewed(),
            todo.total(),
            List.of(),
            "计划已按本次收盘资料跟踪；风险"
                + todo.risk()
                + "项、条件变化"
                + todo.changed()
                + "项、待补资料"
                + todo.data()
                + "项、待复核"
                + todo.due()
                + "项，请到研究计划处理");
      }
      case FULL_MARKET_ANALYSIS -> {
        var value = fullMarket.observation(date);
        return new StageQuality(
            value != null && "AVAILABLE".equals(value.status())
                ? DataQualityStatus.AVAILABLE
                : DataQualityStatus.PARTIAL,
            value == null || value.breadth() == null ? 0 : value.breadth().valid60(),
            value == null || value.breadth() == null ? 0 : value.breadth().trading(),
            List.of(),
            "三所同日截面、停牌和同源历史分别验收；缺窗口不形成完整评级");
      }
      case MARKET_UNIVERSE -> {
        var view = universe.snapshot(date);
        boolean valid =
            view != null
                && date.equals(view.businessDate())
                && "AVAILABLE".equals(view.status())
                && view.memberCount() != null
                && view.memberCount() > 0;
        return new StageQuality(
            valid ? DataQualityStatus.AVAILABLE : DataQualityStatus.UNAVAILABLE,
            valid ? view.memberCount() : 0,
            view == null || view.memberCount() == null ? 0 : view.memberCount(),
            valid
                ? List.of()
                : List.of(
                    new StageQualityIssue(
                        "BSE_A", "SINA", "NOT_ARCHIVED", "该日北交所在市目录未归档；不使用当前名单代替历史")),
            "仅核验供应商当前在市目录；不表示收盘行情、历史窗口或完整全A评级可用");
      }
      case FORECASTS -> {
        for (var forecast : forecasts.forReport(date)) {
          expected++;
          if ("AVAILABLE".equals(forecast.status())) {
            available++;
          } else {
            issues.add(
                new StageQualityIssue(
                    forecast.symbol(),
                    "EASTMONEY_PUBLIC",
                    forecast.status(),
                    "该报告截止时点尚无可用预测；当前采集不能倒填历史可得性"));
          }
        }
        if (expected == 0 && !watchlist().isEmpty()) {
          expected = watchlist().size();
          issues.add(new StageQualityIssue("WATCHLIST", null, "NOT_CHECKED", "未返回自选预测核验结果"));
        }
      }
      case RESEARCH_DATA -> {
        for (var stock : watchlist()) {
          expected++;
          if (!financials.reports(stock.getSymbol(), date).isEmpty()) {
            available++;
            var attempt = financialSync.status(stock.getSymbol(), date);
            if (attempt != null && "UNAVAILABLE".equals(attempt.status())) {
              issues.add(
                  new StageQualityIssue(
                      stock.getSymbol(), "EASTMONEY_FREE", "CACHE_USED", "最近刷新失败，保留时点可用的已保存财报"));
            }
          } else {
            issues.add(
                new StageQualityIssue(
                    stock.getSymbol(), "EASTMONEY_FREE", "UNAVAILABLE", "业务截止前没有可用累计财报"));
          }
        }
        expected++;
        var snapshot = breadth.snapshot(date);
        if (snapshot != null
            && date.equals(snapshot.getBusinessDate())
            && "COMPLETE".equals(snapshot.getStatus())) {
          available++;
        } else {
          issues.add(
              new StageQualityIssue(
                  "SH_SZ_A",
                  snapshot == null ? null : snapshot.getSource(),
                  snapshot == null ? "UNKNOWN" : snapshot.getStatus(),
                  "同日沪深截面尚未满足完整覆盖"));
        }
      }
      case MARKET_OBSERVATION -> {
        expected = 1;
        var view = observations.view(date);
        if (view != null
            && view.analysis() != null
            && date.equals(view.analysis().businessDate())
            && "AVAILABLE".equals(view.status())) {
          available++;
        } else {
          issues.add(
              new StageQualityIssue(
                  "SH_SZ_A_HISTORY",
                  null,
                  view == null ? "UNKNOWN" : view.status(),
                  "同日趋势、历史量能或复权广度仍有缺项，较早日期的观察不能替代本期"));
        }
      }
      case INDICATORS -> {
        for (var stock : watchlist()) {
          String day = market.dayStatus(stock.getSymbol(), date);
          if ("SUSPENDED".equals(day) || "NOT_LISTED".equals(day) || "DELISTED".equals(day)) {
            continue;
          }
          expected++;
          var rows =
              indicators.list(
                  stock.getSymbol(), DateRangeRequest.builder().start(date).end(date).build());
          if (!rows.isEmpty() && "COMPLETE".equals(rows.getLast().getDataStatus())) {
            available++;
          } else {
            issues.add(
                new StageQualityIssue(
                    stock.getSymbol(), null, "INSUFFICIENT_HISTORY", "当日指标或同源历史不足"));
          }
        }
      }
      case SCORE_RULES -> {
        for (var stock : watchlist()) {
          String day = market.dayStatus(stock.getSymbol(), date);
          if ("SUSPENDED".equals(day) || "NOT_LISTED".equals(day) || "DELISTED".equals(day)) {
            continue;
          }
          var rows =
              signals.list(
                  stock.getSymbol(),
                  OptionalDateRangeRequest.builder().start(date).end(date).build());
          var catalog = signals.catalog();
          if (catalog == null || catalog.definitions().isEmpty()) {
            expected++;
            issues.add(
                new StageQualityIssue(
                    stock.getSymbol(), null, "CATALOG_UNAVAILABLE", "当前规则清单未能核验，不能从已保存记录推断完整"));
            continue;
          }
          var current =
              rows.stream()
                  .filter(row -> catalog.version().equals(row.getRuleVersion()))
                  .collect(Collectors.groupingBy(row -> row.getRuleCode()));
          for (var definition : catalog.definitions()) {
            if (definition.applicable()) {
              expected++;
            }
            var matching = current.getOrDefault(definition.code(), List.of());
            String resource = stock.getSymbol() + ":" + definition.code() + ":" + catalog.version();
            if (matching.size() != 1) {
              issues.add(
                  new StageQualityIssue(
                      resource,
                      null,
                      matching.isEmpty() ? "MISSING_RULE" : "DUPLICATE_RULE",
                      matching.isEmpty() ? "当前版本的应有规则记录缺失" : "当前版本同一规则出现重复记录"));
              continue;
            }
            String status = matching.getFirst().getStatus();
            if (!definition.applicable()) {
              if (!"NOT_APPLICABLE".equals(status)) {
                issues.add(
                    new StageQualityIssue(
                        resource, null, "APPLICABILITY_MISMATCH", "当前模式应明确标记不适用"));
              }
            } else if (Set.of("TRIGGERED", "NOT_TRIGGERED").contains(status)) {
              available++;
            } else {
              issues.add(
                  new StageQualityIssue(resource, null, status, "应评估规则没有有效结果；不适用或证据不足不能当作已核验"));
            }
          }
        }
      }
      default -> {
        return StageQuality.available("阶段动作及原有强制校验通过；不代表未启用的研究维度已接入");
      }
    }
    DataQualityStatus status =
        expected == 0
            ? DataQualityStatus.NOT_APPLICABLE
            : available == expected && issues.isEmpty()
                ? DataQualityStatus.AVAILABLE
                : available > 0 ? DataQualityStatus.PARTIAL : DataQualityStatus.UNAVAILABLE;
    return new StageQuality(
        status, available, expected, issues, "按实际研究截止日期只读核验；可选证据缺项允许报告生成，但必须显示质量不足");
  }

  private List<WatchlistDO> watchlist() {
    return portfolio.watchlist().stream()
        .filter(row -> Boolean.TRUE.equals(row.getEnabled()))
        .toList();
  }
}

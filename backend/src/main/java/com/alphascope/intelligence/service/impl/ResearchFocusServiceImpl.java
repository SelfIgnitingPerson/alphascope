package com.alphascope.intelligence.service.impl;

import com.alphascope.common.util.Jsons;
import com.alphascope.intelligence.calculator.PortfolioImpactCalculator;
import com.alphascope.intelligence.calculator.PositionImpactCalculator;
import com.alphascope.intelligence.calculator.ResearchFocusCalculator;
import com.alphascope.intelligence.dto.DailyObservationEvidence;
import com.alphascope.intelligence.dto.HoldingResearchQuery;
import com.alphascope.intelligence.dto.ObservationQuery;
import com.alphascope.intelligence.dto.ResearchFocusInput;
import com.alphascope.intelligence.dto.ResearchFocusQuery;
import com.alphascope.intelligence.entity.ResearchObservation;
import com.alphascope.intelligence.service.HoldingResearchService;
import com.alphascope.intelligence.service.ResearchFocusService;
import com.alphascope.intelligence.service.ResearchObservationService;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.intelligence.vo.PositionImpact;
import com.alphascope.intelligence.vo.ResearchFocusView;
import com.alphascope.intelligence.vo.ResearchPriority;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.service.MarketService;
import com.alphascope.operations.policy.SourceEvidence;
import com.alphascope.operations.service.SourceLicenseService;
import com.alphascope.planning.service.PlanFollowupService;
import com.alphascope.planning.vo.PlanSummary;
import com.alphascope.portfolio.dto.PositionDO;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.portfolio.vo.WatchlistView;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 从已保存行情生成当前账号研究待办，既不访问供应商也不写入个人计划。
 *
 * @author chenxuefeng
 */
@Service
public class ResearchFocusServiceImpl implements ResearchFocusService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ResearchFocusServiceImpl.class);
  private final PortfolioService portfolio;
  private final HoldingResearchService research;
  private final MarketService market;
  @Autowired private PlanFollowupService plans;
  @Autowired private SourceLicenseService planLicenses;

  @Autowired private ResearchObservationService journal;

  public ResearchFocusServiceImpl(
      PortfolioService portfolio, HoldingResearchService research, MarketService market) {
    this.portfolio = portfolio;
    this.research = research;
    this.market = market;
  }

  /** {@inheritDoc} */
  @Override
  public ResearchFocusView read(ResearchFocusQuery query) {
    query.validate();
    if (query.getSize() > 20) throw new IllegalArgumentException("研究重点每页最多核对20只，请翻页继续");
    var date = query.getAsOf();
    if (date == null
        || date.getYear() < 1990
        || date.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai"))))
      throw new IllegalArgumentException("研究日期须在1990年至上海当前日期之间");
    var issues = new ArrayList<String>();
    var originalRisks = new java.util.HashMap<String, PlanSummary>();
    if (plans != null)
      try {
        for (var plan : plans.originalRiskAlerts(date))
          originalRisks.putIfAbsent(plan.symbol(), plan);
      } catch (RuntimeException unavailable) {
        LOGGER.warn("原计划风险读取失败 date={}", date, unavailable);
        issues.add("原计划风险暂未读全，不能据此认为原风险已经解除；当前个股研究继续展示");
      }
    var watched = portfolio.watchlistView().stream().filter(w -> w.enabled()).toList();
    var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    boolean currentWorkspace = false;
    try {
      var end =
          java.time.LocalTime.now(ZoneId.of("Asia/Shanghai"))
                  .isBefore(java.time.LocalTime.of(15, 30))
              ? today.minusDays(1)
              : today;
      var latest =
          market
              .expectedDates(
                  TradingDatesQuery.builder()
                      .symbol("000300.SH")
                      .start(end.minusDays(14))
                      .end(end)
                      .build())
              .stream()
              .max(LocalDate::compareTo)
              .orElse(null);
      currentWorkspace = date.equals(latest);
    } catch (RuntimeException calendarMissing) {
      issues.add("最近收盘日历未确认，个人组合按严格历史日核对，不猜测当前账户");
    }
    var knowledgeDate = currentWorkspace ? today : date;
    boolean positionRiskEnabled = portfolio.positionRiskEnabled();
    var context = positionRiskEnabled ? portfolio.context(knowledgeDate) : null;
    var positions =
        !positionRiskEnabled
            ? java.util.List.<com.alphascope.portfolio.dto.PositionDO>of()
            : context == null ? portfolio.positions() : context.positions();
    var account =
        !positionRiskEnabled ? null : context == null ? portfolio.account() : context.account();
    var held =
        positions.stream()
            .filter(
                p ->
                    p.getQuantity() != null
                        && p.getQuantity().signum() > 0
                        && p.getAsOfDate() != null
                        && !p.getAsOfDate().isAfter(knowledgeDate))
            .toList();
    // 持仓即使没有加入自选，也必须进入风险核对；未知或未来登记不据此推测空仓。
    var scope = new LinkedHashMap<String, WatchlistView>();
    watched.forEach(w -> scope.put(w.symbol(), w));
    for (var p : positions)
      if (p.getQuantity() != null
          && p.getQuantity().signum() > 0
          && p.getAsOfDate() != null
          && !p.getAsOfDate().isAfter(knowledgeDate))
        scope.putIfAbsent(p.getSymbol(), new WatchlistView(p.getSymbol(), p.getName(), 0, true));
    originalRisks
        .values()
        .forEach(
            p -> scope.putIfAbsent(p.symbol(), new WatchlistView(p.symbol(), p.name(), 0, true)));
    var items = new ArrayList<ResearchFocusView.Item>();
    Boolean open = null;
    try {
      open = market.isOpen(date);
    } catch (IllegalStateException missingCalendar) {
      LOGGER.debug("研究重点所选日日历缺失 date={}", date);
    }
    for (var w : scope.values()) {
      var original = originalRisks.get(w.symbol());
      if (Boolean.FALSE.equals(open)) {
        var item =
            ResearchFocusView.Item.builder()
                .symbol(w.symbol())
                .name(w.name())
                .category("WAIT")
                .title("所选日期已确认休市")
                .explanation("休市日没有当日收盘，不属于行情缺失；请用页头“最近收盘日”进入交易日研究。")
                .newlyMatched(false)
                .positionImpact(missingImpact(w.symbol(), held))
                .changeState("UNCOMPARABLE")
                .changeLabel("休市日不比较交易信号")
                .priorities(List.of())
                .build();
        items.add(withOriginalRisk(item, original, issues));
        continue;
      }
      try {
        var current = research.read(w.symbol(), at(date));
        LocalDate previousDate = null;
        HoldingResearchView previous = null;
        try {
          previousDate =
              market
                  .expectedDates(
                      TradingDatesQuery.builder()
                          .symbol(w.symbol())
                          .source(current.source())
                          .start(date.minusDays(14))
                          .end(date.minusDays(1))
                          .build())
                  .stream()
                  .max(LocalDate::compareTo)
                  .orElse(null);
          if (previousDate != null) previous = research.read(w.symbol(), at(previousDate));
        } catch (RuntimeException unavailable) {
          LOGGER.debug("自选上期对照不可用 symbol={} date={}", w.symbol(), date, unavailable);
        }
        var replayPrevious = previous;
        ResearchObservation frozen = null;
        if (journal != null && previousDate != null) {
          frozen =
              journal.previous(new ObservationQuery(w.symbol(), previousDate, date.atStartOfDay()));
          if (frozen != null)
            previous =
                Jsons.read(frozen.getEvidenceJson(), DailyObservationEvidence.class).research();
        }
        var impact =
            PositionImpactCalculator.calculate(
                new PositionImpactCalculator.Input(
                    positions.stream()
                        .filter(p -> w.symbol().equals(p.getSymbol()))
                        .findFirst()
                        .orElse(null),
                    account,
                    current,
                    knowledgeDate));
        var item =
            ResearchFocusCalculator.compare(
                    ResearchFocusInput.builder()
                        .current(current)
                        .previous(previous)
                        .previousDate(previousDate)
                        .build())
                .toBuilder()
                .positionImpact(impact)
                .comparisonBasis(frozen == null ? "CURRENT_RULE_REPLAY" : "FROZEN_DAILY")
                .previousCapturedAt(frozen == null ? null : frozen.getCreatedAt())
                .previousTitle(
                    frozen == null
                        ? null
                        : ResearchFocusCalculator.compare(
                                ResearchFocusInput.builder().current(previous).build())
                            .title())
                .build();
        if (frozen != null
            && replayPrevious != null
            && !java.util.Set.of("METHOD_CHANGED", "SOURCE_CHANGED", "DATA_GAP")
                .contains(item.changeState())
            && java.util.Objects.equals(previous.version(), replayPrevious.version())
            && java.util.Objects.equals(previous.source(), replayPrevious.source())) {
          if (previous.dataHash() != null
              && replayPrevious.dataHash() != null
              && !previous.dataHash().equals(replayPrevious.dataHash()))
            item =
                item.toBuilder()
                    .changeState("DATA_REVISED")
                    .newlyMatched(false)
                    .changeLabel("上期原始价格、因子或财报版本已经修订；本期差异同时受修订影响，请先区分修订与新事实")
                    .build();
          else if (previous.assumptionHash() != null
              && replayPrevious.assumptionHash() != null
              && !previous.assumptionHash().equals(replayPrevious.assumptionHash()))
            item =
                item.toBuilder()
                    .changeState("ASSUMPTION_CHANGED")
                    .newlyMatched(false)
                    .changeLabel("上期私人估值假设已改变，不能把个人输入变化当成公司新事实")
                    .build();
        }
        items.add(withOriginalRisk(item, original, issues));
      } catch (RuntimeException unavailable) {
        var reference = java.util.UUID.randomUUID().toString().substring(0, 8);
        LOGGER.warn(
            "自选研究读取失败 reference={} symbol={} date={}", reference, w.symbol(), date, unavailable);
        var item =
            ResearchFocusView.Item.builder()
                .symbol(w.symbol())
                .name(w.name())
                .category("DATA_GAP")
                .title("研究暂不可用")
                .explanation("本标的读取失败，其他标的仍可查看；请进入个股核对资料，维护编号" + reference + "。")
                .newlyMatched(false)
                .positionImpact(missingImpact(w.symbol(), held))
                .changeState("DATA_GAP")
                .changeLabel("尚未取得可用研究，不推断风险消失")
                .priorities(List.of())
                .build();
        items.add(withOriginalRisk(item, original, issues));
      }
    }
    items.sort(
        Comparator.comparingInt(
                (ResearchFocusView.Item i) ->
                    "DATA_GAP".equals(i.category())
                            && held.stream().anyMatch(p -> i.symbol().equals(p.getSymbol()))
                        ? Math.min(25, ResearchFocusCalculator.priority(i))
                        : ResearchFocusCalculator.priority(i))
            .thenComparingInt(
                i ->
                    i.positionImpact() != null && "HELD".equals(i.positionImpact().state()) ? 0 : 1)
            .thenComparing(
                Comparator.comparingDouble(
                        (ResearchFocusView.Item i) ->
                            i.positionImpact() != null && i.positionImpact().weight() != null
                                ? i.positionImpact().weight()
                                : -1)
                    .reversed()));
    var portfolioImpact =
        positionRiskEnabled
            ? PortfolioImpactCalculator.calculate(
                new PortfolioImpactCalculator.Input(date, positions, account, items, knowledgeDate))
            : null;
    return new ResearchFocusView(
        date,
        items.stream()
            .skip((long) (query.getPage() - 1) * query.getSize())
            .limit(query.getSize())
            .toList(),
        scope.size(),
        query.getPage(),
        query.getSize(),
        portfolioImpact,
        List.copyOf(issues),
        knowledgeDate,
        positionRiskEnabled);
  }

  private ResearchFocusView.Item withOriginalRisk(
      ResearchFocusView.Item item, PlanSummary original, List<String> issues) {
    if (original == null) return item;
    try {
      if (planLicenses != null) planLicenses.requireEvidence(original, "DERIVED_REPORT");
    } catch (RuntimeException blocked) {
      issues.add(item.symbol() + "原方案来源许可待核验，未展示其数值；不能视作风险消失");
      return item;
    }
    var priority =
        ResearchPriority.builder()
            .code("ORIGINAL_PLAN_RISK")
            .level("FIRST")
            .title("原保存方案已触及风险参考，优先复核")
            .explanation(original.latestReviewExplanation())
            .horizon(original.horizon())
            .actionCode(original.actionCode())
            .facts(
                List.of(
                    "复核日" + original.latestReviewDate() + "：" + original.latestReviewExplanation()))
            .nextChecks(List.of("到研究计划核对原始条件、触及记录和当前实际持仓。原风险参考以保存时的方案为准。"))
            .build();
    var priorities = new ArrayList<ResearchPriority>();
    priorities.add(priority);
    if (item.priorities() != null) priorities.addAll(item.priorities());
    var sources = new java.util.TreeSet<String>();
    if (item.inputSources() != null) sources.addAll(item.inputSources());
    sources.addAll(SourceEvidence.inspect(original).sources());
    var impact = item.positionImpact();
    if (impact != null && java.util.Set.of("HELD", "ESTIMATED", "STALE").contains(impact.state()))
      impact =
          new PositionImpact(
              impact.state(),
              impact.recordedAt(),
              impact.marketValue(),
              impact.weight(),
              impact.unrealizedPnl(),
              null,
              impact.checks(),
              "BREACHED",
              impact.liquidityDays());
    return item.toBuilder()
        .category("RISK")
        .title(priority.title())
        .explanation(priority.explanation())
        .horizon(original.horizon())
        .newlyMatched(false)
        .changeState("ORIGINAL_PLAN_ALERT")
        .changeLabel(original.acknowledged() ? "已阅读原风险，触及记录仍保留" : "原方案风险仍待处理，请优先核对原触及记录")
        .originalPlanId(original.id())
        .priorities(List.copyOf(priorities))
        .positionImpact(impact)
        .inputSources(java.util.Set.copyOf(sources))
        .build();
  }

  private PositionImpact missingImpact(String symbol, List<PositionDO> held) {
    var position = held.stream().filter(p -> symbol.equals(p.getSymbol())).findFirst().orElse(null);
    return position == null
        ? null
        : new PositionImpact(
            "STALE",
            position.getAsOfDate(),
            null,
            null,
            null,
            null,
            List.of("已登记正持仓，但本期行情不可核验；个人影响未知，不能据此判断风险降低"));
  }

  private HoldingResearchQuery at(LocalDate date) {
    var query = new HoldingResearchQuery();
    query.setAsOf(date);
    return query;
  }
}

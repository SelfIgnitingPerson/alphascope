package com.alphascope.market.service.impl;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.ExecutionProgress;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.calculator.FullMarketAssembler;
import com.alphascope.market.calculator.HistoricalFullMarketAssembler;
import com.alphascope.market.calculator.MarketBreadthCalculator;
import com.alphascope.market.calculator.MarketLeaderCalculator;
import com.alphascope.market.calculator.MarketObservationCalculator;
import com.alphascope.market.calculator.UniverseCoverageCalculator;
import com.alphascope.market.dto.LeaderScoreEvidence;
import com.alphascope.market.dto.MarketObservationInput;
import com.alphascope.market.dto.ResearchFactorRequest;
import com.alphascope.market.dto.ResearchMarketSnapshot;
import com.alphascope.market.dto.ResearchSnapshotQuery;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.entity.MarketBreadthSnapshot;
import com.alphascope.market.entity.MarketFactorSnapshot;
import com.alphascope.market.entity.MarketObservationSnapshot;
import com.alphascope.market.mapper.MarketBreadthMapper;
import com.alphascope.market.mapper.MarketObservationMapper;
import com.alphascope.market.provider.MarketCapability;
import com.alphascope.market.provider.research.ResearchDataRouter;
import com.alphascope.market.service.FullMarketService;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.service.MarketUniverseService;
import com.alphascope.market.vo.MarketObservation;
import com.alphascope.operations.policy.SourceEvidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * 先保存三所同日原文，后从同源已归档历史与因子计算；不拿当前名单倒填过去。
 *
 * @author chenxuefeng
 */
@Service
public class FullMarketServiceImpl implements FullMarketService {
  private final MarketUniverseService universe;
  private final MarketService market;
  private final ResearchDataRouter router;
  private final MarketBreadthMapper breadth;
  private final MarketObservationMapper observations;
  private final Clock clock;
  private final ObjectMapper json;

  @org.springframework.beans.factory.annotation.Autowired
  private com.alphascope.common.mapper.ResearchCollectionMapper collections;

  public FullMarketServiceImpl(
      MarketUniverseService universe,
      MarketService market,
      ResearchDataRouter router,
      MarketBreadthMapper breadth,
      MarketObservationMapper observations,
      Clock clock,
      ObjectMapper json) {
    this.universe = universe;
    this.market = market;
    this.router = router;
    this.breadth = breadth;
    this.observations = observations;
    this.clock = clock;
    this.json = json;
  }

  @Override
  public void sync(LocalDate date) {
    if (market.synthetic()) throw new IllegalArgumentException("演示模式不能生成真实三所证据");
    market.syncCalendar(date.minusDays(140), date);
    if (!market.isOpen(date)) throw new IllegalArgumentException("目标日期不是已核验交易日");
    var source = archivedSource(date);
    if (HistoricalFullMarketAssembler.imported(source)) {
      rebuild(date, source);
      return;
    }
    if (!"AVAILABLE".equals(universe.snapshot(date).status())) universe.sync(date);
    var roster = universe.snapshot(date);
    var saved =
        breadth.fullForDate(ResearchSnapshotQuery.builder().date(date).source(source).build());
    if (!complete(saved)) {
      var snapshot = archivedSnapshot(date);
      saved = null;
      if (snapshot != null) {
        try {
          saved = verifiedSnapshot(snapshot, roster);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
          org.slf4j.LoggerFactory.getLogger(getClass())
              .info("已存原文未通过全 A 验证 date={} code={}", date, invalid.getMessage());
        }
      }
      if (saved == null)
        saved = router.fullSnapshot(date, candidate -> verifiedSnapshot(candidate, roster));
      breadth.save(saved);
    }
    rebuild(date, saved.getSource());
  }

  private MarketBreadthSnapshot verifiedSnapshot(
      ResearchMarketSnapshot snapshot, com.alphascope.market.vo.MarketUniverseView roster) {
    String raw = FullMarketAssembler.assemble(snapshot, roster);
    var metrics =
        MarketBreadthCalculator.calculateScope(
            snapshot.date(), snapshot.universe(), snapshot.daily(), "FULL_A");
    if (!"COMPLETE".equals(metrics.getStatus()))
      throw new IllegalStateException("FULL_A_DAILY_COVERAGE_INCOMPLETE");
    metrics.setSource(snapshot.source());
    return MarketBreadthSnapshot.builder()
        .tradeDate(snapshot.date())
        .source(snapshot.source())
        .universeScope("FULL_A")
        .contentHash(Jsons.hash(raw))
        .metricsJson(Jsons.write(metrics))
        .rawJson(raw)
        .fetchedAt(now())
        .build();
  }

  private boolean complete(MarketBreadthSnapshot row) {
    return row != null
        && "COMPLETE"
            .equals(Jsons.read(row.getMetricsJson(), JsonNode.class).path("status").asText());
  }

  /** {@inheritDoc} */
  @Override
  public void rebuildImported(LocalDate date) {
    String source = archivedSource(date);
    if (market.synthetic() || !HistoricalFullMarketAssembler.imported(source))
      throw new IllegalStateException("所选日期缺少可复算的真实历史导入档案");
    rebuild(date, source);
  }

  private String archivedSource(LocalDate date) {
    for (String source :
        java.util.List.of(
            HistoricalFullMarketAssembler.SOURCE,
            HistoricalFullMarketAssembler.PUBLIC_HISTORY_SOURCE)) {
      if (complete(
          breadth.fullForDate(ResearchSnapshotQuery.builder().date(date).source(source).build())))
        return source;
    }
    if (!complete(
        breadth.fullForDate(
            ResearchSnapshotQuery.builder().date(date).source("EASTMONEY").build()))) {
      for (String source : java.util.List.of("SINA", "TUSHARE")) {
        if (complete(
            breadth.fullForDate(ResearchSnapshotQuery.builder().date(date).source(source).build())))
          return source;
      }
    }
    return "EASTMONEY";
  }

  /** 复用已经保存的完整原文；只转换本日证据，不使用当前目录倒填历史。 */
  private com.alphascope.market.dto.ResearchMarketSnapshot archivedSnapshot(LocalDate date) {
    var archived =
        breadth.forSource(
            ResearchSnapshotQuery.builder().date(date).source("EASTMONEY").loadRaw(true).build());
    if (archived == null || archived.getRawJson() == null) return null;
    var raw = Jsons.read(archived.getRawJson(), JsonNode.class);
    if (!raw.path("universe").isArray()
        || !raw.path("daily").isArray()
        || !raw.path("upstreamJson").isTextual()) return null;
    return com.alphascope.market.dto.ResearchMarketSnapshot.builder()
        .date(date)
        .source("EASTMONEY")
        .universe(
            json.convertValue(
                raw.path("universe"),
                new com.fasterxml.jackson.core.type.TypeReference<
                    java.util.List<Map<String, String>>>() {}))
        .daily(
            json.convertValue(
                raw.path("daily"),
                new com.fasterxml.jackson.core.type.TypeReference<
                    java.util.List<Map<String, String>>>() {}))
        .upstreamJson(raw.path("upstreamJson").asText())
        .limitations(java.util.List.of())
        .build();
  }

  /** {@inheritDoc} */
  @Override
  public void refresh(LocalDate date) {
    if (market.synthetic()) return;
    try {
      sync(date);
      var value = observation(date);
      collections.collectionSave(
          com.alphascope.common.entity.ResearchCollection.builder()
              .dataKind("FULL_MARKET")
              .scope("FULL_A")
              .businessDate(date)
              .status(value == null ? "UNAVAILABLE" : value.status())
              .evidenceJson(
                  Jsons.write(
                      Map.of("source", archivedSource(date), "reason", "已核对三所同日目录，历史和因子覆盖分别记录")))
              .build());
    } catch (com.alphascope.common.task.TaskStoppedException stopped) {
      throw stopped;
    } catch (RuntimeException failure) {
      var diagnosis = com.alphascope.common.diagnostic.FailureDiagnostics.from(failure);
      collections.collectionSave(
          com.alphascope.common.entity.ResearchCollection.builder()
              .dataKind("FULL_MARKET")
              .scope("FULL_A")
              .businessDate(date)
              .status("UNAVAILABLE")
              .evidenceJson(Jsons.write(diagnosis))
              .build());
      org.slf4j.LoggerFactory.getLogger(getClass()).warn("三所归档未完成 date={}", date, failure);
    }
  }

  /** {@inheritDoc} */
  @Override
  public com.alphascope.market.vo.MarketReadinessView readiness(LocalDate date) {
    var days =
        market
            .expectedDates(
                TradingDatesQuery.builder()
                    .symbol("000300.SH")
                    .start(date.minusDays(140))
                    .end(date)
                    .build())
            .stream()
            .sorted()
            .toList();
    days = days.subList(Math.max(0, days.size() - 60), days.size());
    var missing = new ArrayList<LocalDate>();
    String source = archivedSource(date);
    if (!HistoricalFullMarketAssembler.imported(source)) {
      String historySource = breadth.latestImportedSource(date);
      if (historySource != null) source = historySource;
    }
    int archived = 0;
    for (var day : days) {
      var row =
          breadth.fullForDate(ResearchSnapshotQuery.builder().date(day).source(source).build());
      if (row != null
          && "COMPLETE"
              .equals(Jsons.read(row.getMetricsJson(), JsonNode.class).path("status").asText()))
        archived++;
      else missing.add(day);
    }
    boolean factors =
        HistoricalFullMarketAssembler.imported(source)
            || router.supports(source, MarketCapability.ADJUSTMENT_FACTORS);
    var steps = new ArrayList<String>();
    if (days.size() < 60) steps.add("先补齐交易日历；至少需要60个有效交易日核对长期广度");
    if (!missing.isEmpty())
      steps.add("缺少 " + missing.size() + " 个同源交易日。导入授权历史时，需同时提供逐日三所名单、交易状态、日线及复权因子。");
    if (missing.contains(date) && HistoricalFullMarketAssembler.imported(source))
      steps.add("所选日期尚缺可连入这段历史的同口径截面，单日成交额和涨跌家数独立核验。");
    if (!factors) steps.add("东方财富收盘截面缺少同源复权因子，均线广度需等同源历史导入后发布。");
    if (HistoricalFullMarketAssembler.SOURCE.equals(source))
      steps.add("当前采用 BigQuant 历史重建。后续日期通过同一来源补充档案。");
    else if (HistoricalFullMarketAssembler.PUBLIC_HISTORY_SOURCE.equals(source))
      steps.add("沪深使用证券宝，北交所使用新浪；每只股票按同一来源复权，清单按实际业务日独立核对。");
    else steps.add("收盘任务继续归档当日三所截面。具体失败原因见任务中心。");
    var observed = observation(date);
    var details = new ArrayList<com.alphascope.market.vo.MarketReadinessView.Metric>();
    int trading = observed == null || observed.breadth() == null ? 0 : observed.breadth().trading();
    var observedBreadth = observed == null ? null : observed.breadth();
    int valid20 = observedBreadth == null ? 0 : observedBreadth.valid20();
    int valid60 = observedBreadth == null ? 0 : observedBreadth.valid60();
    boolean directionsAvailable =
        observedBreadth != null && observedBreadth.advancingRatio() != null;
    boolean turnoverAvailable =
        observed != null && observed.turnover() != null && observed.turnover().amount() != null;
    details.add(
        new com.alphascope.market.vo.MarketReadinessView.Metric(
            "当日涨跌家数",
            directionsAvailable ? "AVAILABLE" : "INSUFFICIENT_DATA",
            directionsAvailable ? 1 : 0,
            1,
            directionsAvailable ? "使用已核验的当日收盘与供应商前收盘" : "需要完整三所当日截面及可核验的前收盘数据"));
    details.add(
        new com.alphascope.market.vo.MarketReadinessView.Metric(
            "当日成交额",
            turnoverAvailable ? "AVAILABLE" : "INSUFFICIENT_DATA",
            turnoverAvailable ? 1 : 0,
            1,
            turnoverAvailable ? "三所当日成交额已核验" : "需要完整三所当日行情及成交额"));
    details.add(
        new com.alphascope.market.vo.MarketReadinessView.Metric(
            "20日均线广度",
            observedBreadth != null && observedBreadth.ratio20() != null && factors
                ? "AVAILABLE"
                : "INSUFFICIENT_DATA",
            valid20,
            trading,
            "每只交易股票需要连续同源历史及因子；缺失股票不当作低于均线"));
    details.add(
        new com.alphascope.market.vo.MarketReadinessView.Metric(
            "60日均线广度",
            observedBreadth != null && observedBreadth.ratio60() != null && factors
                ? "AVAILABLE"
                : "INSUFFICIENT_DATA",
            valid60,
            trading,
            "60日归档数量与每只股票历史完整性须分别通过"));
    details.add(
        new com.alphascope.market.vo.MarketReadinessView.Metric(
            "全A历史底座",
            archived == 60 && factors ? "AVAILABLE" : "INSUFFICIENT_DATA",
            archived,
            60,
            "缺同日历史目录或同源因子时，相关总体结论保持未知"));
    return new com.alphascope.market.vo.MarketReadinessView(
        date,
        source,
        archived,
        60,
        java.util.List.copyOf(missing),
        factors,
        collections.collection("FULL_MARKET", "FULL_A", date),
        java.util.List.copyOf(steps),
        java.util.List.copyOf(details));
  }

  /** {@inheritDoc} */
  @Override
  public com.alphascope.market.vo.MarketRepairView repairStored(
      com.alphascope.market.dto.MarketRepairInput input) {
    if (input.asOf() == null
        || input.asOf().isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))))
        || input.dates() == null
        || input.dates().isEmpty()
        || input.dates().size() > 10) throw new IllegalArgumentException("历史核验需明确非未来日期，每次最多10日");
    var allowed =
        market.expectedDates(
            TradingDatesQuery.builder()
                .symbol("000300.SH")
                .start(input.asOf().minusDays(140))
                .end(input.asOf())
                .build());
    int restored = 0, reused = 0;
    var issues = new ArrayList<String>();
    for (var day : new TreeSet<>(input.dates())) {
      if (!allowed.contains(day)) throw new IllegalArgumentException("只能核验最近140天内已确认的交易日");
      var existing =
          breadth.fullForDate(
              ResearchSnapshotQuery.builder().date(day).source("EASTMONEY").build());
      if (existing != null
          && "COMPLETE"
              .equals(
                  Jsons.read(existing.getMetricsJson(), JsonNode.class).path("status").asText())) {
        reused++;
        continue;
      }
      var original =
          breadth.forSource(ResearchSnapshotQuery.builder().date(day).source("EASTMONEY").build());
      if (original == null
          || original.getFetchedAt() == null
          || !day.equals(original.getFetchedAt().toLocalDate())) {
        issues.add(day + "：缺本交易日实际采集的分页行情原文，需取得有日期证明的真实历史资料");
        continue;
      }
      var roster = universe.snapshot(day);
      if (roster == null
          || !"AVAILABLE".equals(roster.status())
          || roster.observedAt() == null
          || !day.equals(roster.observedAt().toLocalDate())) {
        issues.add(day + "：缺当日北交所完整目录及分页证明，不能套用当前名单");
        continue;
      }
      try {
        var snapshot = archivedSnapshot(day);
        if (snapshot == null) {
          issues.add(day + "：原文结构不足以核验完整三所截面");
          continue;
        }
        String raw = FullMarketAssembler.assemble(snapshot, roster);
        var metrics =
            MarketBreadthCalculator.calculateScope(
                day, snapshot.universe(), snapshot.daily(), "FULL_A");
        metrics.setSource(snapshot.source());
        if (!"COMPLETE".equals(metrics.getStatus())) {
          issues.add(day + "：原价或停牌状态缺项，仍不符合完整截面要求");
          continue;
        }
        breadth.save(
            MarketBreadthSnapshot.builder()
                .tradeDate(day)
                .source(snapshot.source())
                .universeScope("FULL_A")
                .contentHash(Jsons.hash(raw))
                .metricsJson(Jsons.write(metrics))
                .rawJson(raw)
                .fetchedAt(original.getFetchedAt())
                .build());
        restored++;
      } catch (RuntimeException invalid) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("本机历史核验失败 date={}", day, invalid);
        issues.add(day + "：原文、目录或来源校验未通过，原资料未修改");
      }
    }
    if (restored > 0 && "EASTMONEY".equals(archivedSource(input.asOf())))
      rebuild(input.asOf(), "EASTMONEY");
    return new com.alphascope.market.vo.MarketRepairView(
        input.asOf(), restored, reused, java.util.List.copyOf(issues));
  }

  private void rebuild(LocalDate date, String source) {
    var open =
        new TreeSet<>(
            market.expectedDates(
                TradingDatesQuery.builder()
                    .symbol("000300.SH")
                    .start(date.minusDays(140))
                    .end(date)
                    .build()));
    var all = new ArrayList<>(open);
    var dates = all.subList(Math.max(0, all.size() - 82), all.size());
    Map<LocalDate, JsonNode> snapshots = new TreeMap<>();
    var refs = new ArrayList<Map<String, Object>>();
    var sources = new TreeSet<String>();
    sources.add(source);
    Map<String, NavigableMap<LocalDate, Double>> factors = new TreeMap<>();
    boolean imported = HistoricalFullMarketAssembler.imported(source);
    for (var day : dates) {
      ExecutionContext.checkpoint();
      var row =
          breadth.fullForDate(
              ResearchSnapshotQuery.builder().date(day).source(source).loadRaw(true).build());
      if (row != null) {
        var raw = Jsons.read(row.getRawJson(), JsonNode.class);
        if (!UniverseCoverageCalculator.calculate(row).coverage().rosterVerified()) continue;
        sources.addAll(SourceEvidence.inspect(raw.path("rosterEvidence")).sources());
        raw.path("inputSources").forEach(value -> sources.add(value.asText()));
        if (imported) readImportedFactors(day, raw, factors);
        var metrics =
            (com.fasterxml.jackson.databind.node.ObjectNode)
                Jsons.read(row.getMetricsJson(), JsonNode.class);
        if (imported && day.equals(date) && !metrics.path("advancers").isNumber()) {
          var direction = dailyDirection(row, raw);
          if (direction != null) {
            metrics
                .put("advancers", direction.up())
                .put("decliners", direction.down())
                .put("unchanged", direction.unchanged());
            var reference = direction.reference();
            refs.add(
                Map.of(
                    "id",
                    reference.getId(),
                    "hash",
                    reference.getContentHash(),
                    "date",
                    date,
                    "source",
                    reference.getSource(),
                    "fact",
                    "DAILY_ADVANCERS"));
            sources.add(reference.getSource());
          }
        }
        // 完整上游分页原文由不可变主键和指纹定位；窗口计算只留标准化日线，避免82份原文同时驻留。
        snapshots.put(
            day,
            json.valueToTree(
                Map.of(
                    "raw",
                    Map.of("universe", raw.path("universe"), "daily", raw.path("daily")),
                    "metrics",
                    metrics)));
        refs.add(
            Map.of("id", row.getId(), "hash", row.getContentHash(), "date", day, "source", source));
      }
    }
    if (!snapshots.containsKey(date))
      throw new IllegalStateException("FULL_A_DAILY_COVERAGE_INCOMPLETE");
    if (HistoricalFullMarketAssembler.PUBLIC_HISTORY_SOURCE.equals(source)) {
      int first = dates.size() - 1;
      while (first > 0 && snapshots.containsKey(dates.get(first - 1))) first--;
      dates = dates.subList(first, dates.size());
    }
    if (!imported
        && dates.size() >= 20
        && dates.subList(dates.size() - 20, dates.size()).stream().allMatch(snapshots::containsKey))
      loadFactorWindows(
          ResearchFactorRequest.builder().source(source).start(dates.getFirst()).end(date).build(),
          snapshots.get(date),
          factors);
    var benchmark =
        json.valueToTree(
            market.bars(
                "000300.SH",
                DateRangeRequest.builder().start(date.minusDays(140)).end(date).build()));
    sources.addAll(SourceEvidence.inspect((JsonNode) benchmark).sources());
    var input =
        new MarketObservationInput(
            date,
            dates,
            snapshots,
            factors,
            benchmark,
            new MarketObservationCalculator.Parameters(.98, 1.1, .9, .6, .4),
            "FULL_A");
    saveObservation(input, new FullMarketEvidence(source, refs, sources));
  }

  private DailyDirection dailyDirection(MarketBreadthSnapshot archived, JsonNode imported) {
    var comparison = imported.path("rosterEvidence").path("comparison");
    long id = comparison.path("referenceSnapshotId").asLong(0);
    if (id == 0) return null;
    var source =
        breadth.fullForDate(
            ResearchSnapshotQuery.builder()
                .date(archived.getTradeDate())
                .source("EASTMONEY")
                .loadRaw(true)
                .build());
    if (!complete(source) || source.getId() != id) return null;
    var raw = Jsons.read(source.getRawJson(), JsonNode.class);
    if (!comparison.path("sha256").asText().equals(source.getContentHash())
        || !raw.path("universeProof")
            .path("rosterSha256")
            .asText()
            .equals(imported.path("universeProof").path("rosterSha256").asText())
        || !UniverseCoverageCalculator.calculate(source).coverage().rosterVerified()) return null;
    var daily = Jsons.read(source.getMetricsJson(), JsonNode.class);
    int trading = Jsons.read(archived.getMetricsJson(), JsonNode.class).path("observed").asInt(-1);
    int up = daily.path("advancers").asInt(-1),
        down = daily.path("decliners").asInt(-1),
        unchanged = daily.path("unchanged").asInt(-1);
    return trading > 0 && up >= 0 && down >= 0 && unchanged >= 0 && up + down + unchanged == trading
        ? new DailyDirection(up, down, unchanged, source)
        : null;
  }

  /**
   * 已引用并核验的原版当日涨跌数据，仅补独立分项。
   *
   * @author chenxuefeng
   */
  private record DailyDirection(int up, int down, int unchanged, MarketBreadthSnapshot reference) {}

  private void readImportedFactors(
      LocalDate day, JsonNode raw, Map<String, NavigableMap<LocalDate, Double>> factors) {
    for (var quote : raw.path("daily")) {
      if (!"1".equals(quote.path("tradestatus").asText())) continue;
      if (quote.path("factorUnavailable").asBoolean()) continue;
      double factor = Double.parseDouble(quote.path("factor").asText());
      if (!Double.isFinite(factor) || factor <= 0) throw new IllegalStateException("历史档案复权因子无效");
      var series = factors.computeIfAbsent(quote.path("code").asText(), ignored -> new TreeMap<>());
      if (series.isEmpty() || Double.compare(series.lastEntry().getValue(), factor) != 0)
        series.put(day, factor);
    }
  }

  private void loadFactorWindows(
      ResearchFactorRequest window,
      JsonNode current,
      Map<String, NavigableMap<LocalDate, Double>> factors) {
    var date = window.end();
    String source = window.source();
    int completed = 0;
    var cachedFactors = new HashMap<String, MarketFactorSnapshot>();
    for (var row : observations.factorsForWindow(window))
      cachedFactors.putIfAbsent(row.getSymbol(), row);
    for (var member : current.path("raw").path("universe")) {
      ExecutionContext.checkpoint();
      if (!"1".equals(member.path("tradeStatus").asText())) continue;
      String code = member.path("code").asText();
      var request =
          ResearchFactorRequest.builder()
              .symbol(code)
              .source(source)
              .start(window.start())
              .end(date)
              .build();
      var factor = cachedFactors.get(code);
      // 新请求仅在该价格来源真实声明因子能力时发出；不拼接另一来源，也不填1。
      if (factor == null && router.supports(source, MarketCapability.ADJUSTMENT_FACTORS)) {
        var fetched = router.factors(request);
        if (fetched.coverageFrom().isAfter(request.start())
            || fetched.verifiedThrough().isBefore(request.end()))
          throw new IllegalStateException("FULL_A_FACTOR_WINDOW_INCOMPLETE");
        String raw =
            Jsons.write(
                Map.of(
                    "source",
                    source,
                    "coverageFrom",
                    fetched.coverageFrom(),
                    "events",
                    Jsons.read(fetched.eventsJson(), JsonNode.class),
                    "upstreamJson",
                    fetched.upstreamJson()));
        factor =
            MarketFactorSnapshot.builder()
                .symbol(code)
                .source(source)
                .verifiedThrough(fetched.verifiedThrough())
                .contentHash(Jsons.hash(raw))
                .rawJson(raw)
                .fetchedAt(now())
                .build();
        observations.factorSave(factor);
        factor = observations.factor(request);
      }
      if (factor != null) {
        var raw = Jsons.read(factor.getRawJson(), JsonNode.class);
        factors.put(
            code,
            MarketObservationCalculator.factors(raw.isArray() ? raw : raw.path("events"), date));
      }
      ExecutionContext.progress(
          new ExecutionProgress(
              ++completed,
              current.path("raw").path("universe").size(),
              "核对三所同源历史与因子",
              clock.instant()));
    }
  }

  private void saveObservation(MarketObservationInput input, FullMarketEvidence proof) {
    var date = input.date();
    var result = MarketObservationCalculator.calculateScope(input);
    if (HistoricalFullMarketAssembler.imported(proof.source())) {
      var notes = new ArrayList<>(result.limitations());
      notes.addFirst(
          HistoricalFullMarketAssembler.PUBLIC_HISTORY_SOURCE.equals(proof.source())
              ? "历史截面经独立核对，沪深采用证券宝，北交所采用新浪。单股价格和复权因子由同一来源提供。"
              : "供应商历史重建，保存实际导出及入库时点。单股价格和复权因子由同一来源提供。");
      result =
          new MarketObservation(
              result.version(),
              result.businessDate(),
              result.scope(),
              result.status(),
              result.trend(),
              result.turnover(),
              result.breadth(),
              result.parameters(),
              notes);
    }
    var leaders = MarketLeaderCalculator.calculateScope(input);
    String evidence =
        Jsons.write(
            Map.of(
                "source",
                proof.source(),
                "sourceRefs",
                proof.snapshots(),
                "calendar",
                input.calendar(),
                "factorCount",
                input.factors().size(),
                "leaderEvidence",
                leaders,
                "inputSources",
                proof.inputSources(),
                "minimumCoverage",
                .98));
    String analysis = Jsons.write(result);
    observations.save(
        MarketObservationSnapshot.builder()
            .tradeDate(date)
            .universeScope("FULL_A")
            .modelVersion("FULL_A_OBSERVATION_V1")
            .contentHash(Jsons.hash(analysis + evidence))
            .analysisJson(analysis)
            .evidenceJson(evidence)
            .fetchedAt(now())
            .build());
  }

  /**
   * 观察采用的实际来源与原截面引用。
   *
   * @author chenxuefeng
   */
  private record FullMarketEvidence(
      String source,
      java.util.List<Map<String, Object>> snapshots,
      java.util.Set<String> inputSources) {}

  @Override
  public MarketObservation observation(LocalDate date) {
    var row = observations.fullForDate(date);
    if (row == null) return null;
    var result = Jsons.read(row.getAnalysisJson(), MarketObservation.class);
    var sources =
        new java.util.TreeSet<>(
            SourceEvidence.inspect(Jsons.read(row.getEvidenceJson(), JsonNode.class)).sources());
    sources.addAll(
        SourceEvidence.inspect(Jsons.read(row.getAnalysisJson(), JsonNode.class)).sources());
    var source = Jsons.read(row.getEvidenceJson(), JsonNode.class).path("source").asText();
    if (HistoricalFullMarketAssembler.imported(source) && !source.equals(archivedSource(date)))
      return null;
    return new MarketObservation(
        result.version(),
        result.businessDate(),
        result.scope(),
        result.status(),
        result.trend(),
        result.turnover(),
        result.breadth(),
        result.parameters(),
        result.limitations(),
        sources);
  }

  @Override
  public LeaderScoreEvidence leaders(LocalDate date) {
    var row = observations.fullForDate(date);
    if (row == null) return null;
    return Jsons.read(row.getEvidenceJson(), JsonNode.class).has("leaderEvidence")
        ? Jsons.read(
            Jsons.read(row.getEvidenceJson(), JsonNode.class).get("leaderEvidence").toString(),
            LeaderScoreEvidence.class)
        : null;
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai"));
  }
}

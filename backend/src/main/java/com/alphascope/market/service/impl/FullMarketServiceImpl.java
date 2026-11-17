package com.alphascope.market.service.impl;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.ExecutionProgress;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.calculator.FullMarketAssembler;
import com.alphascope.market.calculator.MarketBreadthCalculator;
import com.alphascope.market.calculator.MarketLeaderCalculator;
import com.alphascope.market.calculator.MarketObservationCalculator;
import com.alphascope.market.calculator.UniverseCoverageCalculator;
import com.alphascope.market.dto.LeaderScoreEvidence;
import com.alphascope.market.dto.MarketObservationInput;
import com.alphascope.market.dto.ResearchFactorRequest;
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
    if (!"AVAILABLE".equals(universe.snapshot(date).status())) universe.sync(date);
    var saved =
        breadth.fullForDate(
            ResearchSnapshotQuery.builder().date(date).source("EASTMONEY").loadRaw(true).build());
    if (saved == null) {
      var snapshot = router.snapshot(date, "EASTMONEY");
      String raw = FullMarketAssembler.assemble(snapshot, universe.snapshot(date));
      var metrics =
          MarketBreadthCalculator.calculateScope(
              date, snapshot.universe(), snapshot.daily(), "FULL_A");
      metrics.setSource(snapshot.source());
      saved =
          MarketBreadthSnapshot.builder()
              .tradeDate(date)
              .source(snapshot.source())
              .universeScope("FULL_A")
              .contentHash(Jsons.hash(raw))
              .metricsJson(Jsons.write(metrics))
              .rawJson(raw)
              .fetchedAt(now())
              .build();
      breadth.save(saved);
    }
    rebuild(date, saved.getSource());
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
    for (var day : dates) {
      ExecutionContext.checkpoint();
      var row =
          breadth.fullForDate(
              ResearchSnapshotQuery.builder().date(day).source(source).loadRaw(true).build());
      if (row != null) {
        var raw = Jsons.read(row.getRawJson(), JsonNode.class);
        if (!UniverseCoverageCalculator.calculate(row).coverage().rosterVerified()) continue;
        snapshots.put(
            day,
            json.valueToTree(
                Map.of("raw", raw, "metrics", Jsons.read(row.getMetricsJson(), JsonNode.class))));
        refs.add(
            Map.of("id", row.getId(), "hash", row.getContentHash(), "date", day, "source", source));
      }
    }
    if (!snapshots.containsKey(date))
      throw new IllegalStateException("FULL_A_DAILY_COVERAGE_INCOMPLETE");
    Map<String, NavigableMap<LocalDate, Double>> factors = new TreeMap<>();
    int completed = 0;
    var cachedFactors = new HashMap<String, MarketFactorSnapshot>();
    for (var row :
        observations.factorsForWindow(
            ResearchFactorRequest.builder()
                .source(source)
                .start(dates.getFirst())
                .end(date)
                .build())) cachedFactors.putIfAbsent(row.getSymbol(), row);
    for (var member : snapshots.get(date).path("raw").path("universe")) {
      ExecutionContext.checkpoint();
      if (!"1".equals(member.path("tradeStatus").asText())) continue;
      String code = member.path("code").asText();
      var request =
          ResearchFactorRequest.builder()
              .symbol(code)
              .source(source)
              .start(dates.getFirst())
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
              snapshots.get(date).path("raw").path("universe").size(),
              "核对三所同源历史与因子",
              clock.instant()));
    }
    var benchmark =
        json.valueToTree(
            market.bars(
                "000300.SH",
                DateRangeRequest.builder().start(date.minusDays(140)).end(date).build()));
    var input =
        new MarketObservationInput(
            date,
            dates,
            snapshots,
            factors,
            benchmark,
            new MarketObservationCalculator.Parameters(.98, 1.1, .9, .6, .4),
            "FULL_A");
    var result = MarketObservationCalculator.calculateScope(input);
    var leaders = MarketLeaderCalculator.calculateScope(input);
    String evidence =
        Jsons.write(
            Map.of(
                "source",
                source,
                "sourceRefs",
                refs,
                "calendar",
                dates,
                "factorCount",
                factors.size(),
                "leaderEvidence",
                leaders,
                "inputSources",
                SourceEvidence.inspect(benchmark).sources(),
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

package com.alphascope.market.service.impl;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.entity.ResearchCollection;
import com.alphascope.common.mapper.ResearchCollectionMapper;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.ExecutionProgress;
import com.alphascope.common.task.TaskStoppedException;
import com.alphascope.common.util.Jsons;
import com.alphascope.common.vo.CollectionView;
import com.alphascope.market.calculator.MarketBreadthCalculator;
import com.alphascope.market.calculator.MarketLeaderCalculator;
import com.alphascope.market.calculator.MarketObservationCalculator;
import com.alphascope.market.dto.LeaderScoreEvidence;
import com.alphascope.market.dto.MarketSnapshotReference;
import com.alphascope.market.dto.ResearchFactorRequest;
import com.alphascope.market.dto.ResearchSnapshotQuery;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.entity.MarketFactorSnapshot;
import com.alphascope.market.entity.MarketObservationSnapshot;
import com.alphascope.market.mapper.MarketBreadthMapper;
import com.alphascope.market.mapper.MarketObservationMapper;
import com.alphascope.market.provider.MarketCapability;
import com.alphascope.market.provider.baostock.BaoStockAccessGuard;
import com.alphascope.market.provider.research.ResearchDataRouter;
import com.alphascope.market.service.MarketBreadthService;
import com.alphascope.market.service.MarketObservationService;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.vo.MarketObservation;
import com.alphascope.market.vo.MarketObservationView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * MarketObservationService 的业务实现。
 *
 * <p>全市场批量截面与独立因子证据分开持久化；网络请求不占数据库事务，采集中断后可续用缓存。
 *
 * @author chenxuefeng
 */
@Service
public class MarketObservationServiceImpl implements MarketObservationService {
  private final MarketObservationMapper mapper;
  private final MarketBreadthMapper breadthMapper;
  private final MarketBreadthService breadth;
  private final MarketService market;
  private final ResearchCollectionMapper collections;
  private final ObjectMapper json;
  private final MarketObservationCalculator.Parameters parameters;
  private final ResearchDataRouter router;

  public MarketObservationServiceImpl(
      MarketObservationMapper mapper,
      MarketBreadthMapper breadthMapper,
      MarketBreadthService breadth,
      MarketService market,
      ResearchCollectionMapper collections,
      ObjectMapper json,
      @Value("${alphascope.observation.minimum-coverage:0.98}") double coverage,
      @Value("${alphascope.observation.turnover-expansion:1.1}") double expansion,
      @Value("${alphascope.observation.turnover-contraction:0.9}") double contraction,
      @Value("${alphascope.observation.breadth-strong:0.6}") double strong,
      @Value("${alphascope.observation.breadth-weak:0.4}") double weak,
      ResearchDataRouter router) {
    this.mapper = mapper;
    this.breadthMapper = breadthMapper;
    this.breadth = breadth;
    this.market = market;
    this.collections = collections;
    this.json = json;
    this.router = router;
    parameters =
        new MarketObservationCalculator.Parameters(coverage, expansion, contraction, strong, weak);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Double> parameters() {
    return parameters.values();
  }

  private LocalDateTime now() {
    return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
  }

  private JsonNode parse(Object value) {
    try {
      return value instanceof String s ? json.readTree(s) : json.valueToTree(value);
    } catch (IOException e) {
      throw new IllegalStateException("市场证据JSON损坏", e);
    }
  }

  private void progress(LocalDate date, String status, int completed, int total, int failures) {
    collections.collectionSave(
        ResearchCollection.builder()
            .dataKind("MARKET_OBSERVATION")
            .scope("SH_SZ_A")
            .businessDate(date)
            .status(status)
            .evidenceJson(
                Jsons.write(
                    Map.of(
                        "completedFactors",
                        completed,
                        "totalFactors",
                        total,
                        "failedFactors",
                        failures)))
            .build());
  }

  /** {@inheritDoc} */
  @Override
  public void sync(LocalDate date) {
    if (market.synthetic()) {
      return;
    }
    try {
      var open =
          new ArrayList<>(
              new TreeSet<>(
                  market.expectedDates(
                      TradingDatesQuery.builder()
                          .symbol("000300.SH")
                          .start(date.minusDays(140))
                          .end(date)
                          .source(null)
                          .build())));
      if (open.isEmpty() || !open.getLast().equals(date)) {
        throw new IllegalStateException("市场观察交易日历不足");
      }
      var dates = new ArrayList<>(open.subList(Math.max(0, open.size() - 82), open.size()));
      progress(date, "RUNNING", 0, 0, 0);
      var currentBreadth = breadth.snapshot(date);
      String source = currentBreadth == null ? null : currentBreadth.getSource();
      if (source == null || !router.sources().contains(source)) {
        throw new IllegalStateException("当前截面没有已配置的实际来源");
      }
      breadth.syncHistory(dates, source);
      Map<LocalDate, JsonNode> inputs = new TreeMap<>();
      List<MarketSnapshotReference> refs = new ArrayList<>();
      for (var d : dates) {
        var saved =
            breadthMapper.forSource(
                ResearchSnapshotQuery.builder().date(d).source(source).loadRaw(true).build());
        if (saved == null) {
          continue;
        }
        var metrics = parse(saved.getMetricsJson());
        var row = json.createObjectNode();
        row.set("metrics", metrics);
        row.set("raw", parse(saved.getRawJson()));
        inputs.put(d, row);
        refs.add(
            MarketSnapshotReference.builder()
                .type("market_breadth_snapshot")
                .id(saved.getId())
                .hash(saved.getContentHash())
                .date(d)
                .source(source)
                .build());
      }
      var current = inputs.get(date);
      if (current == null) {
        throw new IllegalStateException("缺少业务日沪深截面");
      }
      var symbols = new TreeSet<String>();
      for (var row : current.path("raw").path("daily")) {
        String code = row.path("code").asText();
        if (MarketBreadthCalculator.stock(code) && row.path("tradestatus").asText().equals("1")) {
          symbols.add(code);
        }
      }
      var codes = new ArrayList<>(symbols);
      progress(date, "RUNNING", 0, codes.size(), 0);
      FactorLoad factors =
          collectFactors(
              ResearchFactorRequest.builder()
                  .start(dates.getFirst())
                  .end(date)
                  .source(source)
                  .build(),
              codes);
      refs.addAll(factors.references());
      var analysis =
          MarketObservationCalculator.calculate(
              date,
              dates,
              inputs,
              factors.values(),
              json.valueToTree(
                  market.bars(
                      "000300.SH",
                      DateRangeRequest.builder().start(date.minusDays(140)).end(date).build())),
              parameters);
      boolean accessBlocked =
          "BAOSTOCK".equals(source)
              && BaoStockAccessGuard.standard().blocked()
              && (factors.failures() > 0 || inputs.size() < dates.size());
      if (accessBlocked) {
        var limitations = new ArrayList<>(analysis.limitations());
        limitations.addFirst("采集未完成：上游返回10001011，已停止请求。当前仅展示已有缓存，历史量能与复权广度缺项保留空值。");
        analysis =
            new MarketObservation(
                analysis.version(),
                analysis.businessDate(),
                analysis.scope(),
                analysis.status(),
                analysis.trend(),
                analysis.turnover(),
                analysis.breadth(),
                analysis.parameters(),
                limitations);
      }
      var leaders =
          MarketLeaderCalculator.calculate(
              date,
              dates,
              inputs,
              factors.values(),
              json.valueToTree(
                  market.bars(
                      "000300.SH",
                      DateRangeRequest.builder().start(date.minusDays(140)).end(date).build())));
      String evidence =
          Jsons.write(
              Map.of(
                  "leaderEvidence",
                  leaders,
                  "sourceRefs",
                  refs,
                  "calendar",
                  dates,
                  "parameters",
                  parameters.values(),
                  "benchmark",
                  market.bars(
                      "000300.SH",
                      DateRangeRequest.builder().start(date.minusDays(140)).end(date).build())));
      String result = Jsons.write(analysis);
      mapper.save(
          MarketObservationSnapshot.builder()
              .tradeDate(date)
              .contentHash(Jsons.hash(result + evidence))
              .analysisJson(result)
              .evidenceJson(evidence)
              .fetchedAt(now())
              .build());
      progress(date, analysis.status(), codes.size(), codes.size(), factors.failures());
      if (accessBlocked) {
        collections.collectionSave(
            ResearchCollection.builder()
                .dataKind("MARKET_OBSERVATION")
                .scope("SH_SZ_A")
                .businessDate(date)
                .status("ACCESS_RESTRICTED")
                .evidenceJson(
                    Jsons.write(
                        Map.of(
                            "reason",
                            "BAOSTOCK返回10001011，已持久化停止请求；仅展示已保存证据，缺失项不补造。",
                            "completedFactors",
                            codes.size(),
                            "totalFactors",
                            codes.size())))
                .build());
      }
    } catch (TaskStoppedException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("市场观察采集中断", e);
    } catch (Exception e) {
      String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      collections.collectionSave(
          ResearchCollection.builder()
              .dataKind("MARKET_OBSERVATION")
              .scope("SH_SZ_A")
              .businessDate(date)
              .status("UNAVAILABLE")
              .evidenceJson(
                  Jsons.write(
                      Map.of("reason", reason.substring(0, Math.min(300, reason.length())))))
              .build());
    }
  }

  /**
   * 顺序核验因子缓存，仅对缺失记录限速补取；访问受限时继续使用缓存并保留缺项。
   *
   * @param window 与原价一致的来源及全部截面所需覆盖范围
   * @param codes 业务截面的证券代码，按稳定顺序处理
   * @return 实际可用因子、原始快照引用及缺失计数
   * @throws InterruptedException 线程被取消；交由入口恢复中断标记并停止本次采集
   */
  private FactorLoad collectFactors(ResearchFactorRequest window, List<String> codes)
      throws InterruptedException {
    // 未声明因子能力时不对数千只股票逐项查询或发送必然失败的请求。
    if (!router.supports(window.source(), MarketCapability.ADJUSTMENT_FACTORS))
      return new FactorLoad(Map.of(), List.of(), codes.size());
    Map<String, NavigableMap<LocalDate, Double>> values = new TreeMap<>();
    List<MarketSnapshotReference> references = new ArrayList<>();
    int completed = 0;
    int failures = 0;
    for (String code : codes) {
      ExecutionContext.checkpoint();
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException("因子采集中断");
      }
      var request =
          ResearchFactorRequest.builder()
              .symbol(code)
              .start(window.start())
              .end(window.end())
              .source(window.source())
              .build();
      try {
        var cached = mapper.factor(request);
        if (cached == null) {
          var fetched = router.factors(request);
          if (!request.source().equals(fetched.source())
              || fetched.coverageFrom().isAfter(request.start())
              || fetched.verifiedThrough().isBefore(request.end())) {
            throw new IllegalStateException("同源复权覆盖范围不足");
          }
          String raw;
          if ("BAOSTOCK".equals(fetched.source())) {
            raw = fetched.eventsJson();
          } else {
            raw =
                Jsons.write(
                    Map.of(
                        "source",
                        fetched.source(),
                        "coverageFrom",
                        fetched.coverageFrom(),
                        "events",
                        parse(fetched.eventsJson()),
                        "upstreamJson",
                        fetched.upstreamJson()));
          }
          MarketObservationCalculator.factors(factorEvents(raw), window.end());
          mapper.factorSave(
              MarketFactorSnapshot.builder()
                  .symbol(code)
                  .source(fetched.source())
                  .verifiedThrough(fetched.verifiedThrough())
                  .contentHash(Jsons.hash(raw))
                  .rawJson(raw)
                  .fetchedAt(now())
                  .build());
          cached = mapper.factor(request);
        }
        if (cached == null) {
          throw new IllegalStateException("因子缓存写入失败");
        }
        values.put(
            code,
            MarketObservationCalculator.factors(factorEvents(cached.getRawJson()), window.end()));
        references.add(
            MarketSnapshotReference.builder()
                .type("market_factor_snapshot")
                .id(cached.getId())
                .symbol(code)
                .hash(cached.getContentHash())
                .source(window.source())
                .build());
      } catch (TaskStoppedException e) {
        throw e;
      } catch (RuntimeException e) {
        failures++;
      }
      completed++;
      ExecutionContext.progress(
          new ExecutionProgress(completed, codes.size(), "核对同源复权因子 " + code, Instant.now()));
      if (completed % 200 == 0) {
        progress(window.end(), "RUNNING", completed, codes.size(), failures);
      }
    }
    references.sort(Comparator.comparing(MarketSnapshotReference::symbol));
    return new FactorLoad(values, references, failures);
  }

  private JsonNode factorEvents(String raw) {
    JsonNode root = parse(raw);
    return root.isArray() ? root : root.path("events");
  }

  /**
   * 一次顺序因子采集的内部结果，不包含供应商访问能力或线程状态。
   *
   * @author chenxuefeng
   */
  private record FactorLoad(
      /** 按证券代码和交易日期索引的实际可用复权因子。 */
      Map<String, NavigableMap<LocalDate, Double>> values,
      /** 原始因子快照引用，按证券代码排序。 */
      List<MarketSnapshotReference> references,
      /** 缓存损坏或上游不可用而缺失的证券数量。 */
      int failures) {}

  /** {@inheritDoc} */
  @Override
  public MarketObservationView view(LocalDate date) {
    if (market.synthetic()) {
      return new MarketObservationView("NOT_APPLICABLE", null, null, null, CollectionView.empty());
    }
    var row = mapper.latestOnOrBefore(date);
    var state = collections.collection("MARKET_OBSERVATION", "SH_SZ_A", date);
    if (row == null) {
      return new MarketObservationView(
          state == null ? "NOT_CHECKED" : state.getStatus(),
          null,
          null,
          null,
          CollectionView.from(state));
    }
    var at = row.getFetchedAt();
    return new MarketObservationView(
        parse(row.getAnalysisJson()).path("status").asText(),
        row.getId(),
        at,
        json.convertValue(parse(row.getAnalysisJson()), MarketObservation.class),
        CollectionView.from(state));
  }

  /** {@inheritDoc} */
  @Override
  public LeaderScoreEvidence leaders(LocalDate date) {
    if (market.synthetic()) {
      return null;
    }
    var row = mapper.latestOnOrBefore(date);
    if (row == null || !date.equals(row.getTradeDate())) {
      return null;
    }
    try {
      var evidence = json.readTree(row.getEvidenceJson());
      return evidence.hasNonNull("leaderEvidence")
          ? json.treeToValue(evidence.get("leaderEvidence"), LeaderScoreEvidence.class)
          : null;
    } catch (IOException e) {
      throw new IllegalStateException("领先样本证据格式无效", e);
    }
  }
}

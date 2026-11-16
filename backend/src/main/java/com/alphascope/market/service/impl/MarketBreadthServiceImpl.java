package com.alphascope.market.service.impl;

import com.alphascope.common.entity.ResearchCollection;
import com.alphascope.common.mapper.ResearchCollectionMapper;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.ExecutionProgress;
import com.alphascope.common.task.TaskStoppedException;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.calculator.MarketBreadthCalculator;
import com.alphascope.market.dto.ResearchSnapshotQuery;
import com.alphascope.market.entity.MarketBreadthSnapshot;
import com.alphascope.market.mapper.MarketBreadthMapper;
import com.alphascope.market.provider.research.ResearchDataRouter;
import com.alphascope.market.service.MarketBreadthService;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.vo.MarketBreadthView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 顺序获取同源市场截面，执行完成与数据覆盖状态分别保存，不在业务层硬编码供应商。
 *
 * @author chenxuefeng
 */
@Service
public class MarketBreadthServiceImpl implements MarketBreadthService {
  private final MarketBreadthMapper mapper;
  private final ResearchCollectionMapper collection;
  private final MarketService market;
  private final ObjectMapper json;
  private final ResearchDataRouter router;
  private final Clock clock;

  public MarketBreadthServiceImpl(
      MarketBreadthMapper mapper,
      ResearchCollectionMapper collection,
      MarketService market,
      ObjectMapper json,
      ResearchDataRouter router,
      Clock clock) {
    this.mapper = mapper;
    this.collection = collection;
    this.market = market;
    this.json = json;
    this.router = router;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  public void sync(LocalDate date) {
    sync(date, null);
  }

  /** {@inheritDoc} */
  @Override
  public void syncHistory(List<LocalDate> dates) {
    syncHistory(dates, null);
  }

  /** {@inheritDoc} */
  @Override
  public void syncHistory(List<LocalDate> dates, String source) {
    if (market.synthetic()) {
      return;
    }
    int completed = 0;
    for (LocalDate date : dates) {
      ExecutionContext.checkpoint();
      sync(date, source);
      ExecutionContext.progress(
          new ExecutionProgress(++completed, dates.size(), "核对历史市场截面 " + date, clock.instant()));
    }
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai"));
  }

  private void sync(LocalDate date, String source) {
    if (market.synthetic()) {
      return;
    }
    ExecutionContext.checkpoint();
    if (source != null
        && !router.historicalSnapshots(source)
        && date.isBefore(now().toLocalDate())
        && saved(date, source) == null) {
      status(
          date, "NOT_ARCHIVED", Map.of("reason", "该免费源只提供当前截面；尚未归档的历史不能用今天目录倒填", "source", source));
      return;
    }
    if (date.equals(now().toLocalDate()) && now().toLocalTime().isBefore(LocalTime.of(15, 30))) {
      status(date, "NOT_READY", Map.of("reason", "收盘截面需等待15:30后采集"));
      return;
    }
    MarketBreadthSnapshot existing = saved(date, source);
    if (existing != null && "COMPLETE".equals(read(existing).getStatus())) {
      return;
    }
    var previous = collection.collection("MARKET_BREADTH", "SH_SZ_A", date);
    if (previous != null
        && previous.getUpdatedAt().plusMinutes(2).isAfter(now())
        && existing == null) {
      return;
    }
    try {
      var snapshot = router.snapshot(date, source);
      ExecutionContext.checkpoint();
      var metrics = MarketBreadthCalculator.calculate(date, snapshot.universe(), snapshot.daily());
      metrics.setSource(snapshot.source());
      if (snapshot.limitations() != null && !snapshot.limitations().isEmpty()) {
        var notes = new java.util.ArrayList<>(metrics.getLimitations());
        notes.addAll(snapshot.limitations());
        metrics.setLimitations(notes);
      }
      // 旧来源证据字段保留；新适配器的上游原文另列，不冒充原供应商原文。
      Map<String, Object> rawInput = new LinkedHashMap<>();
      rawInput.put("universe", snapshot.universe());
      rawInput.put("daily", snapshot.daily());
      if (snapshot.upstreamJson() != null) {
        rawInput.put("upstreamJson", snapshot.upstreamJson());
      }
      String raw = Jsons.write(rawInput);
      mapper.save(
          MarketBreadthSnapshot.builder()
              .tradeDate(date)
              .source(snapshot.source())
              .contentHash(Jsons.hash(raw))
              .metricsJson(Jsons.write(metrics))
              .rawJson(raw)
              .fetchedAt(now())
              .build());
      status(date, metrics.getStatus(), metrics);
    } catch (TaskStoppedException e) {
      throw e;
    } catch (RuntimeException e) {
      ExecutionContext.checkpoint();
      status(
          date,
          "UNAVAILABLE",
          Map.of(
              "reason",
              "市场截面或覆盖核验失败；保留缺项，不用自选样本代替市场",
              "failureType",
              e.getClass().getSimpleName()));
      LoggerFactory.getLogger(getClass())
          .warn(
              "market_snapshot_unavailable date={} failureType={}",
              date,
              e.getClass().getSimpleName());
    }
  }

  private MarketBreadthSnapshot saved(LocalDate date, String selected) {
    MarketBreadthSnapshot partial = null;
    for (String source : selected == null ? router.sources() : List.of(selected)) {
      if (!router.sources().contains(source)) {
        continue;
      }
      var row = mapper.forSource(ResearchSnapshotQuery.builder().date(date).source(source).build());
      if (row != null) {
        if ("COMPLETE".equals(read(row).getStatus())) {
          return row;
        }
        if (partial == null) {
          partial = row;
        }
      }
    }
    return partial;
  }

  private MarketBreadthView read(MarketBreadthSnapshot row) {
    try {
      var result = json.readValue(row.getMetricsJson(), MarketBreadthView.class);
      result.setSnapshotId(row.getId());
      result.setFetchedAt(row.getFetchedAt());
      return result;
    } catch (IOException e) {
      throw new IllegalStateException("INVALID_SAVED_MARKET_SNAPSHOT", e);
    }
  }

  private void status(LocalDate date, String status, Object evidence) {
    collection.collectionSave(
        ResearchCollection.builder()
            .dataKind("MARKET_BREADTH")
            .scope("SH_SZ_A")
            .businessDate(date)
            .status(status)
            .evidenceJson(Jsons.write(evidence))
            .build());
  }

  /** {@inheritDoc} */
  @Override
  public MarketBreadthView snapshot(LocalDate date) {
    if (market.synthetic()) {
      var result = new MarketBreadthView();
      result.setStatus("NOT_APPLICABLE");
      result.setReason("演示模式不采集真实市场截面");
      return result;
    }
    var saved = saved(date, null);
    if (saved != null) {
      return read(saved);
    }
    var attempt = collection.collection("MARKET_BREADTH", "SH_SZ_A", date);
    var result = new MarketBreadthView();
    result.setStatus(attempt == null ? "NOT_CHECKED" : attempt.getStatus());
    result.setUniverseScope("SH_SZ_A");
    return result;
  }
}

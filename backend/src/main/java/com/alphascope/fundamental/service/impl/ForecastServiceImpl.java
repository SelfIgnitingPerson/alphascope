package com.alphascope.fundamental.service.impl;

import com.alphascope.common.entity.ResearchCollection;
import com.alphascope.common.mapper.ResearchCollectionMapper;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.TaskStoppedException;
import com.alphascope.common.util.Jsons;
import com.alphascope.common.vo.CollectionView;
import com.alphascope.fundamental.entity.EarningsForecastSnapshot;
import com.alphascope.fundamental.mapper.ForecastMapper;
import com.alphascope.fundamental.provider.EastmoneyForecastProvider;
import com.alphascope.fundamental.service.ForecastService;
import com.alphascope.fundamental.vo.ForecastView;
import com.alphascope.market.service.MarketService;
import com.alphascope.operations.service.SourceLicenseService;
import com.alphascope.portfolio.service.PortfolioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * ForecastService 的业务实现。
 *
 * <p>独立采集并按实际首次观测时刻归档公开EPS预测；行情失败不阻断该采集。
 *
 * @author chenxuefeng
 */
@Service
public class ForecastServiceImpl implements ForecastService {
  private final ForecastMapper mapper;
  private final ResearchCollectionMapper collections;
  private final MarketService market;
  private final PortfolioService portfolio;
  private final ObjectMapper json;

  private SourceLicenseService licenses;

  @Autowired
  public void setLicenses(SourceLicenseService licenses) {
    this.licenses = licenses;
  }

  public ForecastServiceImpl(
      ForecastMapper mapper,
      ResearchCollectionMapper collections,
      MarketService market,
      PortfolioService portfolio,
      ObjectMapper json) {
    this.mapper = mapper;
    this.collections = collections;
    this.market = market;
    this.portfolio = portfolio;
    this.json = json;
  }

  private LocalDateTime now() {
    return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
  }

  /** {@inheritDoc} */
  @Override
  public void sync(String symbol) {
    if (market.synthetic()) {
      return;
    }
    var now = now();
    var old = collections.collection("EARNINGS_FORECAST", symbol, now.toLocalDate());
    if (old != null) {
      int minutes = "AVAILABLE".equals(old.getStatus()) ? 360 : 2;
      if (old.getUpdatedAt().plusMinutes(minutes).isAfter(now)) {
        return;
      }
    }
    try (var provider = new EastmoneyForecastProvider()) {
      if (licenses != null) licenses.require("EASTMONEY_FORECAST", "COLLECT");
      var value = provider.fetch(symbol);
      var observedAt = now();
      mapper.save(
          EarningsForecastSnapshot.builder()
              .instrumentId(market.instrument(symbol).getId())
              .observationDate(observedAt.toLocalDate())
              .availableAt(observedAt)
              .sourceUrl(value.sourceUrl())
              .contentHash(value.hash())
              .metricsJson(
                  Jsons.write(
                      Map.of(
                          "ratingAgencies",
                          value.ratingAgencies(),
                          "estimates",
                          value.estimates())))
              .rawJson(value.raw())
              .build());
      collections.collectionSave(
          ResearchCollection.builder()
              .dataKind("EARNINGS_FORECAST")
              .scope(symbol)
              .businessDate(observedAt.toLocalDate())
              .status("AVAILABLE")
              .evidenceJson(
                  Jsons.write(Map.of("availableAt", observedAt, "source", "EASTMONEY_PUBLIC")))
              .build());
    } catch (TaskStoppedException e) {
      throw e;
    } catch (RuntimeException e) {
      ExecutionContext.checkpoint();
      LoggerFactory.getLogger(getClass())
          .warn(
              "optional_collection_unavailable symbol={} failureType={}",
              symbol,
              e.getClass().getSimpleName());
      collections.collectionSave(
          ResearchCollection.builder()
              .dataKind("EARNINGS_FORECAST")
              .scope(symbol)
              .businessDate(now.toLocalDate())
              .status("UNAVAILABLE")
              .evidenceJson(Jsons.write(Map.of("reason", "公开预测采集失败或该证券无可用记录，保留已有快照")))
              .build());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void syncWatchlist() {
    if (market.synthetic()) {
      return;
    }
    for (var stock : portfolio.watchlist()) {
      if (Boolean.TRUE.equals(stock.getEnabled())) {
        sync(stock.getSymbol());
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<ForecastView> list(LocalDate asOf) {
    return at(asOf.atTime(LocalTime.MAX));
  }

  /** {@inheritDoc} */
  @Override
  public List<ForecastView> forReport(LocalDate date) {
    return at(date.atTime(16, 10));
  }

  private List<ForecastView> at(LocalDateTime requested) {
    var cutoff = requested.isAfter(now()) ? now() : requested;
    List<ForecastView> out = new ArrayList<>();
    for (var stock : portfolio.watchlist()) {
      if (Boolean.TRUE.equals(stock.getEnabled())) {
        out.add(view(stock.getSymbol(), stock.getName(), cutoff));
      }
    }
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public ForecastView view(String symbol, String name, LocalDateTime cutoff) {
    var limits =
        List.of(
            "公开页面的供应商汇总预测EPS，不等同于已核验逐家机构的一致预期。",
            "机构数为近六个月评级机构数；各年度EPS实际参与机构数和汇总算法未提供。",
            "原接口无可核验历史可用时刻，快照从实际采集时刻起可用，不倒填旧报告。",
            "EPS变化还可能来自机构样本或股本口径变化，不能仅据此确认盈利上修/下修；判断超预期需披露前同口径预测。");
    if (market.synthetic()) {
      return new ForecastView(
          symbol,
          name,
          "NOT_APPLICABLE",
          null,
          List.of(),
          "演示模式不接入真实预测",
          limits,
          CollectionView.empty());
    }
    var collection = collections.collection("EARNINGS_FORECAST", symbol, cutoff.toLocalDate());
    var rows = mapper.visible(symbol, cutoff);
    if (rows.isEmpty()) {
      return new ForecastView(
          symbol,
          name,
          "NOT_OBSERVED_AS_OF",
          null,
          List.of(),
          "该时点之前尚无已保存预测快照；当前观测请查看机构预测页。",
          limits,
          CollectionView.from(collection));
    }
    var latest = snapshot(rows.getFirst());
    List<ForecastView.Change> changes = new ArrayList<>();
    for (var current : latest.estimates()) {
      for (int i = 1; i < rows.size(); i++) {
        var previous = snapshot(rows.get(i));
        var estimate =
            previous.estimates().stream().filter(v -> v.year() == current.year()).findFirst();
        if (estimate.isPresent()) {
          changes.add(
              new ForecastView.Change(
                  current.year(),
                  estimate.get().eps(),
                  current.eps(),
                  current.eps() - estimate.get().eps(),
                  previous.availableAt(),
                  latest.availableAt()));
          break;
        }
      }
    }
    return new ForecastView(
        symbol,
        name,
        "AVAILABLE",
        latest,
        changes,
        changes.isEmpty() ? "已取得当前预测；尚无同年可比历史快照，将从接入日起积累。" : "比较已保存的同年度EPS汇总；差额不是已核实的机构调研结论。",
        limits,
        CollectionView.from(collection));
  }

  private ForecastView.Snapshot snapshot(EarningsForecastSnapshot row) {
    try {
      var metrics = json.readTree(row.getMetricsJson());
      var estimates =
          List.of(
              json.treeToValue(
                  metrics.path("estimates"), EastmoneyForecastProvider.Estimate[].class));
      return new ForecastView.Snapshot(
          row.getId(),
          row.getAvailableAt(),
          row.getFetchedAt(),
          row.getSource(),
          row.getSourceUrl(),
          metrics.path("ratingAgencies").asInt(),
          estimates);
    } catch (IOException e) {
      throw new IllegalStateException("预测快照JSON损坏", e);
    }
  }
}

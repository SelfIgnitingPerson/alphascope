package com.alphascope.fundamental.service.impl;

import com.alphascope.common.entity.ResearchCollection;
import com.alphascope.common.mapper.ResearchCollectionMapper;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.TaskStoppedException;
import com.alphascope.common.util.Jsons;
import com.alphascope.common.vo.CollectionView;
import com.alphascope.fundamental.mapper.FinancialSyncMapper;
import com.alphascope.fundamental.provider.EastmoneyFinancialProvider;
import com.alphascope.fundamental.service.FinancialSyncService;
import com.alphascope.market.service.MarketService;
import com.alphascope.operations.service.SourceLicenseService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FinancialSyncService 的业务实现。
 *
 * <p>财报为增强证据；失败保留状态和已有版本，不中断已具备行情的自选研究，也不填充模拟值。
 *
 * @author chenxuefeng
 */
@Service
public class FinancialSyncServiceImpl implements FinancialSyncService {
  private final FinancialSyncMapper mapper;
  private final ResearchCollectionMapper collection;
  private final MarketService market;
  private final TransactionTemplate tx;

  @Value("${alphascope.research.financial-sync-enabled:true}")
  private boolean enabled;

  private SourceLicenseService licenses;

  @Autowired
  public void setLicenses(SourceLicenseService licenses) {
    this.licenses = licenses;
  }

  public FinancialSyncServiceImpl(
      FinancialSyncMapper mapper,
      MarketService market,
      TransactionTemplate tx,
      ResearchCollectionMapper collection) {
    this.mapper = mapper;
    this.collection = collection;
    this.market = market;
    this.tx = tx;
  }

  /** {@inheritDoc} */
  @Override
  public void sync(String symbol, LocalDate date) {
    if (market.synthetic() || !enabled) {
      return;
    }
    var previous = collection.collection("FINANCIAL", symbol, date);
    if (previous != null) {
      var t = previous.getUpdatedAt();
      LocalDateTime at = t;
      long ttl = "AVAILABLE".equals(previous.getStatus()) ? 360 : 2;
      if (at.plusMinutes(ttl).isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))) {
        return;
      }
    }
    try (var provider = new EastmoneyFinancialProvider()) {
      if (licenses != null) licenses.require("EASTMONEY_FINANCIAL", "COLLECT");
      var rows = provider.fetch(symbol);
      if (rows.isEmpty()) {
        throw new IllegalStateException("EMPTY_FINANCIAL_RESPONSE");
      }
      long id = market.instrument(symbol).getId();
      var fetched = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
      tx.executeWithoutResult(
          status -> {
            mapper.lockInstrument(id);
            for (var row : rows) {
              if (mapper.exists(id, row.hash()) == 0) {
                mapper.insert(id, row, fetched);
              }
            }
          });
      collection.collectionSave(
          ResearchCollection.builder()
              .dataKind("FINANCIAL")
              .scope(symbol)
              .businessDate(date)
              .status("AVAILABLE")
              .evidenceJson(
                  Jsons.write(
                      Map.of(
                          "source",
                          "EASTMONEY_FREE",
                          "reportsFetched",
                          rows.size(),
                          "balancePeriodsFetched",
                          rows.stream()
                              .filter(
                                  r ->
                                      Jsons.read(
                                              r.rawJson(),
                                              com.fasterxml.jackson.databind.JsonNode.class)
                                          .path("balance")
                                          .isObject())
                              .count(),
                          "balancePolicy",
                          "资产负债表缺失独立保留未知，不以利润表可用代替三表齐全",
                          "publicationPrecision",
                          "DAY",
                          "availabilityPolicy",
                          "公告与供应商修订日期的次日；未知修订日期从抓取时起可用",
                          "pointInTimeStatus",
                          "供应商标记的修订日期，未核验原始公告版本；不作为严格历史回测样本")))
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
      collection.collectionSave(
          ResearchCollection.builder()
              .dataKind("FINANCIAL")
              .scope(symbol)
              .businessDate(date)
              .status("UNAVAILABLE")
              .evidenceJson(
                  Jsons.write(
                      Map.of("source", "EASTMONEY_FREE", "reason", "免费财报采集失败，保留已有证据；本次不生成替代数值")))
              .build());
    }
  }

  /** {@inheritDoc} */
  @Override
  public CollectionView status(String symbol, LocalDate date) {
    if (market.synthetic()) {
      return CollectionView.state("NOT_APPLICABLE", "演示模式不采集真实财报");
    }
    if (!enabled) {
      return CollectionView.state("DISABLED", "财报自动采集已关闭");
    }
    var row = collection.collection("FINANCIAL", symbol, date);
    return row == null ? CollectionView.state("NOT_CHECKED", null) : CollectionView.from(row);
  }
}

package com.alphascope.intelligence.service.impl;

import com.alphascope.common.util.Jsons;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.intelligence.dto.DailyObservationEvidence;
import com.alphascope.intelligence.dto.ForwardProtocol;
import com.alphascope.intelligence.dto.HoldingResearchQuery;
import com.alphascope.intelligence.dto.ObservationQuery;
import com.alphascope.intelligence.entity.ResearchObservation;
import com.alphascope.intelligence.mapper.ResearchObservationMapper;
import com.alphascope.intelligence.service.HoldingResearchService;
import com.alphascope.intelligence.service.ResearchObservationService;
import com.alphascope.intelligence.vo.ObservationCaptureView;
import com.alphascope.operations.mapper.CapacityMapper;
import com.alphascope.portfolio.service.PortfolioService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 每只证券独立事务留档，失败不伪装成已保存；重复正文复用原记录时间。
 *
 * @author chenxuefeng
 */
@Service
public class ResearchObservationServiceImpl implements ResearchObservationService {
  private final ResearchObservationMapper mapper;
  private final HoldingResearchService research;
  private final PortfolioService portfolio;
  private final CapacityMapper capacity;
  private final Clock clock;
  private final TransactionTemplate tx;

  public ResearchObservationServiceImpl(
      ResearchObservationMapper mapper,
      HoldingResearchService research,
      PortfolioService portfolio,
      CapacityMapper capacity,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.mapper = mapper;
    this.research = research;
    this.portfolio = portfolio;
    this.capacity = capacity;
    this.clock = clock;
    this.tx = new TransactionTemplate(transactions);
  }

  /** {@inheritDoc} */
  @Override
  public ObservationCaptureView capture(LocalDate date) {
    var now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
    if (date == null || date.isAfter(now.toLocalDate()) || date.getYear() < 1990)
      throw new IllegalArgumentException("留档日期须为有效非未来日期");
    if (date.equals(now.toLocalDate()) && now.toLocalTime().isBefore(LocalTime.of(15, 30)))
      throw new IllegalArgumentException("本日判断须15:30后、取得实际收盘证据再留档");
    var symbols = new LinkedHashSet<String>();
    portfolio.watchlistView().stream()
        .filter(w -> w.enabled())
        .forEach(w -> symbols.add(w.symbol()));
    portfolio.context(date).positions().stream()
        .filter(p -> p.getQuantity() != null && p.getQuantity().signum() > 0)
        .forEach(p -> symbols.add(p.getSymbol()));
    int saved = 0, reused = 0;
    var issues = new ArrayList<String>();
    if (symbols.size() > 1000) throw new IllegalArgumentException("单次留档最多1000只，请缩小研究范围");
    for (var symbol : symbols)
      try {
        var q = new HoldingResearchQuery();
        q.setAsOf(date);
        var view = research.read(symbol, q);
        if (view.close() == null
            || !date.equals(view.quoteDate())
            || view.priceVolume() == null
            || !"AVAILABLE".equals(view.priceVolume().status())) {
          issues.add(symbol + "：当日行情或同源量价证据不足，未形成可验证判断");
          continue;
        }
        var sources = new TreeSet<String>();
        if (view.inputSources() != null) sources.addAll(view.inputSources());
        var frozen = view.toBuilder().inputSources(Collections.unmodifiableSet(sources)).build();
        var row = new ResearchObservation();
        row.setSymbol(symbol);
        row.setBusinessDate(date);
        row.setCaptureKind(date.equals(now.toLocalDate()) ? "LIVE" : "RETROSPECTIVE");
        row.setEvidenceJson(
            Jsons.write(new DailyObservationEvidence(frozen, ForwardProtocol.current())));
        row.setEvidenceHash(Jsons.hash(row.getEvidenceJson()));
        int inserted =
            tx.execute(
                status -> {
                  capacity.lockUser(TenantContext.id());
                  if (mapper.count() >= 10000)
                    throw new IllegalStateException("观察记录达到10000条上限，请先导出并调整容量，不能静默删除原判断");
                  return mapper.save(row);
                });
        if (inserted > 0) saved++;
        else reused++;
      } catch (RuntimeException failure) {
        var id = UUID.randomUUID().toString().substring(0, 8);
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("每日留档失败 reference={} symbol={}", id, symbol, failure);
        issues.add(symbol + "：留档失败，原记录保留；维护编号" + id);
      }
    return new ObservationCaptureView(
        date,
        saved,
        reused,
        List.copyOf(issues),
        date.equals(now.toLocalDate()) ? "LIVE" : "RETROSPECTIVE");
  }

  /** {@inheritDoc} */
  @Override
  public ResearchObservation previous(ObservationQuery query) {
    var row = mapper.previous(query);
    if (row != null && !Jsons.hash(row.getEvidenceJson()).equals(row.getEvidenceHash()))
      throw new IllegalStateException("每日判断指纹不匹配");
    return row;
  }
}

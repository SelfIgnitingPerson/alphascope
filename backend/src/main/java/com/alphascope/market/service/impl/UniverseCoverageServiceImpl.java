package com.alphascope.market.service.impl;

import com.alphascope.market.calculator.UniverseCoverageCalculator;
import com.alphascope.market.mapper.MarketBreadthMapper;
import com.alphascope.market.provider.research.ResearchDataRouter;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.service.MarketUniverseService;
import com.alphascope.market.service.UniverseCoverageService;
import com.alphascope.market.vo.UniverseCoverageView;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 按当前允许来源读取最新不可变截面，优先合格全A证明；沪深证据保留实际交易所数量。
 *
 * @author chenxuefeng
 */
@Service
public class UniverseCoverageServiceImpl implements UniverseCoverageService {
  private final MarketBreadthMapper mapper;
  private final MarketService market;
  private final ResearchDataRouter router;
  private final MarketUniverseService universe;
  private final ConcurrentHashMap<String, UniverseCoverageView> cache = new ConcurrentHashMap<>();

  public UniverseCoverageServiceImpl(
      MarketBreadthMapper mapper,
      MarketService market,
      ResearchDataRouter router,
      MarketUniverseService universe) {
    this.mapper = mapper;
    this.market = market;
    this.router = router;
    this.universe = universe;
  }

  /** {@inheritDoc} */
  @Override
  public UniverseCoverageView inspect(LocalDate date) {
    if (market.synthetic()) return UniverseCoverageCalculator.unavailable(date);
    UniverseCoverageView partial = null;
    for (var row : mapper.coverageCandidates(date)) {
      if (!router.sources().contains(row.getSource())) continue;
      String key =
          row.getTradeDate()
              + ":"
              + row.getSource()
              + ":"
              + row.getUniverseScope()
              + ":"
              + row.getContentHash();
      if (cache.size() >= 32 && !cache.containsKey(key)) cache.clear();
      // 同一不可变快照只由一个线程解析和核对；其余请求复用结果，避免冷缓存并发放大正文哈希开销。
      var result =
          cache.computeIfAbsent(
              key,
              ignored -> {
                row.setRawJson(mapper.coverageRaw(row.getId()));
                return UniverseCoverageCalculator.calculate(row);
              });
      if (result.coverage().rosterVerified()) return withRoster(result, date);
      if (partial == null) partial = result;
    }
    return withRoster(
        partial == null ? UniverseCoverageCalculator.unavailable(date) : partial, date);
  }

  private UniverseCoverageView withRoster(UniverseCoverageView value, LocalDate date) {
    var roster = universe.snapshot(date);
    if (roster == null) return value;
    var missing = new java.util.ArrayList<>(value.missing());
    if ("AVAILABLE".equals(roster.status()) && !value.coverage().rosterVerified()) {
      missing.remove("未提供BSE的当日完整成员清单");
      missing.add("北交所供应商目录已归档" + roster.memberCount() + "只；尚缺与三所完整截面对齐的当日行情与停牌核验，目录不替代历史广度。");
    }
    return new UniverseCoverageView(
        value.businessDate(),
        value.status(),
        value.coverage(),
        value.source(),
        value.snapshotId(),
        value.contentHash(),
        value.exchangeMembers(),
        List.copyOf(missing),
        value.sourceUrl(),
        roster.summary());
  }
}

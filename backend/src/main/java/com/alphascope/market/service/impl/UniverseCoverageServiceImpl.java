package com.alphascope.market.service.impl;

import com.alphascope.market.calculator.UniverseCoverageCalculator;
import com.alphascope.market.mapper.MarketBreadthMapper;
import com.alphascope.market.provider.research.ResearchDataRouter;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.service.UniverseCoverageService;
import com.alphascope.market.vo.UniverseCoverageView;
import java.time.LocalDate;
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
  private final ConcurrentHashMap<String, UniverseCoverageView> cache = new ConcurrentHashMap<>();

  public UniverseCoverageServiceImpl(
      MarketBreadthMapper mapper, MarketService market, ResearchDataRouter router) {
    this.mapper = mapper;
    this.market = market;
    this.router = router;
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
      var result = cache.get(key);
      if (result == null) {
        row.setRawJson(mapper.coverageRaw(row.getId()));
        result = UniverseCoverageCalculator.calculate(row);
        if (cache.size() >= 32) cache.clear();
        cache.put(key, result);
      }
      if (result.coverage().rosterVerified()) return result;
      if (partial == null) partial = result;
    }
    return partial == null ? UniverseCoverageCalculator.unavailable(date) : partial;
  }
}

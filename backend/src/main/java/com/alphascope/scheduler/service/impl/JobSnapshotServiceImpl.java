package com.alphascope.scheduler.service.impl;

import com.alphascope.common.config.ResearchVersion;
import com.alphascope.common.util.Jsons;
import com.alphascope.industry.service.ThemeCohortService;
import com.alphascope.market.provider.baostock.BaoStockAccessGuard;
import com.alphascope.market.service.MarketObservationService;
import com.alphascope.market.service.MarketService;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.scheduler.mapper.JobInputMapper;
import com.alphascope.scheduler.service.JobSnapshotService;
import com.alphascope.signal.service.SignalService;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * JobSnapshotService的实现，集中维护模型、规则、来源、缓存和业务证据的指纹口径。
 *
 * @author chenxuefeng
 */
@Service
public class JobSnapshotServiceImpl implements JobSnapshotService {
  private final JobInputMapper inputs;
  private final MarketService market;
  private final PortfolioService portfolio;
  private final SignalService signals;
  private final ThemeCohortService cohorts;
  private final MarketObservationService observations;

  @Value("${spring.ai.model.chat:none}")
  private String chatMode = "none";

  @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
  private String chatModel = "gpt-4o-mini";

  public JobSnapshotServiceImpl(
      JobInputMapper inputs,
      MarketService market,
      PortfolioService portfolio,
      SignalService signals,
      ThemeCohortService cohorts,
      MarketObservationService observations) {
    this.inputs = inputs;
    this.market = market;
    this.portfolio = portfolio;
    this.signals = signals;
    this.cohorts = cohorts;
    this.observations = observations;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> capture(LocalDate date) {
    var state =
        new TreeMap<String, Object>(Jsons.evidenceObject(inputs.state(date, market.synthetic())));
    state.put("scope", market.dataScope());
    state.put("model", chatMode + ":" + chatModel);
    // 同优先级SQL结果顺序可能因新增证券改变，顺序变化不应触发重复研究任务。
    state.put(
        "watchlist",
        portfolio.watchlist().stream()
            .sorted(Comparator.comparing(row -> row.getSymbol()))
            .toList());
    state.put("positionRiskEnabled", portfolio.positionRiskEnabled());
    state.put("ruleParameters", signals.parameters());
    state.put("ruleCatalog", signals.catalog());
    state.put("cohortCatalog", cohorts.catalogHash());
    state.put("observationParameters", observations.parameters());
    state.put("cachePolicy", market.cachePolicy());
    state.put(
        "baostockAccessRestricted",
        !market.synthetic() && BaoStockAccessGuard.standard().blocked());
    if (portfolio.positionRiskEnabled()) {
      state.put("positions", portfolio.positions());
      state.put("account", portfolio.account());
    }
    state.put("algorithms", ResearchVersion.PIPELINE);
    return state;
  }
}

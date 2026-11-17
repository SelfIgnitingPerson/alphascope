package com.alphascope.market.service.impl;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.util.Jsons;
import com.alphascope.common.validation.DateRangeValidator;
import com.alphascope.industry.service.IndustryService;
import com.alphascope.market.calculator.ScopedMarketScoreCalculator;
import com.alphascope.market.dto.ScopedScoreInput;
import com.alphascope.market.dto.ScoreCoverage;
import com.alphascope.market.dto.ThemeScoreEvidence;
import com.alphascope.market.entity.MarketScore;
import com.alphascope.market.mapper.ScoreMapper;
import com.alphascope.market.service.FullMarketService;
import com.alphascope.market.service.MarketBreadthService;
import com.alphascope.market.service.MarketObservationService;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.service.ScoreService;
import com.alphascope.market.service.UniverseCoverageService;
import com.alphascope.market.vo.MarketOverviewView;
import com.alphascope.market.vo.MarketScoreView;
import com.alphascope.market.vo.ScopedScoreView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * ScoreService 的业务实现。
 *
 * <p>独立展示沪深观察事实；全A范围或五维规则未满足时不臆造综合分数。
 *
 * @author chenxuefeng
 */
@Service
public class ScoreServiceImpl implements ScoreService {
  private final ScoreMapper mapper;
  private final MarketService market;
  private final MarketBreadthService breadth;
  private final MarketObservationService observations;
  private final IndustryService industry;
  private final UniverseCoverageService universe;

  @org.springframework.beans.factory.annotation.Autowired private FullMarketService fullMarket;

  public ScoreServiceImpl(
      ScoreMapper mapper,
      MarketService market,
      MarketBreadthService breadth,
      MarketObservationService observations,
      IndustryService industry,
      UniverseCoverageService universe) {
    this.mapper = mapper;
    this.market = market;
    this.breadth = breadth;
    this.observations = observations;
    this.industry = industry;
    this.universe = universe;
  }

  /** {@inheritDoc} */
  @Override
  public void calculate(LocalDate date) {
    var result = research(date);
    mapper.save(
        MarketScore.builder()
            .tradeDate(date)
            .modelVersion("MARKET_V3")
            .score(result.score() == null ? null : BigDecimal.valueOf(result.score()))
            .state(result.state())
            .evidenceJson(Jsons.write(result))
            .synthetic(market.synthetic())
            .build());
  }

  /** {@inheritDoc} */
  @Override
  public ScopedScoreView research(LocalDate date) {
    var contexts = industry.snapshot(date);
    List<String> valid = new ArrayList<>();
    int positive = 0;
    for (var context : contexts) {
      var rows =
          context.scores().stream()
              .filter(
                  row ->
                      date.equals(row.getTradeDate())
                          && row.getRs20() != null
                          && row.getCoverage() != null
                          && row.getCoverage().doubleValue() >= 0.95)
              .toList();
      if (!rows.isEmpty()) {
        valid.add(context.code());
        if (rows.getLast().getRs20().signum() > 0) {
          positive++;
        }
      }
    }
    var themes =
        new ThemeScoreEvidence(
            date,
            new ScoreCoverage(
                "DISCLOSED_RESEARCH_SAMPLE", contexts.size(), valid.size(), !contexts.isEmpty()),
            valid.isEmpty() ? null : positive / (double) valid.size(),
            valid);
    var view = observations.view(date);
    var full = fullMarket == null ? null : fullMarket.observation(date);
    return ScopedMarketScoreCalculator.calculate(
        new ScopedScoreInput(
            date,
            universe.inspect(date).coverage(),
            market.synthetic(),
            full != null ? full : view == null ? null : view.analysis(),
            themes,
            full != null ? fullMarket.leaders(date) : observations.leaders(date)));
  }

  /** {@inheritDoc} */
  @Override
  public List<MarketScoreView> history(DateRangeRequest request) {
    LocalDate start = request.getStart();
    LocalDate end = request.getEnd();
    DateRangeValidator.requireValid(start, end);
    return mapper.scores(start, end, market.synthetic()).stream()
        .map(MarketScoreView::from)
        .toList();
  }

  /** {@inheritDoc} */
  @Override
  public MarketOverviewView overview(LocalDate date) {
    var scores = history(DateRangeRequest.builder().start(date).end(date).build());
    return new MarketOverviewView(
        date,
        scores.isEmpty() ? "UNKNOWN" : scores.getFirst().state(),
        scores,
        market.provider(),
        market.synthetic(),
        "CNY",
        scores.isEmpty() ? "INSUFFICIENT_DATA" : "PARTIAL",
        breadth.snapshot(date),
        observations.view(date));
  }
}

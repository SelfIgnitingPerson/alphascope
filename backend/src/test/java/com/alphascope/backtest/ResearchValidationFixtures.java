package com.alphascope.backtest;

import com.alphascope.backtest.calculator.MarketScoreValidationCalculator;
import com.alphascope.backtest.calculator.SignalEvaluationCalculator;
import com.alphascope.backtest.calculator.TechnicalRuleReplayCalculator;
import com.alphascope.backtest.dto.ResearchValidationRequest;
import com.alphascope.backtest.dto.SignalEvaluationInput;
import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.dto.TechnicalReplayInput;
import com.alphascope.backtest.dto.ValidationCommand;
import com.alphascope.backtest.dto.ValidationProtocol;
import com.alphascope.backtest.vo.ResearchValidationView;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.calculator.ScopedMarketScoreCalculator;
import com.alphascope.market.calculator.UniverseCoverageCalculator;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.dto.ScopedScoreInput;
import com.alphascope.market.dto.ScoreCoverage;
import com.alphascope.signal.rule.RiskRules;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 仅供隔离测试的确定性资料；TEST_FIXTURE不会连接真实供应商或用户数据库。
 *
 * @author chenxuefeng
 */
public final class ResearchValidationFixtures {
  private ResearchValidationFixtures() {}

  public static RiskRules rules() {
    return new RiskRules(-.03, 1.5, -.10, .20, .05, .03, .10);
  }

  public static List<MarketBarDO> bars(int count) {
    var result = new ArrayList<MarketBarDO>();
    for (int i = 0; i < count; i++) {
      var row = new MarketBarDO();
      row.setTradeDate(LocalDate.of(2025, 1, 2).plusDays(i));
      row.setOpen(BigDecimal.valueOf(200 - i));
      row.setHigh(BigDecimal.valueOf(201 - i));
      row.setLow(BigDecimal.valueOf(199 - i));
      row.setClose(BigDecimal.valueOf(200 - i));
      row.setVolume(BigDecimal.valueOf(1000));
      row.setAmount(BigDecimal.valueOf(200000));
      row.setFactor(BigDecimal.ONE);
      row.setSource("TEST_FIXTURE");
      row.setSynthetic(false);
      result.add(row);
    }
    return result;
  }

  public static ValidationCommand command() {
    var bars = bars(100);
    return new ValidationCommand(
        ResearchValidationRequest.builder()
            .start(bars.getFirst().getTradeDate())
            .end(bars.getLast().getTradeDate())
            .symbols(List.of("300476.SZ"))
            .build(),
        rules().snapshot());
  }

  public static ResearchValidationView report(ValidationCommand command) {
    var bars = bars(100);
    var dates = new TreeSet<>(bars.stream().map(MarketBarDO::getTradeDate).toList());
    var query =
        SignalStatisticsRequest.builder()
            .symbol("300476.SZ")
            .start(command.request().getStart())
            .end(command.request().getEnd())
            .build();
    var source = new SignalEvaluationInput(query, bars, bars, List.of(), dates, 20);
    var input = new TechnicalReplayInput(source, command.definition(), dates);
    var generated = TechnicalRuleReplayCalculator.reconstruct(input);
    String raw = Jsons.write(input),
        indicators = Jsons.write(generated.indicators()),
        signals = Jsons.write(generated.evaluation().signals());
    var result = SignalEvaluationCalculator.evaluate(generated.evaluation());
    var replay =
        new ResearchValidationView.Replay(
            "RETROSPECTIVE_RECONSTRUCTION",
            raw,
            Jsons.hash(raw),
            indicators,
            Jsons.hash(indicators),
            signals,
            Jsons.hash(signals),
            result,
            Jsons.hash(Jsons.write(result)));
    var marketInput =
        new MarketScoreValidationCalculator.Input(
            query.getStart(), query.getEnd(), List.of(), bars, dates, 20);
    var marketResults = MarketScoreValidationCalculator.evaluate(marketInput);
    String marketJson = Jsons.write(marketInput);
    var marketReplay =
        new ResearchValidationView.MarketReplay(
            "RETROSPECTIVE_CURRENT_SCORE_RECORDS",
            marketJson,
            Jsons.hash(marketJson),
            marketResults,
            Jsons.hash(Jsons.write(marketResults)));
    var score =
        ScopedMarketScoreCalculator.calculate(
            new ScopedScoreInput(
                query.getEnd(), new ScoreCoverage("FULL_A", 0, 0, false), false, null, null, null));
    var saved = SignalEvaluationCalculator.evaluate(source);
    return new ResearchValidationView(
        Instant.parse("2026-11-09T00:00:00Z"),
        ValidationProtocol.VERSION,
        command.request().getScope(),
        query.getStart(),
        query.getEnd(),
        "NOT_ESTABLISHED",
        score,
        UniverseCoverageCalculator.unavailable(query.getEnd()),
        0,
        marketReplay,
        command.definition(),
        List.of(
            new ResearchValidationView.StockValidation(
                "300476.SZ",
                bars.size(),
                bars.getFirst().getTradeDate(),
                bars.getLast().getTradeDate(),
                0,
                saved,
                saved,
                replay,
                null)),
        List.of("隔离固定样本，不证明投资效果"),
        List.of("TEST_FIXTURE仅供测试"));
  }
}

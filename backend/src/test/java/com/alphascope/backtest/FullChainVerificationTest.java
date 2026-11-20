package com.alphascope.backtest;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.backtest.calculator.ResearchValidationVerifier;
import com.alphascope.backtest.dto.TechnicalReplayInput;
import com.alphascope.backtest.vo.ResearchValidationView;
import com.alphascope.common.util.Jsons;
import org.junit.jupiter.api.Test;

/**
 * 即使修改者重算局部SHA，冻结价格和阈值仍须重建出相同指标、规则和统计。
 *
 * @author chenxuefeng
 */
class FullChainVerificationTest {
  @Test
  void pricesIndicatorsRulesAndStatisticsAllReproduceAfterSerialization() {
    var report = ResearchValidationFixtures.report(ResearchValidationFixtures.command());
    var restored = Jsons.read(Jsons.write(report), ResearchValidationView.class);
    var result = ResearchValidationVerifier.verify(restored);
    assertTrue(result.matched());
    assertEquals(1, result.technicalStocks());
    assertEquals(6, result.marketWindows());
  }

  @Test
  void rewrittenIndicatorWithItsOwnValidHashIsStillRejected() {
    var report = ResearchValidationFixtures.report(ResearchValidationFixtures.command());
    var old = report.stocks().getFirst().replay();
    var changed =
        new ResearchValidationView.Replay(
            old.origin(),
            old.inputJson(),
            old.inputSha256(),
            "[]",
            Jsons.hash("[]"),
            old.signalJson(),
            old.signalSha256(),
            old.result(),
            old.resultSha256());
    assertThrows(
        IllegalStateException.class,
        () -> ResearchValidationVerifier.verify(replace(report, changed)));
  }

  @Test
  void rewrittenSignalWithItsOwnValidHashIsStillRejected() {
    var report = ResearchValidationFixtures.report(ResearchValidationFixtures.command());
    var old = report.stocks().getFirst().replay();
    var changed =
        new ResearchValidationView.Replay(
            old.origin(),
            old.inputJson(),
            old.inputSha256(),
            old.indicatorJson(),
            old.indicatorSha256(),
            "[]",
            Jsons.hash("[]"),
            old.result(),
            old.resultSha256());
    assertThrows(
        IllegalStateException.class,
        () -> ResearchValidationVerifier.verify(replace(report, changed)));
  }

  @Test
  void changedPriceCannotHideBehindPreviouslyGeneratedSignals() {
    var report = ResearchValidationFixtures.report(ResearchValidationFixtures.command());
    var old = report.stocks().getFirst().replay();
    var input = Jsons.read(old.inputJson(), TechnicalReplayInput.class);
    input.source().bars().getLast().setClose(new java.math.BigDecimal("1"));
    String raw = Jsons.write(input);
    var changed =
        new ResearchValidationView.Replay(
            old.origin(),
            raw,
            Jsons.hash(raw),
            old.indicatorJson(),
            old.indicatorSha256(),
            old.signalJson(),
            old.signalSha256(),
            old.result(),
            old.resultSha256());
    assertThrows(
        IllegalStateException.class,
        () -> ResearchValidationVerifier.verify(replace(report, changed)));
  }

  private ResearchValidationView replace(
      ResearchValidationView report, ResearchValidationView.Replay replay) {
    var old = report.stocks().getFirst();
    var stock =
        new ResearchValidationView.StockValidation(
            old.symbol(),
            old.priceDays(),
            old.firstPriceDate(),
            old.lastPriceDate(),
            old.savedRuleRecords(),
            old.savedRetrospective(),
            old.savedPointInTime(),
            replay,
            null);
    return new ResearchValidationView(
        report.checkedAt(),
        report.protocolVersion(),
        report.scope(),
        report.start(),
        report.end(),
        report.fullMarketVerdict(),
        report.scoreAtEnd(),
        report.coverageAtEnd(),
        report.qualifiedScoreDays(),
        report.marketReplay(),
        report.ruleDefinition(),
        java.util.List.of(stock),
        report.blockers(),
        report.limitations(),
        report.portfolioReplay());
  }
}

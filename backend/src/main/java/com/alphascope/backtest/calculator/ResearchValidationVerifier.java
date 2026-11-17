package com.alphascope.backtest.calculator;

import com.alphascope.backtest.dto.TechnicalReplayInput;
import com.alphascope.backtest.dto.ValidationProtocol;
import com.alphascope.backtest.vo.ResearchValidationView;
import com.alphascope.backtest.vo.ValidationVerification;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.util.Jsons;
import com.alphascope.indicator.calculator.TechnicalEvidenceCalculator;

/**
 * 不访问数据库或现行配置的完整复算：输入指纹、指标、逐条规则及描述统计逐层一致。
 *
 * @author chenxuefeng
 */
public final class ResearchValidationVerifier {
  private ResearchValidationVerifier() {}

  /**
   * 从冻结价格和参数重新生成信号，不能直接信任已生成信号作为复算起点。
   *
   * @param report 完整冻结证据
   * @return 逐层匹配的数量与算法
   */
  public static ValidationVerification verify(ResearchValidationView report) {
    require(ValidationProtocol.VERSION.equals(report.protocolVersion()), "不支持的验证冻结协议");
    int windows = 0, stocks = 0;
    if (report.marketReplay() != null) {
      var market = report.marketReplay();
      hash(market.inputJson(), market.inputSha256(), "市场输入");
      hash(Jsons.write(market.results()), market.resultSha256(), "市场结果");
      var result =
          MarketScoreValidationCalculator.evaluate(
              Jsons.read(market.inputJson(), MarketScoreValidationCalculator.Input.class));
      hash(Jsons.write(result), market.resultSha256(), "市场复算");
      windows = result.size();
    }
    for (var stock : report.stocks()) {
      ExecutionContext.checkpoint();
      var replay = stock.replay();
      if (replay == null) continue;
      require("RETROSPECTIVE_RECONSTRUCTION".equals(replay.origin()), "回顾来源标记错误");
      hash(replay.inputJson(), replay.inputSha256(), "价格与参数");
      hash(replay.indicatorJson(), replay.indicatorSha256(), "指标证据");
      hash(replay.signalJson(), replay.signalSha256(), "规则证据");
      hash(Jsons.write(replay.result()), replay.resultSha256(), "统计结果");
      var input = Jsons.read(replay.inputJson(), TechnicalReplayInput.class);
      require(
          Jsons.write(input.definition()).equals(Jsons.write(report.ruleDefinition())),
          "冻结参数与报告声明不一致");
      require(stock.symbol().equals(input.source().request().getSymbol()), "股票范围与冻结输入不一致");
      var generated = TechnicalRuleReplayCalculator.reconstruct(input);
      hash(Jsons.write(generated.indicators()), replay.indicatorSha256(), "指标重建");
      hash(Jsons.write(generated.evaluation().signals()), replay.signalSha256(), "规则重建");
      hash(
          Jsons.write(SignalEvaluationCalculator.evaluate(generated.evaluation())),
          replay.resultSha256(),
          "统计重建");
      if (replay.strategy() != null) {
        require(
            StrategyReplayCalculator.VERSION.equals(replay.strategy().version()), "不支持的策略执行扩展版本");
        hash(Jsons.write(replay.strategy()), replay.strategySha256(), "策略结果");
        hash(
            Jsons.write(StrategyReplayCalculator.evaluate(input)),
            replay.strategySha256(),
            "策略执行复算");
      }
      stocks++;
    }
    return new ValidationVerification(true, windows, stocks, TechnicalEvidenceCalculator.VERSION);
  }

  private static void hash(String value, String expected, String layer) {
    require(
        value != null && expected != null && Jsons.hash(value).equals(expected),
        layer + "指纹或复算不一致");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

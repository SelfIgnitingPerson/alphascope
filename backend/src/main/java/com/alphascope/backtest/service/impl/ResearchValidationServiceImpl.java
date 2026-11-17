package com.alphascope.backtest.service.impl;

import com.alphascope.backtest.calculator.MarketScoreValidationCalculator;
import com.alphascope.backtest.calculator.SignalEvaluationCalculator;
import com.alphascope.backtest.calculator.StrategyReplayCalculator;
import com.alphascope.backtest.calculator.TechnicalRuleReplayCalculator;
import com.alphascope.backtest.dto.SignalEvaluationInput;
import com.alphascope.backtest.dto.TechnicalReplayInput;
import com.alphascope.backtest.dto.ValidationCommand;
import com.alphascope.backtest.dto.ValidationProtocol;
import com.alphascope.backtest.service.ResearchValidationService;
import com.alphascope.backtest.service.ValidationDataService;
import com.alphascope.backtest.vo.ResearchValidationView;
import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.ExecutionProgress;
import com.alphascope.common.util.Jsons;
import com.alphascope.operations.service.SourceLicenseService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 数据捕获后在事务外执行计算；价格、指标、规则和统计四层分别冻结，支持逐层离线核对。
 *
 * @author chenxuefeng
 */
@Service
public class ResearchValidationServiceImpl implements ResearchValidationService {
  public static final String PROTOCOL = ValidationProtocol.VERSION;
  private final ValidationDataService data;
  private final Clock clock;
  private final AlphaScopeProperties properties;

  private SourceLicenseService licenses;

  @Autowired
  public void setLicenses(SourceLicenseService licenses) {
    this.licenses = licenses;
  }

  public ResearchValidationServiceImpl(
      ValidationDataService data, Clock clock, AlphaScopeProperties properties) {
    this.data = data;
    this.clock = clock;
    this.properties = properties;
  }

  /** {@inheritDoc} */
  @Override
  public ResearchValidationView validate(ValidationCommand command) {
    var dataset = data.capture(command);
    if (licenses != null) licenses.requireEvidence(dataset, "DERIVED_REPORT");
    ExecutionContext.checkpoint();
    var request = command.request();
    var blockers = new ArrayList<String>();
    ResearchValidationView.MarketReplay marketReplay = null;
    if (dataset.market() != null) {
      var results = MarketScoreValidationCalculator.evaluate(dataset.market());
      String input = Jsons.write(dataset.market());
      marketReplay =
          new ResearchValidationView.MarketReplay(
              "RETROSPECTIVE_CURRENT_SCORE_RECORDS",
              input,
              Jsons.hash(input),
              results,
              Jsons.hash(Jsons.write(results)));
      if ("UNKNOWN".equals(dataset.score().state())) blockers.add("完整全A输入尚不合格，详见实际清单和分项缺项");
      if (results.stream()
          .filter(row -> row.period().equals("HOLDOUT"))
          .anyMatch(row -> !"DESCRIPTIVE_ONLY".equals(row.status())))
        blockers.add("完整全A评分仅" + dataset.qualifiedScoreDays() + "天，较晚区间有效配对不足");
      blockers.add("描述相关与时间留出不构成独立前瞻验证或显著性结论");
    }
    var stocks = new ArrayList<ResearchValidationView.StockValidation>();
    int completed = 0;
    long evidenceBytes =
        marketReplay == null ? 0 : marketReplay.inputJson().getBytes(StandardCharsets.UTF_8).length;
    for (var stock : dataset.stocks()) {
      ExecutionContext.checkpoint();
      ResearchValidationView.Replay replay = null;
      if (stock.failure() == null) {
        var source =
            new SignalEvaluationInput(
                stock.retrospective().request(),
                stock.bars(),
                stock.benchmark(),
                List.of(),
                stock.calendar(),
                stock.retrospective().minimumSamples());
        var specification =
            new TechnicalReplayInput(source, command.definition(), stock.stockDates());
        var generated = TechnicalRuleReplayCalculator.reconstruct(specification);
        var result = SignalEvaluationCalculator.evaluate(generated.evaluation());
        String input = Jsons.write(specification),
            indicators = Jsons.write(generated.indicators()),
            signals = Jsons.write(generated.evaluation().signals());
        evidenceBytes +=
            input.getBytes(StandardCharsets.UTF_8).length
                + indicators.getBytes(StandardCharsets.UTF_8).length
                + signals.getBytes(StandardCharsets.UTF_8).length;
        if (evidenceBytes > properties.getValidation().getMaximumEvidenceBytes())
          throw new IllegalArgumentException("重建证据超过容量限制，请减少证券或日期区间");
        var strategy = StrategyReplayCalculator.evaluate(specification);
        replay =
            new ResearchValidationView.Replay(
                "RETROSPECTIVE_RECONSTRUCTION",
                input,
                Jsons.hash(input),
                indicators,
                Jsons.hash(indicators),
                signals,
                Jsons.hash(signals),
                result,
                Jsons.hash(Jsons.write(result)),
                strategy,
                Jsons.hash(Jsons.write(strategy)));
      } else blockers.add(stock.symbol() + "：" + stock.failure());
      var bars = stock.bars();
      stocks.add(
          new ResearchValidationView.StockValidation(
              stock.symbol(),
              bars.size(),
              bars.isEmpty() ? null : bars.getFirst().getTradeDate(),
              bars.isEmpty() ? null : bars.getLast().getTradeDate(),
              stock.retrospective().signals().size(),
              SignalEvaluationCalculator.evaluate(stock.retrospective()),
              SignalEvaluationCalculator.evaluate(stock.pointInTime()),
              replay,
              stock.failure()));
      ExecutionContext.progress(
          new ExecutionProgress(
              ++completed, dataset.stocks().size(), "完成逐日验证 " + stock.symbol(), clock.instant()));
    }
    return new ResearchValidationView(
        clock.instant(),
        PROTOCOL,
        request.getScope(),
        request.getStart(),
        request.getEnd(),
        marketReplay == null ? "NOT_REQUESTED" : "NOT_ESTABLISHED",
        dataset.score(),
        dataset.coverage(),
        dataset.qualifiedScoreDays(),
        marketReplay,
        command.definition(),
        List.copyOf(stocks),
        List.copyOf(blockers),
        List.of(
            "仅检验排队时冻结的当前自选；有选择及幸存者偏差，不能推广至全A",
            "使用当前已保存价格版本回顾，不是过去实际运行或当时可得的证明",
            "重建只包含五条技术规则；财报、主题、盈利预期及个人持仓风险不参与",
            "按固定日期70%/30%划分，当前规则可能受历史启发，不宣称未经接触的独立样本外验证",
            "逐规则逐窗口排除重叠，不同证券和规则仍相关，样本量不简单加总",
            "风险触发后上涨不表示提示有效；收益为收盘描述，不是策略胜率",
            "未包含交易成本、涨跌停可成交性和组合回撤，小样本不能证明方法稳定有效"));
  }
}

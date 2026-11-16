package com.alphascope.backtest.calculator;

import com.alphascope.backtest.dto.AvailabilityMode;
import com.alphascope.backtest.dto.SignalEvaluationInput;
import com.alphascope.backtest.dto.TechnicalReplayInput;
import com.alphascope.backtest.dto.TechnicalReplayResult;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.util.Jsons;
import com.alphascope.indicator.calculator.TechnicalEvidenceCalculator;
import com.alphascope.indicator.dto.TechnicalWindow;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.signal.entity.SignalEvent;
import com.alphascope.signal.rule.RiskRules;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * 使用生产唯一技术证据算法逐日重建回顾样本，不写业务信号表或倒填保存时刻。
 *
 * @author chenxuefeng
 */
public final class TechnicalRuleReplayCalculator {
  /** 本次仅验证五条技术规则，不借用今天财报或主题数据重建过去。 */
  public static final Set<String> RULES =
      Set.of("TREND_BREAK_20", "TREND_BREAK_60", "VOLUME_SELL", "NEW_LOW_20", "RELATIVE_WEAK");

  private TechnicalRuleReplayCalculator() {}

  /**
   * 逐日复用生产精度、预热和交易日完整性规则。
   *
   * @param specification 原价、权威日历、标的应有日期和冻结规则
   * @return 按层保留技术证据与信号；不生成历史修订ID或保存时间
   */
  public static TechnicalReplayResult reconstruct(TechnicalReplayInput specification) {
    var source = specification.source();
    var rules = RiskRules.restore(specification.definition());
    if (!source.signals().isEmpty()) throw new IllegalArgumentException("价格重建输入不能借用已经生成的信号");
    if (source.request().getAvailabilityMode() != AvailabilityMode.RETROSPECTIVE) {
      throw new IllegalArgumentException("重建只用于当前保存版本回顾，不能生成严格时点证明");
    }
    var stocks = index(source.bars());
    var benchmark = index(source.benchmark());
    var events = new ArrayList<SignalEvent>();
    var indicators = new ArrayList<TechnicalReplayResult.EvidenceRow>();
    for (LocalDate date : source.tradingDates().stream().sorted().toList()) {
      ExecutionContext.checkpoint();
      if (date.isBefore(source.request().getStart()) || date.isAfter(source.request().getEnd()))
        continue;
      var bars =
          stocks.headMap(date, true).descendingMap().values().stream()
              .limit(61)
              .sorted(Comparator.comparing(MarketBarDO::getTradeDate))
              .toList();
      var base =
          bars.stream()
              .map(bar -> benchmark.get(bar.getTradeDate()))
              .filter(Objects::nonNull)
              .toList();
      var evidence =
          TechnicalEvidenceCalculator.calculate(
              new TechnicalWindow(date, bars, base, specification.stockTradingDates()));
      indicators.add(new TechnicalReplayResult.EvidenceRow(date, evidence));
      for (var result : rules.evaluate(evidence, false, false)) {
        if (!RULES.contains(result.ruleCode())) continue;
        var event = new SignalEvent();
        event.setTradeDate(date);
        event.setRuleCode(result.ruleCode());
        event.setRuleVersion(result.ruleVersion());
        event.setSeverity(result.severity());
        event.setStatus(result.status());
        event.setSynthetic(false);
        event.setEvidenceJson(
            Jsons.write(
                Map.of(
                    "values",
                    result.evidence(),
                    "ruleParameters",
                    rules.parameters(),
                    "technicalVersion",
                    TechnicalEvidenceCalculator.VERSION,
                    "origin",
                    "RETROSPECTIVE_RECONSTRUCTION")));
        events.add(event);
      }
    }
    return new TechnicalReplayResult(
        new SignalEvaluationInput(
            source.request(),
            source.bars(),
            source.benchmark(),
            events,
            source.tradingDates(),
            source.minimumSamples()),
        List.copyOf(indicators));
  }

  private static TreeMap<LocalDate, MarketBarDO> index(List<MarketBarDO> rows) {
    var result = new TreeMap<LocalDate, MarketBarDO>();
    String expectedSource = null;
    for (var row : rows) {
      if (!Boolean.FALSE.equals(row.getSynthetic())
          || row.getSource() == null
          || row.getSource().isBlank()) {
        throw new IllegalArgumentException("回顾重建只接受明确标记的真实、同源行情");
      }
      if (expectedSource != null && !expectedSource.equals(row.getSource()))
        throw new IllegalArgumentException("同一证券不能混合来源");
      expectedSource = row.getSource();
      if (result.put(row.getTradeDate(), row) != null) throw new IllegalArgumentException("同日行情重复");
    }
    return result;
  }
}

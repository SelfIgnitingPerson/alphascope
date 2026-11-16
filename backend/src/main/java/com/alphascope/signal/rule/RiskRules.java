package com.alphascope.signal.rule;

import com.alphascope.indicator.calculator.TechnicalEvidenceCalculator;
import com.alphascope.signal.dto.RuleCatalog;
import com.alphascope.signal.dto.RuleDefinitionSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 纯证据判定：不读取数据库或外部接口；所有比例用小数，毛利率下降用百分点/100。
 *
 * @author chenxuefeng
 */
@Component
public class RiskRules {
  @Value("${alphascope.rules.version:RULE_V2}")
  private String ruleVersion = "RULE_V2";

  private final double sellReturn,
      sellVolume,
      relativeWeak,
      revenueGrowth,
      profitGrowth,
      marginDrop,
      concentration;

  public RiskRules(
      @Value("${alphascope.rules.sell-return:-0.03}") double sellReturn,
      @Value("${alphascope.rules.sell-volume:1.5}") double sellVolume,
      @Value("${alphascope.rules.relative-weak:-0.10}") double relativeWeak,
      @Value("${alphascope.rules.revenue-growth:0.20}") double revenueGrowth,
      @Value("${alphascope.rules.profit-growth:0.05}") double profitGrowth,
      @Value("${alphascope.rules.margin-drop:0.03}") double marginDrop,
      @Value("${alphascope.rules.concentration:0.10}") double concentration) {
    this.sellReturn = sellReturn;
    this.sellVolume = sellVolume;
    this.relativeWeak = relativeWeak;
    this.revenueGrowth = revenueGrowth;
    this.profitGrowth = profitGrowth;
    this.marginDrop = marginDrop;
    this.concentration = concentration;
  }

  /**
   * 一条规则按完整证据计算出的评估结果。
   *
   * @author chenxuefeng
   */
  public record Result(
      /** 稳定规则代码，例如TREND_BREAK_20或PROFIT_SLOWDOWN。 */
      String ruleCode,

      /** 规则及阈值配置标识，用于追溯当时判断口径。 */
      String ruleVersion,

      /** 规则严重度：HIGH高、MEDIUM中、LOW低。 */
      String severity,

      /** 规则状态：TRIGGERED触发、NOT_TRIGGERED未触发、INSUFFICIENT_DATA不足或NOT_APPLICABLE不适用。 */
      String status,

      /** 本条判断采用的具体指标值或证据列表。 */
      Map<String, Double> evidence) {}

  /**
   * 返回完整预期规则，而不是根据已保存结果推断规则数量。
   *
   * @param positionRiskEnabled 当前是否评估个人持仓
   * @return 当前版本、全部规则代码及适用性
   */
  public RuleCatalog catalog(boolean positionRiskEnabled) {
    return new RuleCatalog(
        ruleVersion,
        Arrays.stream(RuleCode.values())
            .map(
                code ->
                    new RuleCatalog.Definition(
                        code.name(),
                        code != RuleCode.POSITION_CONCENTRATION || positionRiskEnabled))
            .toList());
  }

  public Map<String, Double> parameters() {
    return Map.of(
        "sellReturn",
        sellReturn,
        "sellVolume",
        sellVolume,
        "relativeWeak",
        relativeWeak,
        "revenueGrowth",
        revenueGrowth,
        "profitGrowth",
        profitGrowth,
        "marginDrop",
        marginDrop,
        "concentration",
        concentration);
  }

  /**
   * 冻结当次口径，避免队列等待或离线复算时借用新配置。
   *
   * @return 实际版本、技术算法及全部阈值
   */
  public RuleDefinitionSnapshot snapshot() {
    return new RuleDefinitionSnapshot(
        ruleVersion, TechnicalEvidenceCalculator.VERSION, parameters());
  }

  /**
   * 只使用冻结配置恢复规则；未知版本或不完整阈值直接拒绝。
   *
   * @param definition 实验中实际冻结的配置
   * @return 不依赖Spring或当前环境变量的规则实例
   */
  public static RiskRules restore(RuleDefinitionSnapshot definition) {
    if (definition == null
        || !TechnicalEvidenceCalculator.VERSION.equals(definition.technicalVersion())
        || definition.ruleVersion() == null
        || definition.ruleVersion().isBlank()) {
      throw new IllegalArgumentException("不支持的冻结规则或技术证据版本");
    }
    var p = definition.parameters();
    if (p == null
        || !p.keySet()
            .equals(
                Set.of(
                    "sellReturn",
                    "sellVolume",
                    "relativeWeak",
                    "revenueGrowth",
                    "profitGrowth",
                    "marginDrop",
                    "concentration"))
        || p.values().stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
      throw new IllegalArgumentException("冻结规则阈值不完整或非法");
    }
    var restored =
        new RiskRules(
            p.get("sellReturn"),
            p.get("sellVolume"),
            p.get("relativeWeak"),
            p.get("revenueGrowth"),
            p.get("profitGrowth"),
            p.get("marginDrop"),
            p.get("concentration"));
    restored.ruleVersion = definition.ruleVersion();
    return restored;
  }

  public List<Result> evaluate(Map<String, Double> e, boolean synthetic) {
    return evaluate(e, synthetic, true);
  }

  public List<Result> evaluate(
      Map<String, Double> e, boolean synthetic, boolean positionRiskEnabled) {
    List<Result> out = new ArrayList<>();
    add(
        out,
        e,
        synthetic,
        RuleCode.TREND_BREAK_20.name(),
        "MEDIUM",
        List.of("close", "previousClose", "ma20", "previousMa20"),
        () ->
            v(e, "close") < v(e, "ma20")
                && v(e, "previousClose") < v(e, "previousMa20")
                && v(e, "ma20") < v(e, "previousMa20"));
    add(
        out,
        e,
        synthetic,
        RuleCode.TREND_BREAK_60.name(),
        "HIGH",
        List.of("close", "ma60", "previousMa60"),
        () -> v(e, "close") < v(e, "ma60") && v(e, "ma60") < v(e, "previousMa60"));
    add(
        out,
        e,
        synthetic,
        RuleCode.VOLUME_SELL.name(),
        "HIGH",
        List.of("return", "volumeRatio"),
        () -> v(e, "return") < sellReturn && v(e, "volumeRatio") > sellVolume);
    add(
        out,
        e,
        synthetic,
        RuleCode.NEW_LOW_20.name(),
        "MEDIUM",
        List.of("close", "previous20Low"),
        () -> v(e, "close") < v(e, "previous20Low"));
    add(
        out,
        e,
        synthetic,
        RuleCode.RELATIVE_WEAK.name(),
        "MEDIUM",
        List.of("rs20", "previousRs20"),
        () -> v(e, "rs20") < relativeWeak && v(e, "rs20") < v(e, "previousRs20"));
    add(
        out,
        e,
        synthetic,
        RuleCode.PROFIT_SLOWDOWN.name(),
        "MEDIUM",
        List.of("revenueYoy", "profitYoy"),
        () -> v(e, "revenueYoy") > revenueGrowth && v(e, "profitYoy") < profitGrowth);
    add(
        out,
        e,
        synthetic,
        RuleCode.MARGIN_DECLINE.name(),
        "MEDIUM",
        List.of("grossMargin", "previousYearGrossMargin"),
        () -> v(e, "previousYearGrossMargin") - v(e, "grossMargin") > marginDrop);
    add(
        out,
        e,
        synthetic,
        RuleCode.CASHFLOW_WARNING.name(),
        "HIGH",
        List.of("cashflow", "previousYearCashflow", "twoYearsAgoCashflow"),
        () ->
            v(e, "cashflow") < 0
                && v(e, "cashflow") < v(e, "previousYearCashflow")
                && v(e, "previousYearCashflow") < v(e, "twoYearsAgoCashflow"));
    if (positionRiskEnabled) {
      add(
          out,
          e,
          synthetic,
          RuleCode.POSITION_CONCENTRATION.name(),
          "HIGH",
          List.of("positionWeight"),
          () -> v(e, "positionWeight") > concentration);
    } else {
      out.add(
          new Result(
              RuleCode.POSITION_CONCENTRATION.name(),
              ruleVersion,
              "HIGH",
              "NOT_APPLICABLE",
              Map.of()));
    }
    add(
        out,
        e,
        synthetic,
        RuleCode.INDUSTRY_DIVERGENCE.name(),
        "MEDIUM",
        List.of("themeReturn", "themeBreadth", "previousThemeBreadth", "twoDaysAgoThemeBreadth"),
        () ->
            v(e, "themeReturn") > 0
                && v(e, "themeBreadth") < v(e, "previousThemeBreadth")
                && v(e, "previousThemeBreadth") < v(e, "twoDaysAgoThemeBreadth"));
    return out;
  }

  private double v(Map<String, Double> e, String key) {
    return e.get(key);
  }

  private void add(
      List<Result> out,
      Map<String, Double> e,
      boolean synthetic,
      String code,
      String severity,
      List<String> keys,
      BooleanSupplier trigger) {
    Map<String, Double> selected = new LinkedHashMap<>();
    boolean complete = !synthetic;
    for (String key : keys) {
      Double value = e.get(key);
      if (value == null || !Double.isFinite(value)) {
        complete = false;
      } else {
        selected.put(key, value);
      }
    }
    out.add(
        new Result(
            code,
            ruleVersion,
            severity,
            !complete
                ? "INSUFFICIENT_DATA"
                : trigger.getAsBoolean() ? "TRIGGERED" : "NOT_TRIGGERED",
            selected));
  }
}

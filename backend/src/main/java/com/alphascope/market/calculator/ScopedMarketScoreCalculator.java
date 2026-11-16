package com.alphascope.market.calculator;

import com.alphascope.market.dto.ScopedScoreInput;
import com.alphascope.market.vo.ScopedScoreView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 固定五维计分公式及范围门槛；算法可完整计算，但代理或缺项不进入完整市场总分。
 *
 * @author chenxuefeng
 */
public final class ScopedMarketScoreCalculator {
  private ScopedMarketScoreCalculator() {}

  /**
   * 计算五维分项及完整范围总分。
   *
   * @param input 同日事实、实际分母和目标范围
   * @return 保留所有缺项的实验性评分，不把未知当0分
   */
  public static ScopedScoreView calculate(ScopedScoreInput input) {
    var dimensions = new LinkedHashMap<String, ScopedScoreView.Dimension>();
    var observation = input.observation();
    boolean same = observation != null && input.businessDate().equals(observation.businessDate());
    var trend = same ? observation.trend() : null;
    Double trendScore = null;
    if (trend != null
        && "AVAILABLE".equals(trend.status())
        && positive(trend.close())
        && positive(trend.ma20())
        && positive(trend.ma60())
        && finite(trend.ma20Slope())) {
      trendScore =
          (trend.close() >= trend.ma20() ? 5d : 0)
              + (trend.close() >= trend.ma60() ? 5d : 0)
              + (trend.ma20() >= trend.ma60() ? 5d : 0)
              + (trend.ma20Slope() > 0 ? 5d : 0);
    }
    dimensions.put(
        "trend",
        dimension(
            trendScore,
            "INDEX_PROXY",
            true,
            input,
            "四项各5分：收盘≥MA20、收盘≥MA60、MA20≥MA60、MA20上行",
            trend));
    var turnover = same ? observation.turnover() : null;
    Double turnScore = null;
    if (turnover != null
        && "AVAILABLE".equals(turnover.status())
        && turnover.completeDays() == 20
        && positive(turnover.ratio20())
        && trend != null
        && finite(trend.dayReturn())) {
      turnScore =
          trend.dayReturn() >= 0
              ? (turnover.ratio20() >= 1.2 ? 20d : turnover.ratio20() >= 1 ? 15d : 10d)
              : turnover.ratio20() < 1 ? 5d : 0d;
    }
    dimensions.put(
        "turnoverDirection",
        dimension(
            turnScore,
            same ? observation.scope() : "UNKNOWN",
            false,
            input,
            "指数非负收益：量比≥1.2得20，≥1得15，否则10；负收益：缩量5，否则0",
            turnover));
    var breadth = same ? observation.breadth() : null;
    Double breadthScore = null;
    if (breadth != null
        && "AVAILABLE".equals(breadth.status())
        && ratio(breadth.ratio20())
        && ratio(breadth.ratio60())
        && enough(breadth.valid20(), breadth.trading())
        && enough(breadth.valid60(), breadth.trading())
        && finite(breadth.coverage20())
        && breadth.coverage20() >= 0.95
        && finite(breadth.coverage60())
        && breadth.coverage60() >= 0.95) {
      breadthScore = 10 * breadth.ratio20() + 10 * breadth.ratio60();
    }
    dimensions.put(
        "breadth",
        dimension(
            breadthScore,
            same ? observation.scope() : "UNKNOWN",
            false,
            input,
            "10×站上MA20比例 + 10×站上MA60比例；两窗口均须覆盖≥95%",
            breadth));
    var themes = input.themes();
    Double themeScore =
        themes != null
                && input.businessDate().equals(themes.businessDate())
                && themes.coverage() != null
                && themes.coverage().sufficient(0.95)
                && ratio(themes.positiveRatio())
            ? 20 * themes.positiveRatio()
            : null;
    dimensions.put(
        "themeRelativeStrength",
        dimension(
            themeScore,
            themes == null || themes.coverage() == null ? "UNKNOWN" : themes.coverage().scope(),
            false,
            input,
            "20×有效主题RS20为正比例；完整清单与有效覆盖均须核验",
            themes));
    var leaders = input.leaders();
    Double leaderScore =
        leaders != null
                && input.businessDate().equals(leaders.businessDate())
                && "AVAILABLE".equals(leaders.status())
                && leaders.coverage() != null
                && leaders.coverage().sufficient(0.95)
                && !leaders.leaders().isEmpty()
                && leaders.confirmed() >= 0
                && leaders.confirmed() <= leaders.leaders().size()
            ? 20d * leaders.confirmed() / leaders.leaders().size()
            : null;
    dimensions.put(
        "leaderConfirmation",
        dimension(
            leaderScore,
            leaders == null || leaders.coverage() == null ? "UNKNOWN" : leaders.coverage().scope(),
            false,
            input,
            "全市场20日均成交额前10%中，站上MA20且20日跑赢沪深300的比例×20",
            leaders));
    var credited = new LinkedHashMap<String, MarketScoreCalculator.Dimension>();
    dimensions.forEach(
        (key, value) ->
            credited.put(
                key,
                new MarketScoreCalculator.Dimension(
                    "AVAILABLE".equals(value.status()) && !input.synthetic() ? value.score() : null,
                    value.evidence())));
    var aggregate = MarketScoreCalculator.calculate(credited);
    boolean complete =
        input.universe() != null && input.universe().sufficient(0.95) && !input.synthetic();
    return new ScopedScoreView(
        "MARKET_RULES_V3",
        input.universe() == null ? "UNKNOWN" : input.universe().scope(),
        input.businessDate(),
        complete ? aggregate.score() : null,
        complete ? aggregate.state() : "UNKNOWN",
        "EXPERIMENTAL_NOT_VALIDATED",
        dimensions,
        Map.of("minimumCoverage", 0.95, "leaderFraction", 0.10, "turnoverExpansion", 1.2),
        List.of(
            "指数趋势与涨跌方向采用沪深300代理，不是全部股票等权表现",
            "不同范围的代理分数仅展示，不计入全A总分；缺项不按已有项放大",
            "阈值为透明研究规则，未证明预测效果，不是交易指令"));
  }

  private static ScopedScoreView.Dimension dimension(
      Double score,
      String scope,
      boolean indexProxy,
      ScopedScoreInput input,
      String formula,
      Object evidence) {
    String status =
        score == null
            ? "INSUFFICIENT_DATA"
            : indexProxy || input.universe() != null && scope.equals(input.universe().scope())
                ? "AVAILABLE"
                : "PROXY";
    Map<String, Object> values =
        evidence == null
            ? Map.of("reason", "同日事实、完整历史或有效分母不足")
            : Map.of(
                "observed",
                evidence,
                "reason",
                score == null
                    ? "输入未满足公式或覆盖门槛"
                    : status.equals("PROXY") ? "实际范围与目标范围不同，仅展示原范围分项" : "输入满足本维度定义");
    return new ScopedScoreView.Dimension(score, status, scope, formula, values);
  }

  private static boolean finite(Double v) {
    return v != null && Double.isFinite(v);
  }

  private static boolean positive(Double v) {
    return finite(v) && v > 0;
  }

  private static boolean ratio(Double v) {
    return finite(v) && v >= 0 && v <= 1;
  }

  private static boolean enough(int observed, int total) {
    return total > 0 && observed >= 0 && observed <= total && observed / (double) total >= 0.95;
  }
}

package com.alphascope.market.calculator;

import com.alphascope.market.dto.LeaderScoreEvidence;
import com.alphascope.market.dto.ScoreCoverage;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * 全截面领先样本：历史均成交额排序，复权价格核验，不允许自选名单代替分母。
 *
 * @author chenxuefeng
 */
public final class MarketLeaderCalculator {
  private MarketLeaderCalculator() {}

  /**
   * 核对21个交易日全量截面和同源因子后选择领先样本。
   *
   * @param date 业务日期
   * @param calendar 已核验开市日序列
   * @param snapshots 同一来源的市场截面
   * @param factors 同源已核验复权事件
   * @param benchmark 已保存同日期沪深300原价与因子
   * @return 完整定义的名单和确认比例，缺项保留不足
   */
  public static LeaderScoreEvidence calculate(
      LocalDate date,
      List<LocalDate> calendar,
      Map<LocalDate, JsonNode> snapshots,
      Map<String, NavigableMap<LocalDate, Double>> factors,
      JsonNode benchmark) {
    var dates = calendar.stream().filter(d -> !d.isAfter(date)).distinct().sorted().toList();
    if (dates.size() < 21 || !dates.getLast().equals(date)) {
      return missing(date, 0, 0, "至少需要21个连续交易日");
    }
    dates = dates.subList(dates.size() - 21, dates.size());
    List<Map<String, JsonNode>> daily = new ArrayList<>();
    for (var day : dates) {
      var snapshot = snapshots.get(day);
      if (snapshot == null
          || !"COMPLETE".equals(snapshot.path("metrics").path("status").asText())) {
        return missing(date, 0, 0, "领先样本需要连续完整同源截面");
      }
      Map<String, JsonNode> rows = new HashMap<>();
      for (var row : snapshot.path("raw").path("daily")) {
        rows.put(row.path("code").asText(), row);
      }
      daily.add(rows);
    }
    Map<String, JsonNode> index = new HashMap<>();
    for (var row : benchmark) {
      index.put(row.path("trade_date").asText(), row);
    }
    var first = index.get(dates.getFirst().toString());
    var last = index.get(date.toString());
    if (dates.stream().anyMatch(day -> adjusted(index.get(day.toString())) == null)) {
      return missing(date, 0, 0, "基准21日窗口不完整，不能只比较两个端点");
    }
    Double start = adjusted(first), end = adjusted(last);
    if (start == null || end == null) {
      return missing(date, 0, 0, "同日期基准原价或因子不足");
    }
    double base = end / start - 1;
    var candidates = new ArrayList<Candidate>();
    int trading = 0;
    for (var entry : daily.getLast().entrySet()) {
      String code = entry.getKey();
      if (!MarketBreadthCalculator.stock(code)
          || !"1".equals(entry.getValue().path("tradestatus").asText())) {
        continue;
      }
      trading++;
      var events = factors.get(code);
      if (events == null) {
        continue;
      }
      double amount = 0, sum = 0, initial = 0, current = 0;
      boolean valid = true;
      for (int i = 0; i < dates.size(); i++) {
        var row = daily.get(i).get(code);
        var factor = events.floorEntry(dates.get(i));
        Double close = number(row, "close"), value = number(row, "amount");
        if (row == null
            || !"1".equals(row.path("tradestatus").asText())
            || close == null
            || close <= 0
            || value == null
            || value < 0
            || factor == null
            || !Double.isFinite(factor.getValue())
            || factor.getValue() <= 0) {
          valid = false;
          break;
        }
        current = close * factor.getValue();
        if (i == 0) {
          initial = current;
        } else {
          sum += current;
          amount += value;
        }
      }
      if (valid && amount > 0) {
        candidates.add(
            new Candidate(code, amount / 20, current > sum / 20 && current / initial - 1 > base));
      }
    }
    if (trading <= 0 || candidates.size() / (double) trading < 0.95) {
      return missing(date, trading, candidates.size(), "全市场有效历史覆盖不足95%，不能从少数股票挑领先名单");
    }
    candidates.sort(
        Comparator.comparingDouble(Candidate::amount).reversed().thenComparing(Candidate::code));
    var chosen = candidates.subList(0, Math.max(1, (int) Math.ceil(candidates.size() * 0.10)));
    return new LeaderScoreEvidence(
        date,
        "AVAILABLE",
        new ScoreCoverage("SH_SZ_A", trading, candidates.size(), true),
        chosen.stream().map(Candidate::code).toList(),
        (int) chosen.stream().filter(Candidate::confirmed).count(),
        base,
        "20日均成交额前10%，站上MA20且20日跑赢沪深300；至少95%全截面有效历史");
  }

  private static LeaderScoreEvidence missing(
      LocalDate date, int expected, int observed, String reason) {
    return new LeaderScoreEvidence(
        date,
        "INSUFFICIENT_DATA",
        new ScoreCoverage("SH_SZ_A", expected, observed, false),
        List.of(),
        0,
        null,
        reason);
  }

  private static Double number(JsonNode row, String key) {
    try {
      double value = Double.parseDouble(row.path(key).asText());
      return Double.isFinite(value) ? value : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static Double adjusted(JsonNode row) {
    Double close = number(row, "close"), factor = number(row, "factor");
    return close != null && close > 0 && factor != null && factor > 0 ? close * factor : null;
  }

  /**
   * 完整历史标的的稳定排序项。
   *
   * @author chenxuefeng
   */
  private record Candidate(
      /** 证券代码。 */
      String code,
      /** 20日均成交额。 */
      double amount,
      /** 价格及相对强度确认。 */
      boolean confirmed) {}
}

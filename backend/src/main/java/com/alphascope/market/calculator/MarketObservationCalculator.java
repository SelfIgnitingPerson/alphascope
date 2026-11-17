package com.alphascope.market.calculator;

import com.alphascope.market.dto.MarketObservationInput;
import com.alphascope.market.vo.MarketObservation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * 所有分母来自当日沪深证券清单；技术广度用同源原价乘后复权因子，不使用自选代表市场。
 *
 * @author chenxuefeng
 */
public final class MarketObservationCalculator {
  private MarketObservationCalculator() {}

  /**
   * 市场分项观察实际采用的阈值；随分析证据冻结，不据缺失分项臆造总分。
   *
   * @author chenxuefeng
   */
  public record Parameters(
      /** 允许输出该分项结论的最低有效样本覆盖率，范围0至1。 */
      double minimumCoverage,

      /** 判断相对20日均额放量的倍数阈值。 */
      double turnoverExpansion,

      /** 判断相对20日均额缩量的倍数阈值。 */
      double turnoverContraction,

      /** 均线上方样本比例达到该阈值时判断偏强，范围0至1。 */
      double breadthStrong,

      /** 均线上方样本比例低于该阈值时判断偏弱，范围0至1。 */
      double breadthWeak) {
    public Parameters {
      if (!(minimumCoverage > 0
          && minimumCoverage <= 1
          && turnoverExpansion > 1
          && turnoverContraction > 0
          && turnoverContraction < 1
          && breadthWeak >= 0
          && breadthStrong <= 1
          && breadthStrong > breadthWeak)) {
        throw new IllegalArgumentException("无效市场观察参数");
      }
    }

    public Map<String, Double> values() {
      return Map.of(
          "minimumCoverage",
          minimumCoverage,
          "turnoverExpansion",
          turnoverExpansion,
          "turnoverContraction",
          turnoverContraction,
          "breadthStrong",
          breadthStrong,
          "breadthWeak",
          breadthWeak);
    }
  }

  private static Double n(JsonNode row, String key) {
    try {
      double value = Double.parseDouble(row.path(key).asText());
      return Double.isFinite(value) ? value : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** 单个复权序列没有事件时1是基准，不是缺失因子的默认替代；调用者必须先确认查询成功。 */
  public static NavigableMap<LocalDate, Double> factors(JsonNode rows, LocalDate cutoff) {
    if (!rows.isArray()) {
      throw new IllegalArgumentException("因子原文无效");
    }
    var out = new TreeMap<LocalDate, Double>();
    out.put(LocalDate.MIN, 1d);
    for (var row : rows) {
      var d = LocalDate.parse(row.path("dividOperateDate").asText());
      if (d.isAfter(cutoff)) {
        continue;
      }
      Double value = n(row, "backAdjustFactor");
      if (value == null || value <= 0 || out.putIfAbsent(d, value) != null) {
        throw new IllegalArgumentException("因子事件无效或重复");
      }
    }
    return out;
  }

  public static MarketObservation calculate(
      LocalDate date,
      List<LocalDate> calendar,
      Map<LocalDate, JsonNode> snapshots,
      Map<String, NavigableMap<LocalDate, Double>> factors,
      JsonNode benchmark,
      Parameters p) {
    return calculateScope(
        new MarketObservationInput(date, calendar, snapshots, factors, benchmark, p, "SH_SZ_A"));
  }

  /**
   * 用范围明确的输入计算分项，未知历史不补造。
   *
   * @param input 已核验输入
   * @return 分项观察
   */
  public static MarketObservation calculateScope(MarketObservationInput input) {
    var date = input.date();
    var calendar = input.calendar();
    var snapshots = input.snapshots();
    var factors = input.factors();
    var benchmark = input.benchmark();
    var p = input.parameters();
    var scope = input.scope();
    if (!java.util.Set.of("SH_SZ_A", "FULL_A").contains(scope))
      throw new IllegalArgumentException("市场范围无效");
    var dates = calendar.stream().filter(d -> !d.isAfter(date)).sorted().distinct().toList();
    JsonNode latest = snapshots.get(date);
    var metric = latest == null ? new ObjectMapper().createObjectNode() : latest.path("metrics");
    var trend = trend(date, benchmark);
    Double amount = n(metric, "amount");
    int complete = 0;
    Double average5 = null, average20 = null;
    if (dates.size() >= 20 && dates.getLast().equals(date)) {
      double sum20 = 0, sum5 = 0;
      for (int i = dates.size() - 20; i < dates.size(); i++) {
        var row = snapshots.get(dates.get(i));
        Double v = row == null ? null : n(row.path("metrics"), "amount");
        if (row == null
            || !row.path("metrics").path("status").asText().equals("COMPLETE")
            || v == null
            || v < 0) {
          continue;
        }
        complete++;
        sum20 += v;
        if (i >= dates.size() - 5) {
          sum5 += v;
        }
      }
      if (complete == 20) {
        average20 = sum20 / 20;
        average5 = sum5 / 5;
      }
    }
    Double r5 = amount != null && average5 != null && average5 > 0 ? amount / average5 : null;
    Double r20 = amount != null && average20 != null && average20 > 0 ? amount / average20 : null;
    String volumeJudgment = "证据不足";
    if (r5 != null && r20 != null && trend.dayReturn() != null) {
      boolean up = trend.dayReturn() > 0, down = trend.dayReturn() < 0;
      volumeJudgment =
          r20 >= p.turnoverExpansion()
              ? (down ? "放量下跌" : up ? "放量上涨" : "放量平盘")
              : r20 <= p.turnoverContraction()
                  ? (down ? "缩量下跌" : up ? "缩量上涨" : "缩量平盘")
                  : "成交额接近20日均值";
    }
    var turnover =
        new MarketObservation.Turnover(
            r20 == null ? "INSUFFICIENT_DATA" : "AVAILABLE",
            volumeJudgment,
            amount,
            average5,
            average20,
            r5,
            r20,
            complete,
            "5/20日均值均包含业务日，要求连续20个实际交易日目标范围截面完整；方向采用沪深300当日收益，放量下跌不视为利好。");

    int trading = metric.path("observed").asInt(), suspended = metric.path("suspended").asInt();
    Map<String, List<Double>> series = new HashMap<>();
    Set<String> current = new HashSet<>();
    if (latest != null) {
      for (var row : latest.path("raw").path("daily")) {
        if (MarketBreadthCalculator.stock(row.path("code").asText(), scope)
            && row.path("tradestatus").asText().equals("1")) {
          current.add(row.path("code").asText());
        }
      }
    }
    int valid20 = 0, above20 = 0, valid60 = 0, above60 = 0;
    boolean dailyComplete = !dates.isEmpty() && dates.getLast().equals(date);
    for (var d : dates) {
      var snapshot = snapshots.get(d);
      if (snapshot == null
          || !snapshot.path("metrics").path("status").asText().equals("COMPLETE")) {
        dailyComplete = false;
      }
      if (snapshot == null) {
        continue;
      }
      Set<String> seen = new HashSet<>();
      for (var row : snapshot.path("raw").path("daily")) {
        String code = row.path("code").asText();
        if (!current.contains(code) || !row.path("tradestatus").asText().equals("1")) {
          continue;
        }
        if (!seen.add(code) || !row.path("date").asText().equals(d.toString())) {
          throw new IllegalArgumentException("市场日线重复或日期不匹配");
        }
        var f = factors.get(code);
        Double close = n(row, "close");
        if (f == null || close == null || close <= 0) {
          continue;
        }
        double adjusted = close * f.floorEntry(d).getValue();
        if (!Double.isFinite(adjusted) || adjusted <= 0) {
          throw new IllegalArgumentException("无效复权收盘");
        }
        series.computeIfAbsent(code, k -> new ArrayList<>()).add(adjusted);
      }
    }
    if (dailyComplete && current.size() == trading) {
      for (var code : current) {
        var values = series.getOrDefault(code, List.of());
        for (int period : List.of(20, 60)) {
          if (values.size() < period) {
            continue;
          }
          double mean =
              values.subList(values.size() - period, values.size()).stream()
                  .mapToDouble(Double::doubleValue)
                  .average()
                  .orElseThrow();
          if (period == 20) {
            valid20++;
            if (values.getLast() > mean) {
              above20++;
            }
          } else {
            valid60++;
            if (values.getLast() > mean) {
              above60++;
            }
          }
        }
      }
    }
    Double coverage20 = trading > 0 ? valid20 / (double) trading : null,
        coverage60 = trading > 0 ? valid60 / (double) trading : null;
    Double ratio20 =
        coverage20 != null && coverage20 >= p.minimumCoverage() ? above20 / (double) valid20 : null;
    Double ratio60 =
        coverage60 != null && coverage60 >= p.minimumCoverage() ? above60 / (double) valid60 : null;
    Double advancing = trading > 0 ? n(metric, "advancers") : null;
    if (advancing != null) {
      advancing /= trading;
    }
    String breadthJudgment =
        ratio20 == null
            ? "证据不足"
            : ratio20 >= p.breadthStrong()
                ? "多数有效样本高于MA20"
                : ratio20 <= p.breadthWeak() ? "多数有效样本低于MA20" : "MA20上下分布较均衡";
    var breadth =
        new MarketObservation.Breadth(
            ratio20 == null ? "INSUFFICIENT_DATA" : ratio60 == null ? "PARTIAL" : "AVAILABLE",
            breadthJudgment,
            trading,
            suspended,
            valid20,
            above20,
            coverage20,
            ratio20,
            valid60,
            above60,
            coverage60,
            ratio60,
            advancing,
            (scope.equals("FULL_A") ? "分母为当日三所实际交易A股；" : "分母为当日实际交易沪深A股；")
                + "停牌单列。MA取最近20/60个有效交易收盘，同源后复权；历史/因子不足不填0，低覆盖不形成广度判断。");
    String status =
        trend.status().equals("AVAILABLE")
                && turnover.status().equals("AVAILABLE")
                && breadth.status().equals("AVAILABLE")
            ? "AVAILABLE"
            : "PARTIAL";
    return new MarketObservation(
        scope.equals("FULL_A") ? "FULL_A_OBSERVATION_V1" : "SH_SZ_OBSERVATION_V1",
        date,
        scope,
        status,
        trend,
        turnover,
        breadth,
        p.values(),
        List.of(
            scope.equals("FULL_A") ? "三所同日观察；历史广度和五维总评级仍分别验收。" : "不含北交所；独立沪深观察不替代完整全A五维评级。",
            "分项阈值为研究观察线，未完成历史效果验证；不输出合成总分。",
            "免费历史行情为当前可获得版本，不构成严格时点回测；新股、长期停牌等可能缺少均线窗口。"));
  }

  private static MarketObservation.Trend trend(LocalDate date, JsonNode rows) {
    var values = new TreeMap<LocalDate, Double>();
    if (rows.isArray()) {
      for (var row : rows) {
        var d = LocalDate.parse(row.path("trade_date").asText());
        Double close = n(row, "close"), factor = n(row, "factor");
        if (!d.isAfter(date)
            && !row.path("is_synthetic").asBoolean()
            && close != null
            && close > 0
            && factor != null
            && factor > 0) {
          values.put(d, close * factor);
        }
      }
    }
    if (values.size() < 61 || !values.lastKey().equals(date)) {
      return new MarketObservation.Trend(
          "INSUFFICIENT_DATA", "指数历史不足", null, null, null, null, null, "需要截至业务日的61个有效收盘以核对均线斜率。");
    }
    var v = new ArrayList<>(values.values());
    int size = v.size();
    double factorAnchor = 1;
    // 数值用于比较与展示时以业务日实际价格为锚，指数因子一般为1。
    for (var row : rows) {
      if (row.path("trade_date").asText().equals(date.toString())
          && n(row, "factor") != null
          && n(row, "factor") > 0) {
        factorAnchor = n(row, "factor");
      }
    }
    double current = v.getLast(),
        ma20 =
            v.subList(size - 20, size).stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow(),
        ma60 =
            v.subList(size - 60, size).stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow(),
        previous20 =
            v.subList(size - 21, size - 1).stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();
    String judgment =
        current > ma20 && current > ma60 && ma20 > previous20
            ? "趋势偏强"
            : current < ma20 && current < ma60 && ma20 < previous20 ? "趋势偏弱" : "趋势信号不一致";
    return new MarketObservation.Trend(
        "AVAILABLE",
        judgment,
        current / factorAnchor,
        ma20 / factorAnchor,
        ma60 / factorAnchor,
        (ma20 - previous20) / factorAnchor,
        current / v.get(size - 2) - 1,
        "基准沪深300，比较收盘与MA20/MA60及MA20较前一交易日的变化。");
  }
}

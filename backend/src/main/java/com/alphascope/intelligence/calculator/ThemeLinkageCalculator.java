package com.alphascope.intelligence.calculator;

import com.alphascope.intelligence.vo.ThemeResearch;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 留一法同业组合、交易日精确对齐、20/60日Pearson及控制市场线性影响后的相关性。
 *
 * @author chenxuefeng
 */
public final class ThemeLinkageCalculator {
  private static final ObjectMapper JSON = new ObjectMapper();

  private ThemeLinkageCalculator() {}

  private static Double n(JsonNode row, String key) {
    var value = row.path(key);
    try {
      double v = value.isNumber() ? value.doubleValue() : Double.parseDouble(value.asText());
      return Double.isFinite(v) ? v : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static JsonNode object(JsonNode node) {
    try {
      return node.isTextual() ? JSON.readTree(node.asText()) : node;
    } catch (IOException e) {
      return JSON.nullNode();
    }
  }

  private static LocalDateTime time(String value) {
    try {
      return OffsetDateTime.parse(value.replace(' ', 'T'))
          .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
          .toLocalDateTime();
    } catch (RuntimeException e) {
      return LocalDateTime.parse(value.replace(' ', 'T'));
    }
  }

  private static NavigableMap<LocalDate, Double> prices(JsonNode rows, LocalDate cutoff) {
    var out = new TreeMap<LocalDate, Double>();
    String source = null;
    if (rows.isArray()) {
      for (var row : rows) {
        try {
          var d = LocalDate.parse(row.path("trade_date").asText());
          if (d.isAfter(cutoff) || row.path("is_synthetic").asBoolean()) {
            continue;
          }
          var close = n(row, "close");
          var factor = n(row, "factor");
          if (close == null || factor == null || close <= 0 || factor <= 0) {
            continue;
          }
          String current = row.path("source").asText();
          if (current.isBlank() || current.equals("DEMO")) {
            continue;
          }
          if (source == null) {
            source = current;
          }
          if (!source.equals(current)) {
            return new TreeMap<>();
          } // 不拼接不同来源或不同复权基准。
          if (out.putIfAbsent(d, close * factor) != null) {
            return new TreeMap<>();
          }
        } catch (RuntimeException ignored) {
          /* 不补造无效行。 */
        }
      }
    }
    return out;
  }

  private static double[] returns(NavigableMap<LocalDate, Double> prices, List<LocalDate> dates) {
    if (dates.size() < 2) {
      return null;
    }
    var out = new double[dates.size() - 1];
    for (int i = 1; i < dates.size(); i++) {
      Double a = prices.get(dates.get(i - 1)), b = prices.get(dates.get(i));
      if (a == null || b == null) {
        return null;
      }
      out[i - 1] = b / a - 1;
      if (!Double.isFinite(out[i - 1])) {
        return null;
      }
    }
    return out;
  }

  public static Double correlation(double[] x, double[] y) {
    if (x == null || y == null || x.length != y.length || x.length < 2) {
      return null;
    }
    double mx = 0, my = 0;
    for (int i = 0; i < x.length; i++) {
      if (!Double.isFinite(x[i]) || !Double.isFinite(y[i])) {
        return null;
      }
      mx += x[i];
      my += y[i];
    }
    mx /= x.length;
    my /= y.length;
    double xx = 0, yy = 0, xy = 0;
    for (int i = 0; i < x.length; i++) {
      double a = x[i] - mx, b = y[i] - my;
      xx += a * a;
      yy += b * b;
      xy += a * b;
    }
    if (xx < 1e-20 || yy < 1e-20) {
      return null;
    }
    return Math.max(-1, Math.min(1, xy / Math.sqrt(xx * yy)));
  }

  /** OLS残差相关性只控制所选市场因子的线性影响，不构成因果证据。 */
  public static Double adjustedCorrelation(double[] x, double[] y, double[] market) {
    if (x == null
        || y == null
        || market == null
        || x.length != y.length
        || x.length != market.length
        || x.length < 3) {
      return null;
    }
    double mx = Arrays.stream(x).average().orElse(0),
        my = Arrays.stream(y).average().orElse(0),
        mm = Arrays.stream(market).average().orElse(0);
    double vm = 0, cx = 0, cy = 0;
    for (int i = 0; i < x.length; i++) {
      if (!Double.isFinite(x[i]) || !Double.isFinite(y[i]) || !Double.isFinite(market[i])) {
        return null;
      }
      double m = market[i] - mm;
      vm += m * m;
      cx += (x[i] - mx) * m;
      cy += (y[i] - my) * m;
    }
    if (vm < 1e-20) {
      return null;
    }
    var a = new double[x.length];
    var b = new double[x.length];
    for (int i = 0; i < x.length; i++) {
      a[i] = x[i] - mx - cx / vm * (market[i] - mm);
      b[i] = y[i] - my - cy / vm * (market[i] - mm);
    }
    return correlation(a, b);
  }

  private static double compound(double[] returns) {
    double result = 1;
    for (double v : returns) {
      result *= 1 + v;
    }
    return result - 1;
  }

  private static ThemeResearch.Window window(
      int lookback,
      LocalDate date,
      LocalDate available,
      List<LocalDate> calendar,
      NavigableMap<LocalDate, Double> stock,
      Map<String, NavigableMap<LocalDate, Double>> peers,
      NavigableMap<LocalDate, Double> market,
      int expected) {
    var empty =
        new ThemeResearch.Window(
            lookback,
            0,
            expected,
            List.of(),
            expected == 0 ? null : 0d,
            null,
            null,
            null,
            null,
            null,
            null,
            "INSUFFICIENT_DATA");
    if (calendar.size() < lookback + 1) {
      return empty;
    }
    var dates = calendar.subList(calendar.size() - lookback - 1, calendar.size());
    if (!dates.getLast().equals(date) || dates.get(1).isBefore(available)) {
      return empty;
    }
    var x = returns(stock, dates);
    if (x == null) {
      return empty;
    }
    List<String> valid = new ArrayList<>();
    var y = new double[lookback];
    for (var e : peers.entrySet()) {
      var series = returns(e.getValue(), dates);
      if (series == null) {
        continue;
      }
      valid.add(e.getKey());
      for (int i = 0; i < lookback; i++) {
        y[i] += series[i];
      }
    }
    Double coverage = expected == 0 ? null : valid.size() / (double) expected;
    if (valid.size() < 2 || coverage < .8) {
      return new ThemeResearch.Window(
          lookback,
          0,
          expected,
          valid,
          coverage,
          null,
          null,
          null,
          null,
          null,
          null,
          "INSUFFICIENT_DATA");
    }
    for (int i = 0; i < lookback; i++) {
      y[i] /= valid.size();
    }
    Double rho = correlation(x, y), adjusted = adjustedCorrelation(x, y, returns(market, dates));
    double stockReturn = compound(x), peerReturn = compound(y);
    return new ThemeResearch.Window(
        lookback,
        lookback,
        expected,
        valid,
        coverage,
        rho,
        adjusted,
        stockReturn,
        peerReturn,
        stockReturn - peerReturn,
        y[lookback - 1],
        rho == null ? "PARTIAL" : "AVAILABLE");
  }

  public static List<ThemeResearch> calculate(JsonNode input, LocalDate date, boolean demo) {
    if (demo || !input.path("themeContexts").isArray()) {
      return List.of();
    }
    List<ThemeResearch> out = new ArrayList<>();
    var market = prices(input.path("benchmark"), date);
    for (var stock : input.path("watchlist")) {
      for (var theme : input.path("themeContexts")) {
        String symbol = stock.path("symbol").asText();
        var cohort = theme.path("cohort");
        if (!cohort.path("scope").asText().equals("DISCLOSED_RESEARCH_SAMPLE")) {
          continue;
        }
        LocalDate available;
        try {
          var at = time(cohort.path("available_at").asText());
          if (at.isAfter(date.atTime(16, 10))) {
            continue;
          }
          available = at.toLocalDate();
        } catch (RuntimeException e) {
          continue;
        }
        List<String> members = new ArrayList<>();
        List<ThemeResearch.Member> memberDetails = new ArrayList<>();
        Map<String, NavigableMap<LocalDate, Double>> peers = new LinkedHashMap<>();
        for (var member : theme.path("members")) {
          String peer = member.path("symbol").asText();
          members.add(peer);
          memberDetails.add(new ThemeResearch.Member(peer, member.path("name").asText(peer)));
          if (!peer.equals(symbol)) {
            peers.put(peer, prices(member.path("history"), date));
          }
        }
        if (!members.contains(symbol)) {
          continue;
        }
        int expected = Math.max(peers.size(), cohort.path("expected_members").asInt() - 1);
        var dates = new TreeSet<LocalDate>();
        for (var d : theme.path("expectedDates")) {
          try {
            var parsed = LocalDate.parse(d.asText());
            if (!parsed.isAfter(date)) {
              dates.add(parsed);
            }
          } catch (RuntimeException ignored) {
          }
        }
        var calendar = new ArrayList<>(dates);
        var own = prices(stock.path("history"), date);
        var w20 = window(20, date, available, calendar, own, peers, market, expected);
        var w60 = window(60, date, available, calendar, own, peers, market, expected);
        var definition = object(cohort.path("definition_json"));
        var source =
            new ThemeResearch.Source(
                cohort.path("source_title").asText(),
                cohort.path("source_url").asText(),
                definition.path("sourcePages").asText(),
                cohort.path("available_at").asText(),
                cohort.path("content_hash").asText(),
                definition.path("definition").asText());
        List<ThemeResearch.Day> recent = new ArrayList<>();
        for (var row : theme.path("scores")) {
          try {
            var d = LocalDate.parse(row.path("trade_date").asText());
            if (d.isAfter(date)
                || !dates.contains(d)
                || !row.path("model_version").asText().equals("EQUAL_RETURN_V1")) {
              continue;
            }
            var e = object(row.path("evidence_json"));
            // 只展示同一披露样本版本的评分，不能把旧单成员记录拼进新样本。
            if (e.path("cohortId").asLong() != cohort.path("id").asLong()) {
              continue;
            }
            recent.add(
                new ThemeResearch.Day(
                    d,
                    n(row, "day_return"),
                    n(row, "breadth"),
                    n(row, "coverage"),
                    e.path("validMembers").asInt(),
                    e.path("totalMembers").asInt()));
          } catch (RuntimeException ignored) {
          }
        }
        recent.sort(Comparator.comparing(ThemeResearch.Day::date));
        if (recent.size() > 3) {
          recent = new ArrayList<>(recent.subList(recent.size() - 3, recent.size()));
        }
        String status =
            w20.status().equals("AVAILABLE") && w60.status().equals("AVAILABLE")
                ? "AVAILABLE"
                : w20.stockReturn() != null ? "PARTIAL" : "INSUFFICIENT_DATA";
        String observation =
            w20.stockReturn() == null
                ? "同业联动样本或历史不足，暂不能判断。"
                : "个股20日收益"
                    + ReportAnalysisBuilder.pct(w20.stockReturn())
                    + "，剔除自身后的同业每日等权组合"
                    + ReportAnalysisBuilder.pct(w20.peerReturn())
                    + "，相差"
                    + ReportAnalysisBuilder.fmt(w20.excessReturn() * 100)
                    + "个百分点；20日相关系数"
                    + ReportAnalysisBuilder.fmt(w20.correlation())
                    + "。";
        if (w60.stockReturn() != null) {
          observation +=
              "60日个股收益"
                  + ReportAnalysisBuilder.pct(w60.stockReturn())
                  + "、同业组合"
                  + ReportAnalysisBuilder.pct(w60.peerReturn())
                  + "，相差"
                  + ReportAnalysisBuilder.fmt(w60.excessReturn() * 100)
                  + "个百分点。";
        }
        boolean consecutiveWeakSample =
            recent.size() == 3
                && calendar.size() >= 3
                && recent.stream()
                    .map(ThemeResearch.Day::date)
                    .toList()
                    .equals(calendar.subList(calendar.size() - 3, calendar.size()))
                && recent.stream()
                    .allMatch(
                        day ->
                            day.coverage() != null
                                && day.coverage() >= .8
                                && day.validMembers() >= 2
                                && day.breadth() != null
                                && day.breadth() == 0);
        if (consecutiveWeakSample) {
          observation += "最近3个交易日样本高于MA20的比例均为0%，显示该样本共同偏弱；分化规则未触发不等于主题强势。";
        }
        List<String> hypotheses = new ArrayList<>(), checks = new ArrayList<>();
        if (w20.stockReturn() != null) {
          if (w20.excessReturn() < -.05) {
            hypotheses.add("个股在20日区间跑输披露同业样本，优先核查公司自身经营兑现及估值变化；这是待验证解释。");
            checks.add("继续记录后续3个交易日相对同业的收益差，并结合最新利润、毛利率和现金流复核个股原因。");
          } else {
            hypotheses.add("个股与同业样本的20日差距未超过本研究采用的5个百分点偏弱观察线；仍需结合绝对趋势。");
            checks.add("观察未来3个交易日个股与同业是否同时修复，避免把行业共同波动全部归因于单家公司。");
          }
          if (w60.correlation() != null && w60.correlation() >= .6) {
            hypotheses.add("60日同向相关较高，可把共同需求、政策或市场资金变化列入核查清单；不能由此确认共同订单或未来同涨同跌。");
          }
          if (w60.excessReturn() != null && w60.excessReturn() < -.05) {
            hypotheses.add("60日区间明显落后同业样本；20日同步下跌或短期相对抗跌不代表中期相对表现已经修复，需验证公司因素。 ");
            checks.add("同时跟踪20日与60日相对同业收益差，核对中期落后是否收窄；对照公司财报与披露，区分样本共同压力和个股经营问题。");
          }
          if (w60.marketAdjustedCorrelation() != null) {
            checks.add(
                "对照60日原始相关系数"
                    + ReportAnalysisBuilder.fmt(w60.correlation())
                    + "与控制沪深300线性影响后的"
                    + ReportAnalysisBuilder.fmt(w60.marketAdjustedCorrelation())
                    + "，核对联动是否主要来自共同市场波动。");
          }
        }
        out.add(
            new ThemeResearch(
                symbol,
                theme.path("code").asText(),
                cohort.path("title").asText(),
                cohort.path("scope").asText(),
                status,
                source,
                memberDetails,
                recent,
                w20,
                w60,
                observation,
                hypotheses,
                checks,
                List.of(
                    "仅为有披露依据的固定研究样本，行业覆盖率未知；样本覆盖率以本名单为分母。",
                    "样本于本次研究时选定，即使原披露早于业务日，仍可能存在事后选样偏差，不构成严格历史策略回测。",
                    "同业组合排除个股自身；每日等权，收益由同源原价与复权因子计算；缺失日期不填0。",
                    "相关性不代表因果、领先或收益保证；0.6相关线与5个百分点观察线为启发式研究阈值，未验证预测效果。",
                    "应用户要求以量子纠缠作联动关系的类比，实际采用经典统计方法，不声称股票存在可预测的物理量子纠缠。")));
      }
    }
    return out;
  }
}

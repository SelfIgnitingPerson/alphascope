package com.alphascope.intelligence.calculator;

import com.alphascope.common.config.ResearchVersion;
import com.alphascope.fundamental.vo.ForecastView;
import com.alphascope.intelligence.builder.PriceVolumeAnalysisBuilder;
import com.alphascope.intelligence.vo.ReportAnalysis;
import com.alphascope.intelligence.vo.ResearchAdvice;
import com.alphascope.market.vo.MarketAcquisition;
import com.alphascope.market.vo.MarketObservation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 解释冻结事实，不预测价格、不产生交易目标。缺少证据返回空值，不能用0补齐。
 *
 * @author chenxuefeng
 */
public final class ReportAnalysisBuilder {
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  private static final Map<String, String> RULES =
      Map.of(
          "TREND_BREAK_20",
          "20日趋势转弱",
          "TREND_BREAK_60",
          "60日趋势转弱",
          "VOLUME_SELL",
          "放量下跌",
          "NEW_LOW_20",
          "20日收盘新低",
          "RELATIVE_WEAK",
          "相对强度走弱",
          "PROFIT_SLOWDOWN",
          "利润增速放缓",
          "MARGIN_DECLINE",
          "毛利率下降",
          "CASHFLOW_WARNING",
          "现金流恶化",
          "POSITION_CONCENTRATION",
          "持仓集中度偏高",
          "INDUSTRY_DIVERGENCE",
          "主题走势分化");
  private static final Map<String, List<String>> REQUIRED =
      Map.of(
          "TREND_BREAK_20",
          List.of("close", "previousClose", "ma20", "previousMa20"),
          "TREND_BREAK_60",
          List.of("close", "ma60", "previousMa60"),
          "VOLUME_SELL",
          List.of("return", "volumeRatio"),
          "NEW_LOW_20",
          List.of("close", "previous20Low"),
          "RELATIVE_WEAK",
          List.of("rs20", "previousRs20"),
          "PROFIT_SLOWDOWN",
          List.of("revenueYoy", "profitYoy"),
          "MARGIN_DECLINE",
          List.of("grossMargin", "previousYearGrossMargin"),
          "CASHFLOW_WARNING",
          List.of("cashflow", "previousYearCashflow", "twoYearsAgoCashflow"),
          "POSITION_CONCENTRATION",
          List.of("positionWeight"),
          "INDUSTRY_DIVERGENCE",
          List.of("themeReturn", "themeBreadth", "previousThemeBreadth", "twoDaysAgoThemeBreadth"));
  private static final Map<String, String> LABELS =
      Map.ofEntries(
          Map.entry("close", "收盘价"),
          Map.entry("previousClose", "前收盘（同锚点复权）"),
          Map.entry("ma20", "20日均线"),
          Map.entry("previousMa20", "前20日均线"),
          Map.entry("ma60", "60日均线"),
          Map.entry("previousMa60", "前60日均线"),
          Map.entry("previous20Low", "前20日收盘低点"),
          Map.entry("return", "当日复权收益"),
          Map.entry("volumeRatio", "量比"),
          Map.entry("rs20", "20日相对强度"),
          Map.entry("previousRs20", "前期相对强度"),
          Map.entry("revenueYoy", "营收同比"),
          Map.entry("profitYoy", "利润同比"),
          Map.entry("grossMargin", "毛利率"),
          Map.entry("previousYearGrossMargin", "上年同期毛利率"),
          Map.entry("cashflow", "经营现金流"),
          Map.entry("previousYearCashflow", "上年同期现金流"),
          Map.entry("twoYearsAgoCashflow", "前年同期现金流"),
          Map.entry("positionWeight", "持仓占比"),
          Map.entry("themeReturn", "主题收益"),
          Map.entry("themeBreadth", "主题广度"),
          Map.entry("previousThemeBreadth", "前期主题广度"),
          Map.entry("twoDaysAgoThemeBreadth", "更早一期主题广度"));
  private static final Set<String> PERCENT =
      Set.of(
          "return",
          "rs20",
          "previousRs20",
          "revenueYoy",
          "profitYoy",
          "grossMargin",
          "previousYearGrossMargin",
          "positionWeight",
          "themeReturn",
          "themeBreadth",
          "previousThemeBreadth",
          "twoDaysAgoThemeBreadth");

  private ReportAnalysisBuilder() {}

  public static String ruleName(String code) {
    return RULES.getOrDefault(code, code);
  }

  private static Double number(JsonNode row, String key) {
    var n = row.path(key);
    try {
      double value =
          n.isNumber()
              ? n.doubleValue()
              : n.isTextual() ? Double.parseDouble(n.asText()) : Double.NaN;
      return Double.isFinite(value) ? value : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static LocalDate date(JsonNode row) {
    try {
      return LocalDate.parse(row.path("trade_date").asText());
    } catch (Exception e) {
      return null;
    }
  }

  private static List<JsonNode> history(JsonNode rows, LocalDate cutoff) {
    List<JsonNode> out = new ArrayList<>();
    if (rows.isArray()) {
      for (var row : rows) {
        var d = date(row);
        if (d != null && !d.isAfter(cutoff)) {
          out.add(row);
        }
      }
    }
    out.sort(Comparator.comparing(ReportAnalysisBuilder::date));
    return out;
  }

  private static Double returnBetween(JsonNode first, JsonNode last) {
    if (first == null || last == null) {
      return null;
    }
    Double p = number(first, "close"),
        q = number(last, "close"),
        a = number(first, "factor"),
        b = number(last, "factor");
    if (p == null || q == null || a == null || b == null || p <= 0 || q <= 0 || a <= 0 || b <= 0) {
      return null;
    }
    double value = q * b / (p * a) - 1;
    return Double.isFinite(value) ? value : null;
  }

  private static Double observedReturn(List<JsonNode> rows, int n) {
    return rows.size() > n ? returnBetween(rows.get(rows.size() - 1 - n), rows.getLast()) : null;
  }

  private static Double periodReturn(List<JsonNode> rows, LocalDate from) {
    if (rows.isEmpty() || date(rows.getLast()).isBefore(from)) {
      return null;
    }
    JsonNode prior = null;
    for (var row : rows) {
      if (date(row).isBefore(from)) {
        prior = row;
      }
    }
    return returnBetween(prior, rows.getLast());
  }

  private static Double distance(Double price, Double reference) {
    return price == null || reference == null || reference <= 0 ? null : price / reference - 1;
  }

  public static String fmt(Double value) {
    return value == null ? "暂无" : String.format(Locale.ROOT, "%.2f", value);
  }

  public static String pct(Double value) {
    return value == null ? "暂无" : fmt(value * 100) + "%";
  }

  private static JsonNode evidence(JsonNode signal) {
    JsonNode evidence = signal.path("evidence_json");
    if (evidence.isTextual()) {
      try {
        evidence = JSON.readTree(evidence.asText());
      } catch (Exception e) {
        return JSON.nullNode();
      }
    }
    return evidence;
  }

  private static JsonNode values(JsonNode signal) {
    return evidence(signal).path("values");
  }

  public static ReportAnalysis build(
      JsonNode input, String type, LocalDate businessDate, boolean demo) {
    List<ReportAnalysis.Stock> stocks = new ArrayList<>();
    List<ReportAnalysis.Assessment> assessments = new ArrayList<>();
    List<ReportAnalysis.Check> checks = new ArrayList<>();
    List<ReportAnalysis.FinancialFact> financials = new ArrayList<>();
    Set<String> missing = new LinkedHashSet<>();
    LocalDate periodStart =
        type.equals("WEEKLY") ? businessDate.with(DayOfWeek.MONDAY) : businessDate;
    var benchmark = history(input.path("benchmark"), businessDate);
    Double benchmarkReturn =
        benchmark.isEmpty() || !businessDate.equals(date(benchmark.getLast()))
            ? null
            : periodReturn(benchmark, periodStart);
    JsonNode inputs = input.path("watchlist");
    if (inputs.isArray()) {
      for (var stock : inputs) {
        // 旧版每只股有多个signals/financials分片而无行情，不能从当前数据库补回伪造快照。
        if (!stock.path("history").isArray()) {
          continue;
        }
        String symbol = stock.path("symbol").asText(), name = stock.path("name").asText(symbol);
        var bars = history(stock.path("history"), businessDate);
        boolean suspended = stock.path("suspended").asBoolean();
        JsonNode last = bars.isEmpty() ? JSON.nullNode() : bars.getLast();
        LocalDate asOf = date(last);
        boolean current =
            businessDate.equals(asOf)
                && !suspended
                && number(last, "close") != null
                && number(last, "close") > 0
                && number(last, "factor") != null
                && number(last, "factor") > 0;
        String status =
            suspended ? "SUSPENDED" : bars.isEmpty() ? "MISSING" : current ? "TRADING" : "STALE";
        JsonNode indicator = JSON.nullNode();
        if (stock.path("indicators").isArray()) {
          for (var row : stock.path("indicators")) {
            if (businessDate.equals(date(row))) {
              indicator = row;
            }
          }
        }
        Double close = number(last, "close"),
            ma20 = current ? number(indicator, "ma20") : null,
            ma60 = current ? number(indicator, "ma60") : null,
            volume = current ? number(indicator, "vol_ratio") : null,
            rs = current ? number(indicator, "rs20") : null;
        Double low = null, anchor = number(last, "factor");
        if (current && anchor != null && anchor > 0 && bars.size() >= 21) {
          for (int i = bars.size() - 21; i < bars.size() - 1; i++) {
            Double p = number(bars.get(i), "close"), factor = number(bars.get(i), "factor");
            if (p == null || factor == null || factor <= 0) {
              low = null;
              break;
            }
            double adjusted = p * factor / anchor;
            low = low == null ? adjusted : Math.min(low, adjusted);
          }
        }
        int triggered = 0, reviewed = 0, insufficient = 0;
        if (stock.path("signals").isArray()) {
          for (var signal : stock.path("signals")) {
            var d = date(signal);
            if (d == null || d.isBefore(periodStart) || d.isAfter(businessDate)) {
              continue;
            }
            String code = signal.path("rule_code").asText(), state = signal.path("status").asText();
            if (!RULES.containsKey(code)) {
              continue;
            }
            // 停牌、陈旧或演示输入不得把遗留规则当作当前可用风险判断。
            if ((!current || demo) && !state.equals("NOT_APPLICABLE")) {
              state = "INSUFFICIENT_DATA";
            }
            if (state.equals("TRIGGERED")) {
              triggered++;
            }
            if (state.equals("INSUFFICIENT_DATA")) {
              insufficient++;
            } else if (state.equals("TRIGGERED") || state.equals("NOT_TRIGGERED")) {
              reviewed++;
            }
            List<ReportAnalysis.Metric> metrics = new ArrayList<>();
            var vals = values(signal);
            var fields = vals.fieldNames();
            while (fields.hasNext()) {
              String key = fields.next();
              Double n = number(vals, key);
              if (n != null) {
                metrics.add(
                    new ReportAnalysis.Metric(
                        LABELS.getOrDefault(key, key),
                        n,
                        PERCENT.contains(key)
                            ? "PERCENT"
                            : key.equals("volumeRatio") ? "TIMES" : "CNY"));
              }
            }
            assessments.add(
                new ReportAnalysis.Assessment(
                    symbol,
                    name,
                    d,
                    code,
                    ruleName(code),
                    state,
                    signal.path("severity").asText(),
                    metrics,
                    state.equals("INSUFFICIENT_DATA")
                        ? demo
                            ? List.of("演示输入不用于真实规则判定")
                            : !current
                                ? List.of("业务日期有效行情")
                                : REQUIRED.getOrDefault(code, List.of()).stream()
                                    .filter(key -> number(vals, key) == null)
                                    .map(key -> LABELS.getOrDefault(key, key))
                                    .toList()
                        : List.of(),
                    evidence(signal).path("reason").asText("")));
          }
        }
        String trend =
            ma20 == null || ma60 == null || close == null
                ? "均线证据不足"
                : close < ma20 && close < ma60
                    ? "低于20日和60日均线"
                    : close >= ma20 && close >= ma60 ? "不低于20日和60日均线" : "长短均线位置分化";
        stocks.add(
            new ReportAnalysis.Stock(
                symbol,
                name,
                asOf,
                status,
                last.path("source").asText("UNKNOWN"),
                last.path("batch_id").asText(""),
                close,
                current ? observedReturn(bars, 1) : null,
                current ? periodReturn(bars, periodStart) : null,
                current ? observedReturn(bars, 5) : null,
                current ? observedReturn(bars, 20) : null,
                ma20,
                ma60,
                distance(close, ma20),
                distance(close, ma60),
                volume,
                rs,
                low,
                trend,
                triggered,
                reviewed,
                insufficient));
        if (current) {
          if (ma20 != null) {
            checks.add(
                new ReportAnalysis.Check(
                    symbol,
                    "核对下一期趋势",
                    "比较新收盘与更新后的20日均线；本期均线为"
                        + fmt(ma20)
                        + "元，当前偏离"
                        + pct(distance(close, ma20))
                        + "，不能把该数值当成固定买卖价。"));
          }
          if (volume != null) {
            checks.add(
                new ReportAnalysis.Check(
                    symbol,
                    "核对量价变化",
                    "本期量比"
                        + fmt(volume)
                        + "倍、当日复权收益"
                        + pct(observedReturn(bars, 1))
                        + "；下一期核对成交量与涨跌方向，并重新评估放量下跌规则。"));
          }
          if (rs != null) {
            checks.add(
                new ReportAnalysis.Check(
                    symbol, "核对相对强弱", "本期20日相对沪深300为" + pct(rs) + "，下一期核对是否继续变化。"));
          }
        } else {
          checks.add(
              new ReportAnalysis.Check(
                  symbol,
                  "先核对行情日期",
                  suspended ? "本期供应商证实停牌，展示的是最近一次有K线的日期。" : "冻结输入缺少业务日有效行情，先核对数据缺口。"));
        }
        JsonNode financial = latestFinancial(stock.path("financials"), businessDate);
        if (financial != null) {
          financials.add(
              new ReportAnalysis.FinancialFact(
                  symbol,
                  name,
                  financial.path("period_end").asText(),
                  financial.path("report_type").asText(),
                  financial.path("published_at").asText(),
                  financial.path("available_at").isNull()
                          || financial.path("available_at").isMissingNode()
                      ? financial.path("published_at").asText()
                      : financial.path("available_at").asText(),
                  financial.path("source").asText("UNKNOWN"),
                  number(financial, "revenue"),
                  number(financial, "net_profit"),
                  number(financial, "operating_cashflow"),
                  number(financial, "gross_margin"),
                  number(financial, "revenue_yoy"),
                  number(financial, "profit_yoy")));
        } else {
          missing.add(name + "：未保存可用财报，利润、毛利率和现金流规则未覆盖");
          checks.add(
              new ReportAnalysis.Check(
                  symbol, "补齐财报证据", "自动采集后仍无截至业务日可用的财报，核对数据源及披露时间；只接受同口径累计报表和同期比较，不将未评估解读为风险消失。"));
        }
        if (insufficient > 0) {
          missing.add(name + "：" + insufficient + "项规则记录证据不足，详见规则明细");
        }
        if (assessments.stream()
            .anyMatch(
                a ->
                    a.symbol().equals(symbol)
                        && a.ruleCode().equals("INDUSTRY_DIVERGENCE")
                        && a.status().equals("INSUFFICIENT_DATA"))) {
          missing.add(name + "：主题分化至少需要2只有效成员及连续3个交易日的记录；详见规则中的成员数与历史进度，成分历史不能倒填。");
        }
      }
    }
    int triggers = stocks.stream().mapToInt(ReportAnalysis.Stock::triggered).sum(),
        covered = stocks.stream().mapToInt(ReportAnalysis.Stock::reviewed).sum();
    String headline =
        stocks.isEmpty()
            ? "该修订未冻结行情，无法生成有依据的个股分析。"
            : (demo ? "演示输入；" : "")
                + stocks.size()
                + "只关注标的，"
                + covered
                + "项规则记录可评估，"
                + triggers
                + "项已触发。";
    var snapshot = input.path("market").path("snapshot");
    String marketStatus = snapshot.path("status").asText("NOT_CHECKED");
    boolean observedMarket =
        !demo
            && Set.of("COMPLETE", "PARTIAL").contains(marketStatus)
            && snapshot.path("universeScope").asText().equals("SH_SZ_A");
    var marketFact =
        new ReportAnalysis.MarketFact(
            marketStatus,
            snapshot.path("universeScope").asText("UNKNOWN"),
            snapshot.path("source").asText("UNKNOWN"),
            observedMarket ? number(snapshot, "amount") : null,
            observedMarket ? integer(snapshot, "advancers") : null,
            observedMarket ? integer(snapshot, "decliners") : null,
            observedMarket ? integer(snapshot, "unchanged") : null,
            observedMarket ? integer(snapshot, "suspended") : null,
            observedMarket ? integer(snapshot, "expected") : null,
            observedMarket ? integer(snapshot, "observed") : null,
            observedMarket ? number(snapshot, "coverage") : null);
    if (observedMarket && "PARTIAL".equals(marketStatus))
      missing.add(
          "当日沪深截面仍有未核验交易状态或行情记录；成交额和涨跌家数为已覆盖部分，不能当作全市场合计。待补记录："
              + snapshot.path("missingCount").asText("未知"));
    var observationNode = input.path("market").path("observation").path("analysis");
    MarketObservation observation = null;
    if (!demo
        && observationNode.isObject()
        && observationNode.path("businessDate").asText().equals(businessDate.toString())) {
      observation = JSON.convertValue(observationNode, MarketObservation.class);
    }
    missing.add(
        observation != null
            ? "沪深观察分项状态：趋势="
                + observation.trend().status()
                + "、历史量能="
                + observation.turnover().status()
                + "、均线广度="
                + observation.breadth().status()
                + "。具体缺项与采集限制见分项说明；北交所、完整五主题与龙头定义仍未满足全A五维要求，总评级保持UNKNOWN。"
            : observedMarket
                ? "已接入沪深A股当日成交额和涨跌家数；尚缺北交所覆盖、市场均线广度及成交额历史均值，全市场五维评级保持UNKNOWN。"
                : "本修订缺少可验证的市场截面，不能用自选股代替全A成交额与广度；无法评定全市场。");
    List<ForecastView> forecastFacts = new ArrayList<>();
    if (input.path("forecasts").isArray()) {
      for (var forecast : input.path("forecasts")) {
        forecastFacts.add(JSON.convertValue(forecast, ForecastView.class));
      }
    }
    if (!input.has("forecasts")) {
      missing.add("分析师盈利预期未接入；已披露财报实际值不等于未来盈利预期。");
    } else if (forecastFacts.stream().noneMatch(f -> f.latest() != null)) {
      missing.add("机构预测采集已接入，但本业务日16:10前无已保存预测快照；当前预测单独展示于机构预测页，不倒填本报告。");
    } else {
      missing.add("公开预测为供应商汇总，未核验逐家机构及披露前同口径预期；不能据此断言业绩超预期。");
    }
    assessments.sort(
        Comparator.comparingInt(
                (ReportAnalysis.Assessment a) ->
                    a.status().equals("TRIGGERED")
                        ? 0
                        : a.status().equals("INSUFFICIENT_DATA") ? 2 : 1)
            .thenComparingInt(a -> a.severity().equals("HIGH") ? 0 : 1)
            .thenComparing(ReportAnalysis.Assessment::symbol)
            .thenComparing(ReportAnalysis.Assessment::date));
    var themes = ThemeLinkageCalculator.calculate(input, businessDate, demo);
    var advice =
        input.has("ruleParameters")
            ? ResearchAdviceBuilder.build(
                stocks, assessments, financials, themes, input.path("ruleParameters"), demo)
            : List.<ResearchAdvice>of();
    List<MarketAcquisition> acquisition = new ArrayList<>();
    if (input.path("marketAcquisition").isArray()) {
      for (var row : input.path("marketAcquisition")) {
        acquisition.add(JSON.convertValue(row, MarketAcquisition.class));
      }
    }
    return new ReportAnalysis(
        ResearchVersion.ANALYSIS,
        stocks.isEmpty()
            ? "LEGACY_NO_MARKET_SNAPSHOT"
            : demo
                ? "DEMO"
                : stocks.stream()
                        .anyMatch(stock -> !Set.of("TRADING", "SUSPENDED").contains(stock.status()))
                    ? "PARTIAL"
                    : "AVAILABLE",
        headline,
        benchmarkReturn,
        stocks,
        assessments,
        checks,
        new ArrayList<>(missing),
        List.of(
            "收益按同源因子计算；均线及前20日低点仅为冻结的观测值，随新增K线变化。",
            "规则未触发、证据不足与不适用分别列示；自选研究不评估个人持仓风险。",
            "免费财报按公告与供应商修订日期的次日起使用；仍需核验原始公告，不作为严格时点历史回测样本。",
            "历史补跑可采用完整的已保存真实行情，原采集时间不变；使用方式与未联网复核的最新修订边界单独列示。",
            "联动采用留一法同业收益相关性，控制市场后的相关性仅剔除沪深300线性影响；量子纠缠仅作类比，相关性不证明因果或预测能力。"),
        financials,
        marketFact,
        input.path("researchMode").asText("LEGACY"),
        themes,
        advice,
        observation,
        forecastFacts,
        acquisition,
        PriceVolumeAnalysisBuilder.build(input, businessDate, demo));
  }

  private static Integer integer(JsonNode row, String key) {
    Double value = number(row, key);
    return value == null ? null : value.intValue();
  }

  private static JsonNode latestFinancial(JsonNode rows, LocalDate businessDate) {
    JsonNode latest = null;
    LocalDateTime cutoff = businessDate.atTime(16, 10);
    if (rows.isArray()) {
      for (var row : rows) {
        try {
          LocalDate period = LocalDate.parse(row.path("period_end").asText());
          String published = row.path("published_at").asText();
          String available =
              row.path("available_at").isMissingNode() || row.path("available_at").isNull()
                  ? published
                  : row.path("available_at").asText();
          if (period.isAfter(businessDate)
              || LocalDateTime.parse(published.replace(' ', 'T')).isAfter(cutoff)
              || LocalDateTime.parse(available.replace(' ', 'T')).isAfter(cutoff)) {
            continue;
          }
          if (latest == null
              || period.toString().compareTo(latest.path("period_end").asText()) > 0) {
            latest = row;
          }
        } catch (RuntimeException ignored) {
          /* 不完整旧输入不能用当前库补齐。 */
        }
      }
    }
    return latest;
  }
}

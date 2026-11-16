package com.alphascope.market.calculator;

import com.alphascope.market.dto.FreeMarketInput;
import com.alphascope.market.dto.ResearchMarketSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 当前在市目录与带日期行情交叉匹配；零成交、旧日期或缺字段不能被推断为停牌。
 *
 * @author chenxuefeng
 */
public final class FreeMarketNormalizer {
  private FreeMarketNormalizer() {}

  /**
   * 标准化同一业务日证据；北交所记录保留在原文中，现有统计仍明确为沪深范围。
   *
   * @param input 已完成分页和目录数量核验的原始批次
   * @return 目录来源SINA、价格及停复牌来源EASTMONEY的明确截面
   */
  public static ResearchMarketSnapshot normalize(FreeMarketInput input) {
    Map<String, String> members = new TreeMap<>();
    for (var row : input.roster()) {
      String symbol = row.path("symbol").asText();
      if (!symbol.matches("sh6[0-9]{5}|sz(?:00|30)[0-9]{4}|bj[0-9]{6}")
          || !symbol.substring(2).equals(row.path("code").asText())
          || members.putIfAbsent(
                  symbol.substring(2), symbol.substring(0, 2) + "." + symbol.substring(2))
              != null) throw new IllegalArgumentException("PUBLIC_ROSTER_INVALID_OR_DUPLICATE");
    }
    Set<String> halted = fullDaySuspensions(input.date(), input.suspensions());
    Map<String, JsonNode> matching = new HashMap<>();
    for (var row : input.quotes()) {
      String code = members.get(row.path("f12").asText());
      if (code == null) continue; // 排除报价大目录中的历史退市证券等额外行。
      int market = row.path("f13").asInt(-1);
      if (market != (code.startsWith("sh.") ? 1 : 0) || matching.putIfAbsent(code, row) != null)
        throw new IllegalArgumentException("PUBLIC_QUOTE_MARKET_OR_DUPLICATE");
    }
    List<Map<String, String>> universe = new ArrayList<>();
    List<Map<String, String>> daily = new ArrayList<>();
    for (String code : members.values()) {
      var quote = matching.get(code);
      Map<String, String> normalized = quote == null ? null : traded(input.date(), code, quote);
      if (normalized != null) {
        universe.add(Map.of("code", code, "tradeStatus", "1"));
        daily.add(normalized); // 有当日交易证据时不能因为盘中停牌记录而删除成交。
      } else if (halted.contains(code)) {
        universe.add(Map.of("code", code, "tradeStatus", "0"));
      } else {
        universe.add(Map.of("code", code, "tradeStatus", "UNKNOWN"));
      }
    }
    return ResearchMarketSnapshot.builder()
        .source("EASTMONEY")
        .date(input.date())
        .universe(universe)
        .daily(daily)
        .upstreamJson(input.upstreamJson())
        .limitations(
            List.of(
                "新浪当前在市目录与东方财富带日期行情交叉匹配；清单按供应商目录核验，未独立证明完整全A。",
                "停牌只计明确覆盖全天的记录；缺日期、缺数值或状态不明保留缺项，部分统计不能当作全市场总量。"))
        .build();
  }

  private static Map<String, String> traded(LocalDate date, String code, JsonNode quote) {
    try {
      var time =
          Instant.ofEpochSecond(quote.path("f124").asLong()).atZone(ZoneId.of("Asia/Shanghai"));
      if (!time.toLocalDate().equals(date) || time.toLocalTime().isBefore(LocalTime.of(15, 0)))
        return null;
      BigDecimal close = number(quote, "f2"),
          previous = number(quote, "f18"),
          amount = number(quote, "f6"),
          volume = number(quote, "f5");
      BigDecimal open = number(quote, "f17"),
          high = number(quote, "f15"),
          low = number(quote, "f16");
      if (close.signum() <= 0
          || previous.signum() <= 0
          || amount.signum() <= 0
          || volume.signum() <= 0
          || open.signum() <= 0
          || low.signum() <= 0
          || high.compareTo(low) < 0
          || high.compareTo(close) < 0
          || low.compareTo(close) > 0
          || high.compareTo(open) < 0
          || low.compareTo(open) > 0) return null;
      Map<String, String> row = new TreeMap<>();
      row.put("code", code);
      row.put("date", date.toString());
      row.put("tradestatus", "1");
      row.put("adjustflag", "3");
      row.put("close", close.toPlainString());
      row.put("preclose", previous.toPlainString());
      row.put("amount", amount.toPlainString());
      row.put("volume", volume.multiply(BigDecimal.valueOf(100)).toPlainString());
      row.put("open", open.toPlainString());
      row.put("high", high.toPlainString());
      row.put("low", low.toPlainString());
      return row;
    } catch (RuntimeException error) {
      return null;
    }
  }

  private static BigDecimal number(JsonNode row, String field) {
    if (!row.path(field).isNumber())
      throw new IllegalArgumentException("PUBLIC_QUOTE_NUMBER_MISSING");
    return row.path(field).decimalValue();
  }

  private static Set<String> fullDaySuspensions(LocalDate date, List<JsonNode> rows) {
    Set<String> result = new HashSet<>();
    var open = date.atTime(9, 30);
    var close = date.atTime(15, 0);
    for (var row : rows) {
      try {
        var start = LocalDateTime.parse(row.path("SUSPEND_START_TIME").asText().replace(' ', 'T'));
        String endText = row.path("SUSPEND_END_TIME").asText("");
        LocalDateTime end =
            endText.isBlank() ? null : LocalDateTime.parse(endText.replace(' ', 'T'));
        String symbol = row.path("SECUCODE").asText();
        if (!symbol.matches("[0-9]{6}\\.(SH|SZ|BJ)")
            || start.isAfter(open)
            || end != null && end.isBefore(close)) continue;
        String[] parts = symbol.split("\\.");
        result.add(parts[1].toLowerCase(java.util.Locale.ROOT) + "." + parts[0]);
      } catch (RuntimeException ignored) {
        /* 缺少明确起止证据时继续保持未知。 */
      }
    }
    return result;
  }
}

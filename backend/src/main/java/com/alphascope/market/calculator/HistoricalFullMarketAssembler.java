package com.alphascope.market.calculator;

import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.HistoricalFullMarketArchive;
import com.alphascope.market.entity.MarketBreadthSnapshot;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 校验历史全 A 导出，生成带来源和实际落库时点的不可变截面。
 *
 * @author chenxuefeng
 */
public final class HistoricalFullMarketAssembler {
  public static final String SOURCE = "BIGQUANT";
  public static final String PUBLIC_HISTORY_SOURCE = "BAOSTOCK_SINA";
  private static final String ROSTER_URL =
      "https://bigquant.com/data/datasources/cn_stock_instruments";

  private HistoricalFullMarketAssembler() {}

  private static final Map<String, Set<String>> EXCHANGE_SOURCES =
      Map.of(
          "SSE", Set.of("sse.com.cn"),
          "SZSE", Set.of("szse.cn"),
          "BSE", Set.of("bse.cn", "bseinfo.net"));

  private static boolean exchangeSource(String exchange, String link) {
    return sourceDomain(link, EXCHANGE_SOURCES.get(exchange));
  }

  private static boolean sourceDomain(String link, Set<String> domains) {
    if (link == null || link.isBlank()) return false;
    try {
      URI uri = URI.create(link);
      String host = uri.getHost();
      return "https".equalsIgnoreCase(uri.getScheme())
          && uri.getUserInfo() == null
          && host != null
          && uri.getPath() != null
          && uri.getPath().length() > 1
          && domains.stream()
              .anyMatch(
                  domain ->
                      host.equalsIgnoreCase(domain)
                          || host.toLowerCase(java.util.Locale.ROOT).endsWith("." + domain));
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  /**
   * 判断来源是否在历史档案内保存逐股复权依据。
   *
   * @param source 已存来源代码
   * @return 是否使用历史导入复算
   */
  public static boolean imported(String source) {
    return SOURCE.equals(source) || PUBLIC_HISTORY_SOURCE.equals(source);
  }

  /**
   * 逐项对照三所名册、交易状态、价格和因子。完全通过后才允许入库。
   *
   * @param input 来源导出资料
   * @param importedAt 系统实际入库时刻，上海时区
   * @return 已通过既有全 A 覆盖校验的截面
   */
  public static MarketBreadthSnapshot assemble(
      HistoricalFullMarketArchive input, LocalDateTime importedAt) {
    require(
        input != null
            && imported(input.source())
            && input.date() != null
            && input.members() != null
            && input.quotes() != null
            && input.expectedCounts() != null
            && input.rosterSources() != null,
        "缺少交易日、名单、行情或来源");
    boolean reconstructed = PUBLIC_HISTORY_SOURCE.equals(input.source());
    var comparison = input.comparison();
    require(
        !reconstructed
            || comparison != null
                && input.date().equals(comparison.date())
                && comparison.sha256() != null
                && comparison.sha256().matches("[0-9a-f]{64}")
                && (comparison.referenceSnapshotId() == null
                    ? sourceDomain(comparison.sourceUrl(), Set.of("csdata.cn"))
                    : comparison.referenceSnapshotId() > 0
                        && sourceDomain(comparison.sourceUrl(), Set.of("sina.com.cn"))),
        "缺少当日独立市场统计");
    require(input.exportedAt() != null, "缺少实际导出时间");
    var exportedAt =
        input.exportedAt().atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
    require(
        !exportedAt.isBefore(input.date().atTime(15, 30)) && !exportedAt.isAfter(importedAt),
        "导出时间必须在当日收盘之后及本次导入之前");
    var members = new TreeMap<String, Boolean>();
    var counts = new HashMap<String, Integer>();
    var suspensionEvidence = new TreeMap<String, HistoricalFullMarketArchive.Suspension>();
    require(
        input.rosterSources().keySet().equals(EXCHANGE_SOURCES.keySet())
            && input.rosterSources().entrySet().stream()
                .allMatch(
                    entry ->
                        exchangeSource(entry.getKey(), entry.getValue())
                            || reconstructed
                                && !"BSE".equals(entry.getKey())
                                && sourceDomain(entry.getValue(), Set.of("baostock.com"))),
        "缺少逐日独立的沪深北交易所名册或统计来源");
    for (var member : input.members()) {
      require(member != null && member.suspended() != null, "名册含未确认的交易状态");
      normalize(member.symbol());
      require(
          members.putIfAbsent(member.symbol(), member.suspended()) == null,
          "名册证券重复 " + member.symbol());
      String exchange =
          switch (member.symbol().substring(7)) {
            case "SH" -> "SSE";
            case "SZ" -> "SZSE";
            default -> "BSE";
          };
      counts.merge(exchange, 1, Integer::sum);
      var suspension = member.suspension();
      if (member.suspended()) {
        require(
            suspension != null
                && suspension.startAt() != null
                && suspension.endAt() != null
                && !suspension.startAt().isAfter(input.date().atTime(9, 30))
                && !suspension.endAt().isBefore(input.date().atTime(15, 0))
                && (exchangeSource(exchange, suspension.sourceUrl())
                    || reconstructed
                        && !"BSE".equals(exchange)
                        && sourceDomain(suspension.sourceUrl(), Set.of("baostock.com"))),
            "缺少覆盖全天的交易所停牌证据 " + member.symbol());
        suspensionEvidence.put(member.symbol(), suspension);
      } else {
        require(suspension == null, "交易状态与停牌证据冲突 " + member.symbol());
      }
    }
    require(
        counts.keySet().equals(Set.of("SSE", "SZSE", "BSE"))
            && counts.equals(input.expectedCounts()),
        "沪深北逐所名单数量与来源统计不一致");
    require(
        !reconstructed
            || comparison.excludedInstruments() != null
                && comparison.excludedInstruments().entrySet().stream()
                    .allMatch(
                        entry ->
                            entry.getKey().matches("689[0-9]{3}\\.SH")
                                && !members.containsKey(entry.getKey())
                                && exchangeSource("SSE", entry.getValue()))
                && comparison.shSzCount() - comparison.excludedInstruments().size()
                    == counts.get("SSE") + counts.get("SZSE")
                && comparison.bseCount() == counts.get("BSE"),
        "沪深与北交所名单数量与独立统计不符");
    var quotes = new TreeMap<String, HistoricalFullMarketArchive.Quote>();
    for (var quote : input.quotes()) {
      require(
          quote != null && quote.symbol() != null && members.containsKey(quote.symbol()),
          "行情含名册外证券");
      require(quotes.putIfAbsent(quote.symbol(), quote) == null, "行情证券重复 " + quote.symbol());
    }
    var universe = new ArrayList<Map<String, String>>();
    var daily = new ArrayList<Map<String, String>>();
    for (var entry : members.entrySet()) {
      String symbol = entry.getKey(),
          code = normalize(symbol),
          status = entry.getValue() ? "0" : "1";
      universe.add(Map.of("code", code, "tradeStatus", status));
      var quote = quotes.get(symbol);
      if (entry.getValue()) {
        require(
            quote == null
                || quote.close() == null
                    && (quote.amount() == null || quote.amount().signum() == 0),
            "停牌证据与成交价或成交额冲突 " + symbol);
        continue;
      }
      require(
          quote != null
              && positive(quote.close())
              && (positive(quote.previousClose()) || reconstructed && quote.previousClose() == null)
              && quote.amount() != null
              && quote.amount().signum() > 0
              && (positive(quote.factor())
                      && Double.isFinite(quote.close().doubleValue() * quote.factor().doubleValue())
                  || reconstructed && quote.factor() == null),
          "缺少或无效的交易价格、成交额或同源因子 " + symbol);
      var row =
          new TreeMap<>(
              Map.of(
                  "code",
                  code,
                  "date",
                  input.date().toString(),
                  "tradestatus",
                  status,
                  "adjustflag",
                  "3",
                  "close",
                  quote.close().toPlainString(),
                  "amount",
                  quote.amount().toPlainString()));
      if (quote.previousClose() != null) row.put("preclose", quote.previousClose().toPlainString());
      if (quote.factor() == null) row.put("factorUnavailable", "true");
      else row.put("factor", quote.factor().toPlainString());
      daily.add(row);
    }
    var metrics =
        reconstructed
            ? MarketBreadthCalculator.calculateHistorical(input.date(), universe, daily)
            : MarketBreadthCalculator.calculateScope(input.date(), universe, daily, "FULL_A");
    require("COMPLETE".equals(metrics.getStatus()), "截面未达到完整覆盖");
    metrics.setSource(input.source());
    var evidence =
        new TreeMap<String, Object>(
            Map.of(
                "source",
                input.source(),
                "exportedAt",
                input.exportedAt().toString(),
                "origin",
                reconstructed ? "INDEPENDENTLY_CHECKED_HISTORY" : "SUPPLIER_HISTORY_EXPORT",
                "rosterSources",
                input.rosterSources(),
                "suspensions",
                suspensionEvidence,
                "expectedCounts",
                counts));
    if (reconstructed) {
      evidence.put("comparison", comparison);
      evidence.put(
          "inputSources",
          comparison.referenceSnapshotId() == null
              ? java.util.List.of("BAOSTOCK", "SINA", "CSDATA")
              : java.util.List.of("BAOSTOCK", "SINA", "EASTMONEY"));
    }
    String raw =
        Jsons.write(
            Jsons.evidenceObject(
                Map.of(
                    "universe",
                    universe,
                    "daily",
                    daily,
                    "rosterEvidence",
                    evidence,
                    "universeProof",
                    Map.of(
                        "businessDate",
                        input.date().toString(),
                        "source",
                        input.source(),
                        "scope",
                        "FULL_A",
                        "completeRoster",
                        true,
                        "exchanges",
                        java.util.List.of("BSE", "SSE", "SZSE"),
                        "expectedCount",
                        members.size(),
                        "rosterSha256",
                        Jsons.hash(Jsons.write(members.keySet())),
                        "sourceUrl",
                        reconstructed ? comparison.sourceUrl() : ROSTER_URL))));
    var snapshot =
        MarketBreadthSnapshot.builder()
            .tradeDate(input.date())
            .source(input.source())
            .universeScope("FULL_A")
            .contentHash(Jsons.hash(raw))
            .metricsJson(Jsons.write(metrics))
            .rawJson(raw)
            .fetchedAt(importedAt)
            .build();
    require(
        UniverseCoverageCalculator.calculate(snapshot).coverage().rosterVerified(),
        "档案来源或覆盖证明无法复核");
    return snapshot;
  }

  private static boolean positive(BigDecimal number) {
    return number != null && number.doubleValue() > 0 && Double.isFinite(number.doubleValue());
  }

  private static String normalize(String symbol) {
    require(symbol != null && symbol.matches("[0-9]{6}\\.(?:SH|SZ|BJ)"), "证券代码格式无效 " + symbol);
    String code = symbol.substring(7).toLowerCase(Locale.ROOT) + "." + symbol.substring(0, 6);
    require(MarketBreadthCalculator.stock(code, "FULL_A"), "证券不属于支持的 A 股范围 " + symbol);
    return code;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }
}

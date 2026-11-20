package com.alphascope.market.provider.research;

import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.ExecutionProgress;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.calculator.FreeMarketNormalizer;
import com.alphascope.market.dto.FreeMarketInput;
import com.alphascope.market.dto.ResearchFactorRequest;
import com.alphascope.market.dto.ResearchFactorWindow;
import com.alphascope.market.dto.ResearchMarketSnapshot;
import com.alphascope.market.provider.MarketCapability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 新浪三所收盘报价。名单与独立北交所目录核对，停牌仍以明确记录为准。
 *
 * @author chenxuefeng
 */
public final class SinaSnapshotResearchProvider implements ResearchDataProvider {
  private static final String QUOTES = "https://hq.sinajs.cn/list=";
  private static final Pattern QUOTE =
      Pattern.compile("var hq_str_((?:sh|sz|bj)[0-9]{6})=\"([^\"]*)\";");
  private final Clock clock;
  private final FreeMarketHttp http;
  private final EastmoneyResearchProvider reference;
  private final boolean closeReference;

  public SinaSnapshotResearchProvider(
      Clock clock, EastmoneyResearchProvider reference, boolean closeReference) {
    this.clock = clock;
    this.http = new FreeMarketHttp(clock);
    this.reference = reference;
    this.closeReference = closeReference;
  }

  @Override
  public String code() {
    return "SINA";
  }

  @Override
  public Set<String> requiredSources() {
    return Set.of("SINA", "EASTMONEY");
  }

  @Override
  public boolean historicalSnapshots() {
    return false;
  }

  @Override
  public boolean fullMarketSnapshots() {
    return true;
  }

  @Override
  public boolean fullMarketOnly() {
    return true;
  }

  @Override
  public Instant blockedUntil() {
    return http.blockedUntil("sina");
  }

  @Override
  public Set<MarketCapability> capabilities() {
    return Set.of(MarketCapability.MARKET_SNAPSHOT);
  }

  @Override
  public ResearchMarketSnapshot snapshot(LocalDate date) {
    var observedAt = reference.verifiedClose(date);
    var directory = reference.directory(date);
    var symbols = directory.rows().stream().map(row -> row.path("symbol").asText()).toList();
    if (symbols.stream().anyMatch(symbol -> !symbol.matches("(?:sh|sz|bj)[0-9]{6}")))
      throw new IllegalStateException("PUBLIC_ROSTER_RESPONSE_INVALID");
    var pages = new ArrayList<String>();
    var quotes = new ArrayList<JsonNode>();
    for (int offset = 0; offset < symbols.size(); offset += 100) {
      ExecutionContext.checkpoint();
      int end = Math.min(offset + 100, symbols.size());
      var batch = symbols.subList(offset, end);
      String raw = http.sinaQuotes(URI.create(QUOTES + String.join(",", batch)));
      quotes.addAll(parseBatch(raw, new HashSet<>(batch)));
      pages.add(raw);
      ExecutionContext.progress(
          new ExecutionProgress(end, symbols.size(), "核验新浪收盘行情", clock.instant()));
    }
    var halts = reference.suspensions(date);
    String raw =
        Jsons.write(
            Map.of(
                "rosterSource",
                code(),
                "quoteSource",
                code(),
                "suspensionSource",
                "EASTMONEY",
                "rosterCount",
                symbols.size(),
                "observedAt",
                observedAt,
                "businessDate",
                date,
                "rosterPages",
                directory.pages(),
                "quotePages",
                pages,
                "suspensionPages",
                halts.pages(),
                "sourceUrls",
                List.of(
                    EastmoneyResearchProvider.ROSTER + "getHQNodeData",
                    QUOTES,
                    "https://data.eastmoney.com/tfpxx/")));
    if (raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 12_000_000)
      throw new IllegalStateException("PUBLIC_MARKET_EVIDENCE_TOO_LARGE");
    var verified =
        FreeMarketNormalizer.normalize(
            FreeMarketInput.builder()
                .date(date)
                .roster(directory.rows())
                .quotes(quotes)
                .suspensions(halts.rows())
                .upstreamJson(raw)
                .build());
    return new ResearchMarketSnapshot(
        code(),
        date,
        verified.universe(),
        verified.daily(),
        raw,
        List.of("收盘价和成交额来自新浪，全天停牌由东方财富停复牌资料确认。"));
  }

  /** 解析供应商返回的固定字段；空行情仍由停牌或缺项核验处理。 */
  static List<JsonNode> parseBatch(String raw, Set<String> expected) {
    var seen = new HashSet<String>();
    var quotes = new ArrayList<JsonNode>();
    var matcher = QUOTE.matcher(raw);
    while (matcher.find()) {
      var symbol = matcher.group(1);
      if (!expected.contains(symbol) || !seen.add(symbol))
        throw new IllegalStateException("SINA_BATCH_QUOTE_RESPONSE_INVALID");
      var fields = matcher.group(2).split(",", -1);
      if (fields.length <= 32) continue;
      try {
        long time =
            LocalDate.parse(fields[30])
                .atTime(LocalTime.parse(fields[31]))
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toEpochSecond();
        quotes.add(
            JsonNodeFactory.instance
                .objectNode()
                .put("f12", symbol.substring(2))
                .put("f13", symbol.startsWith("sh") ? 1 : 0)
                .put("f124", time)
                .put("f17", new BigDecimal(fields[1]))
                .put("f18", new BigDecimal(fields[2]))
                .put("f2", new BigDecimal(fields[3]))
                .put("f15", new BigDecimal(fields[4]))
                .put("f16", new BigDecimal(fields[5]))
                .put("f5", new BigDecimal(fields[8]).movePointLeft(2))
                .put("f6", new BigDecimal(fields[9])));
      } catch (NumberFormatException | java.time.DateTimeException invalid) {
        // 缺失或异常字段不作为有效收盘价，也不由报价推断停牌。
      }
    }
    if (!seen.equals(expected))
      throw new IllegalStateException("SINA_BATCH_QUOTE_RESPONSE_INVALID");
    return quotes;
  }

  @Override
  public ResearchFactorWindow factors(ResearchFactorRequest request) {
    throw new UnsupportedOperationException("PUBLIC_MARKET_FACTORS_NOT_AVAILABLE");
  }

  @Override
  public void close() {
    http.close();
    if (closeReference) reference.close();
  }
}

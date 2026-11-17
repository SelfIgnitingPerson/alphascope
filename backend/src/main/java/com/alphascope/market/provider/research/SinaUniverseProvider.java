package com.alphascope.market.provider.research;

import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.ExecutionProgress;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.MarketUniverseBatch;
import com.alphascope.market.dto.UniverseMember;
import com.alphascope.market.provider.ProviderDataUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 分页核对新浪全部在市A股目录，再提取北交所；不使用含旧代码和退市行的报价大目录。
 *
 * @author chenxuefeng
 */
@Component
public final class SinaUniverseProvider {
  public static final String SOURCE_URL =
      "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/Market_Center.getHQNodeData?node=hs_a";
  private static final String API =
      "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/Market_Center.";
  private final Clock clock;
  private final FreeMarketHttp http;
  private final Function<URI, JsonNode> transport;

  @Autowired
  public SinaUniverseProvider(Clock clock) {
    this.clock = clock;
    http = new FreeMarketHttp(clock);
    transport = http::get;
  }

  /**
   * 注入受控读取器进行协议测试，不访问真实网络。
   *
   * @param clock 上海采集日期的实际时钟
   * @param transport 分页及独立计数响应
   */
  public SinaUniverseProvider(Clock clock, Function<URI, JsonNode> transport) {
    this.clock = clock;
    this.transport = transport;
    http = null;
  }

  /**
   * 核对当前日期全部分页、唯一性及前后总数，保留原始分页并提取北交所成员。
   *
   * @param date 必须为上海当前日期；历史目录只能从真实归档读取
   * @return 当前供应商目录完整批次，不表示价格、停牌或历史可得性已验证
   */
  public synchronized MarketUniverseBatch fetch(LocalDate date) {
    if (!date.equals(today()))
      throw new ProviderDataUnavailableException("PUBLIC_MARKET_HISTORY_NOT_ARCHIVED");
    var started = clock.instant();
    int expected = count();
    var pages = new ArrayList<JsonNode>();
    var all = new TreeMap<String, UniverseMember>();
    var exchanges = new TreeMap<String, Integer>();
    String last = null;
    for (int page = 1; all.size() < expected && page <= 150; page++) {
      var rows = read(pageUrl(page));
      int required = Math.min(100, expected - all.size());
      if (!rows.isArray() || rows.size() != required)
        throw new IllegalStateException("PUBLIC_ROSTER_PAGE_INVALID");
      pages.add(rows);
      for (var row : rows) {
        String code = row.path("code").asText(), symbol = row.path("symbol").asText();
        String name = row.path("name").asText();
        if (!symbol.matches("sh6[0-9]{5}|sz(?:00|30)[0-9]{4}|bj[489][0-9]{5}")
            || !code.equals(symbol.substring(2))
            || name.isBlank()
            || name.length() > 64
            || last != null && symbol.compareTo(last) <= 0)
          throw new IllegalStateException("PUBLIC_ROSTER_MEMBER_INVALID");
        last = symbol;
        String exchange = symbol.substring(0, 2).toUpperCase(java.util.Locale.ROOT);
        if (all.putIfAbsent(symbol, new UniverseMember(code + "." + exchange, name)) != null)
          throw new IllegalStateException("PUBLIC_ROSTER_PAGE_REPEATED");
        exchanges.merge(exchange, 1, Integer::sum);
      }
      ExecutionContext.progress(
          new ExecutionProgress(all.size(), expected, "核对在市A股目录并提取北交所成员", clock.instant()));
    }
    int confirmed = count();
    var confirmation = read(pageUrl(1));
    if (all.size() != expected
        || confirmed != expected
        || !membership(pages.getFirst()).equals(membership(confirmation)))
      throw new IllegalStateException("PUBLIC_ROSTER_COUNT_CHANGED");
    if (!exchanges.keySet().equals(java.util.Set.of("SH", "SZ", "BJ")))
      throw new IllegalStateException("PUBLIC_ROSTER_EXCHANGE_MISSING");
    if (!date.equals(today())) throw new IllegalStateException("PUBLIC_ROSTER_DATE_CHANGED");
    var members = all.values().stream().filter(row -> row.symbol().endsWith(".BJ")).toList();
    var ended = clock.instant();
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("version", "CURRENT_UNIVERSE_V1");
    evidence.put("source", "SINA");
    evidence.put("businessDate", date);
    evidence.put("observedFrom", started);
    evidence.put("observedAt", ended);
    evidence.put("sourceUrl", SOURCE_URL);
    evidence.put("expectedAllA", expected);
    evidence.put("observedAllA", all.size());
    evidence.put("confirmedAllA", confirmed);
    evidence.put("exchanges", exchanges);
    evidence.put("pageCount", pages.size());
    evidence.put("pageSize", 100);
    evidence.put("countsConfirmed", true);
    evidence.put("firstPageConfirmed", true);
    evidence.put("firstPageConfirmation", confirmation);
    evidence.put("rosterPages", pages);
    String raw = Jsons.write(evidence);
    if (raw.getBytes(StandardCharsets.UTF_8).length > 12_000_000)
      throw new IllegalStateException("PUBLIC_MARKET_EVIDENCE_TOO_LARGE");
    return new MarketUniverseBatch(date, ended, List.copyOf(members), raw);
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
  }

  private List<Map<String, String>> membership(JsonNode rows) {
    var result = new ArrayList<Map<String, String>>();
    if (!rows.isArray()) throw new IllegalStateException("PUBLIC_ROSTER_PAGE_INVALID");
    for (var row : rows)
      result.add(
          Map.of(
              "code",
              row.path("code").asText(),
              "symbol",
              row.path("symbol").asText(),
              "name",
              row.path("name").asText()));
    // 盘中价格和成交量持续变化，确认的对象是成员身份，不能用报价变化误判成员变化。
    return List.copyOf(result);
  }

  private String pageUrl(int page) {
    return API
        + "getHQNodeData?page="
        + page
        + "&num=100&sort=symbol&asc=1&node=hs_a&symbol=&_s_r_a=page";
  }

  private int count() {
    var value = read(API + "getHQNodeStockCount?node=hs_a");
    int count;
    try {
      if (!value.isTextual()) throw new IllegalArgumentException();
      count = Integer.parseInt(value.asText());
    } catch (RuntimeException e) {
      throw new IllegalStateException("PUBLIC_ROSTER_COUNT_INVALID", e);
    }
    if (count < 4000 || count > 15000)
      throw new IllegalStateException("PUBLIC_ROSTER_COUNT_INVALID");
    return count;
  }

  private JsonNode read(String url) {
    ExecutionContext.checkpoint();
    return transport.apply(URI.create(url));
  }

  /** 释放目录HTTP客户端；不修改持久冷却或供应商访问状态。 */
  @PreDestroy
  public void close() {
    if (http != null) http.close();
  }
}

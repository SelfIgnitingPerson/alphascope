package com.alphascope.market.provider.research;

import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.ExecutionProgress;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.calculator.FreeMarketNormalizer;
import com.alphascope.market.dto.FreeMarketInput;
import com.alphascope.market.dto.ResearchFactorRequest;
import com.alphascope.market.dto.ResearchFactorWindow;
import com.alphascope.market.dto.ResearchMarketSnapshot;
import com.alphascope.market.provider.ExchangeCalendarProvider;
import com.alphascope.market.provider.MarketCapability;
import com.alphascope.market.provider.ProviderDataUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import java.util.function.Function;

/**
 * 免费收盘截面：新浪在市目录与东方财富带日期行情/明确停牌交叉核验，收盘后每日归档。
 *
 * <p>不能用当前目录倒造历史；不提供或反推同源因子，不自动扫描数千只股票的历史接口。
 *
 * @author chenxuefeng
 */
public final class EastmoneyResearchProvider implements ResearchDataProvider {
  static final String ROSTER =
      "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/Market_Center.";
  private static final String QUOTES = "https://push2.eastmoney.com/api/qt/clist/get";
  private final Clock clock;
  private final FreeMarketHttp http;
  private final Function<URI, JsonNode> transport;
  private ResearchMarketSnapshot cached;
  private Instant cacheUntil = Instant.EPOCH;
  private PageBatch directoryCache;
  private LocalDate directoryDate;

  public EastmoneyResearchProvider(Clock clock) {
    this.clock = clock;
    this.http = new FreeMarketHttp(clock);
    this.transport = http::get;
  }

  /**
   * 注入纯响应读取器供分页、缺页及时点契约测试，不读取真实网络或本机配置。
   *
   * @param clock 实际收盘日判断时钟
   * @param transport 有大小和超时边界的响应读取器
   */
  public EastmoneyResearchProvider(Clock clock, Function<URI, JsonNode> transport) {
    this.clock = clock;
    this.http = null;
    this.transport = transport;
  }

  @Override
  public String code() {
    return "EASTMONEY";
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
  public Instant blockedUntil() {
    return http == null ? null : http.blockedUntil();
  }

  @Override
  public Set<MarketCapability> capabilities() {
    return Set.of(MarketCapability.MARKET_SNAPSHOT);
  }

  @Override
  public synchronized ResearchMarketSnapshot snapshot(LocalDate date) {
    var now = verifiedClose(date);
    if (cached != null && cached.date().equals(date) && clock.instant().isBefore(cacheUntil))
      return cached;
    var directory = directory(date);
    List<JsonNode> quotePages = new ArrayList<>();
    List<JsonNode> quotes = new ArrayList<>();
    Set<String> quoteCodes = new HashSet<>();
    int total = -1;
    for (int page = 1; page <= 150; page++) {
      var root = read(quoteUrl(page));
      var data = root.path("data");
      int reported = data.path("total").asInt(-1);
      var rows = data.path("diff");
      if (root.path("rc").asInt(-1) != 0
          || reported < 4000
          || reported > 15000
          || !rows.isArray()
          || rows.isEmpty()
          || rows.size() > 100
          || total >= 0 && total != reported)
        throw new IllegalStateException("PUBLIC_QUOTE_PAGE_INVALID");
      total = reported;
      quotePages.add(root);
      for (var row : rows) {
        if (!quoteCodes.add(row.path("f13").asText() + ":" + row.path("f12").asText()))
          throw new IllegalStateException("PUBLIC_QUOTE_PAGE_REPEATED");
        quotes.add(row);
      }
      progress("读取当日市场行情", quotes.size(), total);
      if (quotes.size() >= total) break;
    }
    if (quotes.size() != total) throw new IllegalStateException("PUBLIC_QUOTE_PAGE_MISSING");
    var confirmation = read(quoteUrl(1));
    if (confirmation.path("data").path("total").asInt(-1) != total)
      throw new IllegalStateException("PUBLIC_QUOTE_COUNT_CHANGED");
    quotePages.add(confirmation);
    var halts = suspensions(date);
    String raw =
        Jsons.write(
            Map.of(
                "rosterSource",
                "SINA",
                "quoteSource",
                "EASTMONEY",
                "rosterCount",
                directory.rows().size(),
                "quoteDirectoryCount",
                total,
                "observedAt",
                now,
                "businessDate",
                date,
                "rosterPages",
                directory.pages(),
                "quotePages",
                quotePages,
                "suspensionPages",
                halts.pages(),
                "sourceUrls",
                List.of(ROSTER + "getHQNodeData", QUOTES, "https://data.eastmoney.com/tfpxx/")));
    if (raw.getBytes(StandardCharsets.UTF_8).length > 12_000_000)
      throw new IllegalStateException("PUBLIC_MARKET_EVIDENCE_TOO_LARGE");
    cached =
        FreeMarketNormalizer.normalize(
            FreeMarketInput.builder()
                .date(date)
                .roster(directory.rows())
                .quotes(quotes)
                .suspensions(halts.rows())
                .upstreamJson(raw)
                .build());
    cacheUntil = clock.instant().plusSeconds(1800);
    return cached;
  }

  java.time.ZonedDateTime verifiedClose(LocalDate date) {
    var now = clock.instant().atZone(ZoneId.of("Asia/Shanghai"));
    if (!date.equals(now.toLocalDate()))
      throw new ProviderDataUnavailableException("PUBLIC_MARKET_HISTORY_NOT_ARCHIVED");
    if (now.toLocalTime().isBefore(LocalTime.of(16, 10)))
      throw new ProviderDataUnavailableException("PUBLIC_MARKET_NOT_CLOSED");
    if (!Boolean.TRUE.equals(new ExchangeCalendarProvider().fetchCalendar(date, date).get(date)))
      throw new ProviderDataUnavailableException("PUBLIC_MARKET_NOT_TRADING_DAY");
    return now;
  }

  synchronized PageBatch directory(LocalDate date) {
    if (date.equals(directoryDate) && directoryCache != null) return directoryCache;
    var pages = new ArrayList<JsonNode>();
    var rows = new ArrayList<JsonNode>();
    var symbols = new HashSet<String>();
    int expected = count();
    for (int page = 1; rows.size() < expected && page <= 150; page++) {
      var batch =
          read(
              ROSTER
                  + "getHQNodeData?page="
                  + page
                  + "&num=100&sort=symbol&asc=1&node=hs_a&symbol=&_s_r_a=page");
      if (!batch.isArray() || batch.isEmpty() || batch.size() > 100)
        throw new IllegalStateException("PUBLIC_ROSTER_PAGE_INVALID");
      pages.add(batch);
      for (var row : batch) {
        if (!symbols.add(row.path("symbol").asText()))
          throw new IllegalStateException("PUBLIC_ROSTER_PAGE_REPEATED");
        rows.add(row);
      }
      progress("核对当前 A 股目录", rows.size(), expected);
    }
    if (rows.size() != expected || count() != expected)
      throw new IllegalStateException("PUBLIC_ROSTER_COUNT_CHANGED");
    directoryDate = date;
    directoryCache = new PageBatch(List.copyOf(rows), List.copyOf(pages));
    return directoryCache;
  }

  PageBatch suspensions(LocalDate date) {
    List<JsonNode> suspensions = new ArrayList<>();
    List<JsonNode> haltPages = new ArrayList<>();
    int haltTotal = -1;
    for (int page = 1; page <= 10; page++) {
      String filter = "(MARKET=\"全部\")(DATETIME='" + date + "')";
      var root =
          read(
              "https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPT_CUSTOM_SUSPEND_DATA_INTERFACE&columns=ALL&filter="
                  + URLEncoder.encode(filter, StandardCharsets.UTF_8)
                  + "&pageNumber="
                  + page
                  + "&pageSize=500&sortColumns=SUSPEND_START_DATE&sortTypes=-1");
      if (!root.path("success").asBoolean())
        throw new IllegalStateException("PUBLIC_SUSPENSION_RESPONSE_INVALID");
      var result = root.path("result");
      int pages = result.path("pages").asInt(-1), count = result.path("count").asInt(-1);
      if (pages < 1
          || pages > 10
          || count < 0
          || haltTotal >= 0 && haltTotal != count
          || !result.path("data").isArray())
        throw new IllegalStateException("PUBLIC_SUSPENSION_PAGE_INVALID");
      haltTotal = count;
      haltPages.add(root);
      result.path("data").forEach(suspensions::add);
      if (page == pages) break;
    }
    if (suspensions.size() != haltTotal)
      throw new IllegalStateException("PUBLIC_SUSPENSION_PAGE_MISSING");
    return new PageBatch(List.copyOf(suspensions), List.copyOf(haltPages));
  }

  /**
   * 已核验分页的行和原始响应，供两个收盘报价通道共用。
   *
   * @author chenxuefeng
   */
  record PageBatch(List<JsonNode> rows, List<JsonNode> pages) {}

  private int count() {
    int count;
    try {
      count = Integer.parseInt(read(ROSTER + "getHQNodeStockCount?node=hs_a").asText());
    } catch (NumberFormatException error) {
      throw new IllegalStateException("PUBLIC_ROSTER_COUNT_INVALID", error);
    }
    if (count < 4000 || count > 15000)
      throw new IllegalStateException("PUBLIC_ROSTER_COUNT_INVALID");
    return count;
  }

  private JsonNode read(String url) {
    ExecutionContext.checkpoint();
    return transport.apply(URI.create(url));
  }

  private String quoteUrl(int page) {
    return QUOTES
        + "?pn="
        + page
        + "&pz=100&po=1&np=1&fltt=2&invt=2&fid=f12&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23,m:0+t:81+s:2048&fields=f2,f3,f4,f5,f6,f12,f13,f14,f15,f16,f17,f18,f124";
  }

  private void progress(String message, int completed, int total) {
    ExecutionContext.progress(new ExecutionProgress(completed, total, message, clock.instant()));
  }

  @Override
  public ResearchFactorWindow factors(ResearchFactorRequest request) {
    throw new UnsupportedOperationException("PUBLIC_MARKET_FACTORS_NOT_AVAILABLE");
  }

  @Override
  public void close() {
    if (http != null) http.close();
  }
}

package com.alphascope.tools;

import com.alphascope.AlphaScopeApplication;
import com.alphascope.backtest.dto.ResearchValidationRequest;
import com.alphascope.backtest.dto.ResearchValidationRequest.Scope;
import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.provider.BaoStockMarketDataProvider;
import com.alphascope.market.provider.MarketDataProvider;
import com.alphascope.market.provider.SinaMarketDataProvider;
import com.alphascope.market.provider.baostock.BaoStockAccessGuard;
import com.alphascope.market.provider.baostock.BaoStockClient;
import com.alphascope.market.service.MarketBreadthService;
import com.alphascope.market.service.MarketService;
import com.alphascope.operations.maintenance.DatabaseMaintenance;
import com.alphascope.scheduler.dto.JobStatus;
import com.alphascope.scheduler.service.JobService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 开发工具统一Java入口。probe/check只读，replay/verify-real显式写本机业务库。
 *
 * @author chenxuefeng
 */
public final class ProjectTools {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String BASE = "http://127.0.0.1:8080";
  private static final List<String> SYMBOLS = List.of("300476.SZ", "002837.SZ", "000300.SH");

  private ProjectTools() {}

  /**
   * 分发显式开发命令；所有业务写入仍通过既有维护或任务入口执行。
   *
   * @param args 工具名及日期、来源等参数
   * @throws Exception 参数、服务或供应商操作失败
   */
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      throw new IllegalArgumentException(
          "package | validate-research 开始 结束 [all|technical|market] | reproduce-research-validation | backup | verify-backup | upgrade-schema | check | probe [baostock|sina] [日期] | replay 日期... | verify-real 日期 | verify-theme-sources | sync-theme-research 日期 | warm-market-history 日期");
    }
    Path root = Path.of(System.getProperty("alphascope.root", ".")).toAbsolutePath().normalize();
    switch (args[0]) {
      case "package" -> {
        int files = SourceArchive.write(root);
        System.out.println("artifacts/alphascope-source.zip files=" + files);
      }
      case "validate-research" -> {
        require(args.length == 3 || args.length == 4, "请提供起止日期，可选all/technical/market范围");
        var scope = args.length == 4 ? Scope.valueOf(args[3].toUpperCase(Locale.ROOT)) : Scope.ALL;
        var request =
            ResearchValidationRequest.builder()
                .start(LocalDate.parse(args[1]))
                .end(LocalDate.parse(args[2]))
                .scope(scope)
                .build();
        ResearchValidationTool.run(root, request);
      }
      case "reproduce-research-validation" -> ResearchValidationTool.reproduce(root);
      case "backup" -> System.out.println(Jsons.write(maintenance(root).backupAndVerify(14)));
      case "verify-backup" ->
          System.out.println("verified tables=" + maintenance(root).verifyLatest().tables().size());
      case "upgrade-schema-direct" ->
          System.out.println(
              "explicit direct schema DDL=" + maintenance(root).upgradeSchemaDirect());
      case "upgrade-schema" ->
          System.out.println("controlled schema DDL=" + maintenance(root).upgradeSchema());
      case "verify-theme-sources" -> verifyThemeSources(root);
      case "sync-theme-research" -> {
        require(args.length == 2, "请提供主题研究的精确业务日期");
        ThemeResearchTool.run(LocalDate.parse(args[1]));
      }
      case "warm-market-history" -> {
        require(args.length == 2, "请提供一个历史预热业务日期");
        warmMarketHistory(LocalDate.parse(args[1]));
      }
      case "probe" -> probe(args);
      case "check" -> check();
      case "replay", "verify-real" -> {
        if (args.length < 2) {
          throw new IllegalArgumentException("请明确提供业务日期，避免误写业务库");
        }
        for (int i = 1; i < args.length; i++) {
          LocalDate date = LocalDate.parse(args[i]);
          var job = api("/api/v1/admin/jobs/daily?date=" + date, true);
          long deadline = System.nanoTime() + 600_000_000_000L;
          while (JobStatus.valueOf(job.path("status").asText()).active()) {
            if (System.nanoTime() > deadline) {
              throw new IllegalStateException("任务等待超时，请查询任务状态");
            }
            Thread.sleep(500);
            job = api("/api/v1/admin/jobs/" + job.path("id").asLong(), false);
          }
          System.out.println(JSON.writeValueAsString(job));
          require(Set.of("SUCCESS", "SKIPPED").contains(job.path("status").asText()), "任务失败");
          if (args[0].equals("verify-real")) {
            require(job.path("data_scope").asText().startsWith("REAL:"), "任务不是实际行情模式");
            for (String symbol : SYMBOLS) {
              realBars(symbol, date);
            }
            // 本机写接口每秒限一次；已完成任务的复验也遵循相同频率。
            Thread.sleep(1100);
            require(
                api("/api/v1/admin/jobs/daily?date=" + date, true)
                    .path("id")
                    .equals(job.path("id")),
                "幂等检查失败");
          }
        }
      }
      default -> throw new IllegalArgumentException("未知命令");
    }
  }

  /** 在没有活动任务且上游允许访问时，以单会话补齐已有真实历史窗口。 */
  private static void warmMarketHistory(LocalDate date) {
    try (var context =
        new SpringApplicationBuilder(AlphaScopeApplication.class)
            .web(WebApplicationType.NONE)
            .run(
                "--alphascope.jobs.worker-enabled=false", "--alphascope.scheduler.enabled=false")) {
      var market = context.getBean(MarketService.class);
      require(!market.synthetic(), "历史预热只用于显式真实模式");
      require(!BaoStockAccessGuard.standard().blocked(), "上游访问受限，历史预热已停止；确认上游恢复前不重试");
      require(
          context
              .getBean(JobService.class)
              .page(PageRequest.builder().size(100).build())
              .items()
              .stream()
              .noneMatch(job -> JobStatus.valueOf(job.status()).active()),
          "存在研究任务时不运行独立历史预热，避免重复请求");
      var dates =
          new ArrayList<>(
              new TreeSet<>(
                  market.expectedDates(
                      TradingDatesQuery.builder()
                          .symbol("000300.SH")
                          .start(date.minusDays(140))
                          .end(date)
                          .source(null)
                          .build())));
      dates = new ArrayList<>(dates.subList(Math.max(0, dates.size() - 82), dates.size()));
      System.out.println("history warmup dates=" + dates.size() + " single worker");
      context.getBean(MarketBreadthService.class).syncHistory(dates);
      System.out.println("history warmup completed");
    }
  }

  private static void probe(String[] args) throws Exception {
    String selected = args.length > 1 ? args[1] : "baostock";
    LocalDate end =
        args.length > 2
            ? LocalDate.parse(args[2])
            : LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
    LocalDate start = end.minusDays(730);
    try (MarketDataProvider provider =
        switch (selected) {
          case "baostock" ->
              new BaoStockMarketDataProvider(
                  new BaoStockClient("public-api.baostock.com", 10030, 15000));
          case "sina" -> new SinaMarketDataProvider();
          default -> throw new IllegalArgumentException("probe仅支持baostock或sina");
        }) {
      boolean failed = false;
      try {
        System.out.println(
            provider.code() + " calendar days=" + provider.fetchCalendar(start, end).size());
      } catch (Exception e) {
        failed = true;
        System.out.println("calendar FAILED " + cause(e));
      }
      for (String symbol : SYMBOLS) {
        try {
          if (selected.equals("sina") && symbol.equals("000300.SH")) {
            System.out.println("SINA 000300.SH UNSUPPORTED: 指数成交额缺失，交给其他通道");
            continue;
          }
          var rows =
              symbol.equals("000300.SH")
                  ? provider.fetchIndexBars(symbol, start, end)
                  : provider.fetchDailyBars(symbol, start, end);
          require(rows.size() >= 250, "历史不足250日");
          require(
              rows.stream().noneMatch(r -> r.isSynthetic() || r.adjustmentFactor().signum() <= 0),
              "实际数据契约失败");
          var last = rows.getLast();
          System.out.println(
              provider.code()
                  + " "
                  + symbol
                  + " days="
                  + rows.size()
                  + " last="
                  + last.tradeDate()
                  + " close="
                  + last.close()
                  + " volume="
                  + last.volume()
                  + " amount="
                  + last.amount()
                  + " factor="
                  + last.adjustmentFactor());
        } catch (Exception e) {
          failed = true;
          System.out.println(symbol + " FAILED " + cause(e));
        }
      }
      require(!failed, "通道探测未全部通过");
    }
  }

  private static String cause(Throwable e) {
    while (e.getCause() != null) {
      e = e.getCause();
    }
    return e.getClass().getSimpleName() + ": " + e.getMessage();
  }

  private static JsonNode api(String path, boolean post) throws Exception {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      var request = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofMinutes(10));
      if (post) {
        request.POST(HttpRequest.BodyPublishers.noBody());
      }
      var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
      require(
          (response.statusCode() == 200 || response.statusCode() == 202),
          "HTTP " + response.statusCode() + " " + path);
      var result = JSON.readTree(response.body());
      if (!path.startsWith("/api/")) return result;
      require(
          "SUCCESS".equals(result.path("code").asText()),
          "业务请求失败：" + result.path("message").asText());
      return result.path("data");
    }
  }

  private static DatabaseMaintenance maintenance(Path root) {
    var settings =
        com.alphascope.common.config.DatabaseConnectionSettings.fromEnvironment(System.getenv());
    var source =
        new DriverManagerDataSource(settings.url(), settings.username(), settings.password());
    return new DatabaseMaintenance(source, root, Clock.systemUTC());
  }

  private static void require(boolean value, String message) {
    if (!value) {
      throw new IllegalStateException(message);
    }
  }

  private static void check() throws Exception {
    require(api("/actuator/health", false).path("status").asText().equals("UP"), "健康检查失败");
    var instrument = api("/api/v1/instruments/300476.SZ", false);
    require(instrument.path("active").isBoolean() && !instrument.has("id"), "标的契约失败");
    require(api("/api/v1/watchlist", false).isArray(), "自选契约失败");
    require(api("/api/v1/portfolio/positions", false).path("items").isArray(), "持仓契约失败");
    require(api("/v3/api-docs", false).has("openapi"), "OpenAPI失败");
    require(api("/api/v1/admin/jobs", false).path("items").isArray(), "任务契约失败");
    var latest = api("/api/v1/market/providers", false).path("latestData");
    for (var row : latest) {
      if (row.path("symbol").asText().equals("300476.SZ")) {
        realBars("300476.SZ", LocalDate.parse(row.path("trade_date").asText()));
      }
    }
    System.out.println("health / instruments / watchlist / portfolio / jobs / OpenAPI PASS");
  }

  private static void realBars(String symbol, LocalDate end) throws Exception {
    var rows =
        api(
                "/api/v1/instruments/"
                    + symbol
                    + "/bars?start="
                    + end.minusDays(730)
                    + "&end="
                    + end,
                false)
            .path("items");
    require(rows.isArray() && rows.size() >= 250, "历史行情不足250日");
    Set<String> sources = new HashSet<>();
    for (var row : rows) {
      require(
          row.path("is_synthetic").isBoolean() && !row.path("is_synthetic").asBoolean(), "模拟数据");
      require(row.hasNonNull("factor"), "缺少因子");
      sources.add(row.path("source").asText());
    }
    require(sources.size() == 1, "混合数据源");
    require(
        rows.get(rows.size() - 1).path("trade_date").asText().equals(end.toString()), "最新日期不完整");
    System.out.println(symbol + " real bars=" + rows.size() + " source=" + sources + " end=" + end);
  }

  /** 下载固定公开原文并比对PDF字节指纹；只读核验，不写业务库。 */
  private static void verifyThemeSources(Path root) throws Exception {
    var definitions =
        JSON.readTree(
            Files.readString(
                root.resolve("backend/src/main/resources/research/theme-cohorts.json")));
    try (var client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()) {
      for (var definition : definitions) {
        var request =
            HttpRequest.newBuilder(URI.create(definition.path("sourceUrl").asText()))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        require(response.statusCode() == 200, "原文不可获取：" + definition.path("code").asText());
        byte[] bytes = response.body();
        require(
            bytes.length > 100
                && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
                && new String(bytes, bytes.length - 100, 100, StandardCharsets.US_ASCII)
                    .contains("%%EOF"),
            "原文不是完整PDF");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        require(
            hash.equals(definition.path("pdfSha256").asText()),
            "PDF指纹变化，必须重新核对原文并新增样本版本：" + definition.path("code").asText());
        System.out.println(
            definition.path("code").asText()
                + " PDF bytes="
                + bytes.length
                + " SHA256="
                + hash
                + " PASS");
      }
    }
  }
}

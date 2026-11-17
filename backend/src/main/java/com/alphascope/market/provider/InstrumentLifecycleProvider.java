package com.alphascope.market.provider;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.InstrumentLifecycle;
import com.alphascope.market.provider.baostock.BaoStockClient;
import com.alphascope.market.provider.research.FreeMarketHttp;
import com.alphascope.operations.service.SourceLicenseService;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 优先用共享串行BaoStock基本资料；不可用时核对东方财富公开基本资料，保留原文。
 *
 * @author chenxuefeng
 */
@Component
public class InstrumentLifecycleProvider {
  private final BaoStockClient bao;
  private final SourceLicenseService licenses;
  private final AlphaScopeProperties configuration;
  private final Clock clock;
  private final FreeMarketHttp http;

  public InstrumentLifecycleProvider(
      BaoStockClient bao,
      SourceLicenseService licenses,
      AlphaScopeProperties configuration,
      Clock clock) {
    this.bao = bao;
    this.licenses = licenses;
    this.configuration = configuration;
    this.clock = clock;
    this.http = new FreeMarketHttp(clock);
  }

  /**
   * 采集单个证券明确生命周期。
   *
   * @param symbol 已验证代码
   * @return 带原文的生命周期
   */
  public InstrumentLifecycle fetch(String symbol) {
    if (!symbol.matches("[0-9]{6}\\.(SH|SZ|BJ)")) throw new IllegalArgumentException("生命周期代码无效");
    if (!symbol.endsWith(".BJ")
        && (configuration.getProvider() == AlphaScopeProperties.ProviderType.BAOSTOCK
            || configuration.getProvider() == AlphaScopeProperties.ProviderType.MULTI
                && configuration
                    .getMarket()
                    .getChannels()
                    .contains(AlphaScopeProperties.ProviderType.BAOSTOCK))) {
      try {
        licenses.require("BAOSTOCK", "COLLECT");
        String code = symbol.substring(7).toLowerCase(Locale.ROOT) + "." + symbol.substring(0, 6);
        var rows = bao.query("45", "query_stock_basic", 9, code, "");
        if (rows.size() != 1
            || !code.equals(rows.getFirst().get("code"))
            || !"1".equals(rows.getFirst().get("type")))
          throw new IllegalStateException("LIFECYCLE_IDENTITY_INVALID");
        var row = rows.getFirst();
        LocalDate ipo = LocalDate.parse(row.get("ipoDate"));
        String out = row.get("outDate");
        return new InstrumentLifecycle(
            symbol,
            row.get("code_name"),
            ipo,
            out == null || out.isBlank() ? null : LocalDate.parse(out),
            "BAOSTOCK",
            now(),
            Jsons.write(
                Map.of(
                    "sourceUrl",
                    "https://www.baostock.com/mainContent?file=stock_basic.md",
                    "method",
                    "query_stock_basic",
                    "raw",
                    rows)));
      } catch (RuntimeException rejected) {
        ExecutionContext.checkpoint();
      }
    }
    licenses.require("EASTMONEY_DIRECTORY", "COLLECT");
    try {
      var url =
          URI.create(
              "https://datacenter.eastmoney.com/securities/api/data/v1/get?reportName=RPT_F10_BASIC_ORGINFO&columns=ALL&source=HSF10&client=PC&filter="
                  + URLEncoder.encode("(SECUCODE=\"" + symbol + "\")", StandardCharsets.UTF_8));
      var root = http.get(url);
      var rows = root.path("result").path("data");
      if (!root.path("success").asBoolean() || rows.size() != 1)
        throw new IllegalStateException("LIFECYCLE_NOT_CONFIRMED");
      return parse(symbol, rows.get(0), url.toString(), now());
    } catch (RuntimeException failure) {
      throw new IllegalStateException("LIFECYCLE_NOT_CONFIRMED: " + failure.getMessage(), failure);
    }
  }

  /**
   * 校验原始身份与日期，旧精选层日期不能冒充北交所上市日。
   *
   * @param symbol 目标证券
   * @param row 原始资料
   * @param url 固定上游请求URL
   * @param at 采集时刻
   * @return 已核验资料
   */
  public static InstrumentLifecycle parse(
      String symbol, JsonNode row, String url, LocalDateTime at) {
    if (!symbol.equals(row.path("SECUCODE").asText())
        || !symbol.substring(0, 6).equals(row.path("SECURITY_CODE").asText())
        || !row.path("SECURITY_TYPEE").asText().equals("A股"))
      throw new IllegalStateException("LIFECYCLE_IDENTITY_INVALID");
    String rawDate = row.path("LISTING_DATE").asText();
    LocalDate ipo;
    try {
      ipo = LocalDate.parse(rawDate.substring(0, 10));
    } catch (RuntimeException e) {
      throw new IllegalStateException("LIFECYCLE_LISTING_DATE_MISSING", e);
    }
    if (ipo.isAfter(at.toLocalDate()) || ipo.isBefore(LocalDate.of(1990, 1, 1)))
      throw new IllegalStateException("LIFECYCLE_LISTING_DATE_INVALID");
    if (symbol.endsWith(".BJ") && ipo.isBefore(LocalDate.of(2021, 11, 15)))
      throw new IllegalStateException("LIFECYCLE_BSE_LEGACY_DATE_AMBIGUOUS");
    String out = row.path("DELISTING_DATE").asText("");
    LocalDate delisted = out.isBlank() ? null : LocalDate.parse(out.substring(0, 10));
    return new InstrumentLifecycle(
        symbol,
        row.path("SECURITY_NAME_ABBR").asText(),
        ipo,
        delisted,
        "EASTMONEY_DIRECTORY",
        at,
        Jsons.write(Map.of("sourceUrl", url, "raw", row)));
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai"));
  }

  @jakarta.annotation.PreDestroy
  public void close() {
    http.close();
  }
}

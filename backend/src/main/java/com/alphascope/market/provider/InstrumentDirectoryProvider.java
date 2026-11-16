package com.alphascope.market.provider;

import com.alphascope.common.util.HttpBodies;
import com.alphascope.market.vo.InstrumentCandidate;
import com.alphascope.operations.service.SourceLicenseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 固定域名的公开证券目录适配；只接受沪深北A股代码，不执行响应中的脚本。
 *
 * @author chenxuefeng
 */
@Component
public class InstrumentDirectoryProvider {
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(4))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final ObjectMapper json;

  public InstrumentDirectoryProvider(ObjectMapper json) {
    this.json = json;
  }

  public List<InstrumentCandidate> search(String keyword) {
    if (licenses != null) licenses.require("EASTMONEY_DIRECTORY", "COLLECT");
    var cached = cache.get(keyword);
    if (cached != null && cached.until().isAfter(Instant.now())) return cached.items();
    if (!slots.tryAcquire()) throw new IllegalStateException("DIRECTORY_BUSY");
    try {
      return fetch(keyword);
    } finally {
      slots.release();
    }
  }

  private final Semaphore slots = new Semaphore(4);
  private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();
  private SourceLicenseService licenses;

  @Autowired
  public void setLicenses(SourceLicenseService licenses) {
    this.licenses = licenses;
  }

  /**
   * 有时限的公开目录缓存；不作为历史成分或上市时点证据。
   *
   * @author chenxuefeng
   * @param until 失效时刻
   * @param items 已验证字段候选
   */
  private record Cached(Instant until, List<InstrumentCandidate> items) {}

  private List<InstrumentCandidate> fetch(String keyword) {
    try {
      URI uri =
          URI.create(
              "https://searchapi.eastmoney.com/api/suggest/get?type=14&count=20&input="
                  + URLEncoder.encode(keyword, StandardCharsets.UTF_8));
      var response =
          client.send(
              HttpRequest.newBuilder(uri)
                  .timeout(Duration.ofSeconds(6))
                  .header("User-Agent", "AlphaScope/0.1")
                  .GET()
                  .build(),
              HttpBodies.limited(1048576, Duration.ofSeconds(6)));
      {
        byte[] bytes = response.body();
        if (response.statusCode() != 200 || bytes.length > 1048576)
          throw new IllegalStateException("DIRECTORY_RESPONSE_INVALID");
        var items = parse(json.readTree(bytes));
        if (cache.size() >= 500) cache.clear();
        cache.put(keyword, new Cached(Instant.now().plusSeconds(900), items));
        return items;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("DIRECTORY_INTERRUPTED", e);
    } catch (IOException e) {
      throw new IllegalStateException("DIRECTORY_UNAVAILABLE", e);
    }
  }

  /** 从受控返回字段解析A股，期货、基金、港股、B股不能作为股票登记。 */
  public List<InstrumentCandidate> parse(JsonNode root) {
    var result = new ArrayList<InstrumentCandidate>();
    for (var item : root.path("QuotationCodeTable").path("Data")) {
      String code = item.path("Code").asText(),
          quote = item.path("QuoteID").asText(),
          name = item.path("Name").asText(),
          type = item.path("SecurityTypeName").asText();
      if (!code.matches("[0-9]{6}")
          || name.isBlank()
          || name.length() > 64
          || !(type.contains("A股") || type.equals("沪A") || type.equals("深A") || type.equals("京A")))
        continue;
      String exchange =
          quote.equals("1." + code) && code.matches("[6][0-9]{5}")
              ? "SH"
              : quote.equals("0." + code) && code.matches("[03][0-9]{5}")
                  ? "SZ"
                  : quote.equals("0." + code) && code.matches("[489][0-9]{5}") ? "BJ" : null;
      if (exchange != null)
        result.add(
            new InstrumentCandidate(
                code + "." + exchange, name, exchange, false, "EASTMONEY_DIRECTORY"));
    }
    return result.stream().distinct().limit(20).toList();
  }
}

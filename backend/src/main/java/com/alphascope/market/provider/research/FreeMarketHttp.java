package com.alphascope.market.provider.research;

import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.util.HttpBodies;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * 免费截面串行HTTP读取，限制正文和频率；403/429写入冷却标记，重启不绕过限制。
 *
 * @author chenxuefeng
 */
public final class FreeMarketHttp implements AutoCloseable {
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
  private final ObjectMapper json =
      new ObjectMapper()
          .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
  private final Clock clock;
  private final Path stateDirectory;
  private final java.util.function.Function<URI, java.net.http.HttpResponse<byte[]>> transport;
  private static final Object REQUEST_LOCK = new Object();
  private static long lastRequest;

  /**
   * 建立受限公开来源传输，持久访问保护保留于运行目录。
   *
   * @param clock 核验时钟
   */
  public FreeMarketHttp(Clock clock) {
    this(clock, Path.of(System.getProperty("alphascope.root", "."), ".runtime"), null);
  }

  /** 隔离测试提供传输，仍经过相同白名单、串行限速与持久冷却检查。 */
  FreeMarketHttp(
      Clock clock,
      Path stateDirectory,
      java.util.function.Function<URI, java.net.http.HttpResponse<byte[]>> transport) {
    this.clock = clock;
    this.stateDirectory = stateDirectory;
    this.transport = transport;
  }

  Instant blockedUntil() {
    return java.util.stream.Stream.of("eastmoney", "sina")
        .map(this::blockedUntil)
        .max(Instant::compareTo)
        .orElse(Instant.EPOCH);
  }

  /** 单个供应商的持久保护；同一供应商所有相关域名共享，不连带另一供应商。 */
  Instant blockedUntil(String supplier) {
    Path cooldown = cooldown(supplier);
    try {
      return Files.exists(cooldown)
          ? Instant.parse(Files.readString(cooldown).trim())
          : Instant.EPOCH;
    } catch (Exception error) {
      throw new IllegalStateException("PUBLIC_MARKET_ACCESS_STATE_INVALID", error);
    }
  }

  /**
   * 读取白名单HTTPS原文，遵守供应商冷却与串行频率。
   *
   * @param uri 固定上游地址
   * @return 有界JSON正文
   */
  public JsonNode get(URI uri) {
    // 目录补齐与收盘截面共享进程内串行锁，不能因增加入口而放大免费节点并发。
    synchronized (REQUEST_LOCK) {
      if (!"https".equals(uri.getScheme())
          || !Set.of(
                  "push2.eastmoney.com",
                  "datacenter-web.eastmoney.com",
                  "datacenter.eastmoney.com",
                  "vip.stock.finance.sina.com.cn")
              .contains(uri.getHost()))
        throw new IllegalArgumentException("PUBLIC_MARKET_HOST_NOT_ALLOWED");
      String supplier = uri.getHost().contains("sina") ? "sina" : "eastmoney";
      Path cooldown = cooldown(supplier);
      Instant retryAt = blockedUntil(supplier);
      if (clock.instant().isBefore(retryAt))
        throw new IllegalStateException(
            "PUBLIC_MARKET_ACCESS_COOLDOWN supplier=" + supplier + " retryAt=" + retryAt);
      try {
        ExecutionContext.checkpoint();
        long wait = 1200 - Duration.ofNanos(System.nanoTime() - lastRequest).toMillis();
        if (wait > 0) Thread.sleep(wait);
        lastRequest = System.nanoTime();
        var response =
            transport != null
                ? transport.apply(uri)
                : client.send(
                    HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(15))
                        .header(
                            "Referer",
                            uri.getHost().contains("sina")
                                ? "https://finance.sina.com.cn/"
                                : "https://quote.eastmoney.com/")
                        .header("User-Agent", "Mozilla/5.0 AlphaScope/1.0")
                        .GET()
                        .build(),
                    HttpBodies.limited(6000000, Duration.ofSeconds(12)));
        if (response.statusCode() == 403 || response.statusCode() == 429) {
          Files.createDirectories(cooldown.toAbsolutePath().getParent());
          Path temporary =
              Files.createTempFile(
                  cooldown.toAbsolutePath().getParent(), "market-cooldown-", ".tmp");
          Files.writeString(temporary, clock.instant().plusSeconds(3600).toString());
          Files.move(
              temporary,
              cooldown,
              java.nio.file.StandardCopyOption.ATOMIC_MOVE,
              java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          throw new IllegalStateException(
              "PUBLIC_MARKET_ACCESS_COOLDOWN supplier="
                  + supplier
                  + " retryAt="
                  + blockedUntil(supplier));
        }
        if (response.statusCode() != 200)
          throw new IllegalStateException("PUBLIC_MARKET_HTTP_ERROR");
        return json.readTree(response.body());
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("PUBLIC_MARKET_INTERRUPTED", error);
      } catch (java.io.IOException error) {
        throw new IllegalStateException("PUBLIC_MARKET_UNAVAILABLE", error);
      }
    }
  }

  private Path cooldown(String supplier) {
    if (!Set.of("eastmoney", "sina").contains(supplier))
      throw new IllegalArgumentException("未知的公开来源保护范围");
    return stateDirectory.resolve(supplier + "-market-cooldown.txt");
  }

  @Override
  public void close() {
    client.close();
  }
}

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
final class FreeMarketHttp implements AutoCloseable {
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
  private final ObjectMapper json =
      new ObjectMapper()
          .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
  private final Clock clock;
  private final Path cooldown =
      Path.of(
          System.getProperty("alphascope.root", "."), ".runtime", "eastmoney-market-cooldown.txt");
  private long lastRequest;

  FreeMarketHttp(Clock clock) {
    this.clock = clock;
  }

  Instant blockedUntil() {
    try {
      return Files.exists(cooldown)
          ? Instant.parse(Files.readString(cooldown).trim())
          : Instant.EPOCH;
    } catch (Exception error) {
      throw new IllegalStateException("PUBLIC_MARKET_ACCESS_STATE_INVALID", error);
    }
  }

  synchronized JsonNode get(URI uri) {
    if (!"https".equals(uri.getScheme())
        || !Set.of(
                "push2.eastmoney.com",
                "datacenter-web.eastmoney.com",
                "vip.stock.finance.sina.com.cn")
            .contains(uri.getHost()))
      throw new IllegalArgumentException("PUBLIC_MARKET_HOST_NOT_ALLOWED");
    if (clock.instant().isBefore(blockedUntil()))
      throw new IllegalStateException("PUBLIC_MARKET_ACCESS_COOLDOWN");
    try {
      ExecutionContext.checkpoint();
      long wait = 1200 - Duration.ofNanos(System.nanoTime() - lastRequest).toMillis();
      if (wait > 0) Thread.sleep(wait);
      lastRequest = System.nanoTime();
      var response =
          client.send(
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
            Files.createTempFile(cooldown.toAbsolutePath().getParent(), "market-cooldown-", ".tmp");
        Files.writeString(temporary, clock.instant().plusSeconds(3600).toString());
        Files.move(
            temporary,
            cooldown,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        throw new IllegalStateException("PUBLIC_MARKET_ACCESS_COOLDOWN");
      }
      if (response.statusCode() != 200) throw new IllegalStateException("PUBLIC_MARKET_HTTP_ERROR");
      return json.readTree(response.body());
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("PUBLIC_MARKET_INTERRUPTED", error);
    } catch (java.io.IOException error) {
      throw new IllegalStateException("PUBLIC_MARKET_UNAVAILABLE", error);
    }
  }

  @Override
  public void close() {
    client.close();
  }
}

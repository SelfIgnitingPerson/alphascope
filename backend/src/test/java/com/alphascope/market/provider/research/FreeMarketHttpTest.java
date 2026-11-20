package com.alphascope.market.provider.research;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 单一供应商限制不能封锁另一供应商，进程重建仍遵守原冷却。 @author chenxuefeng */
class FreeMarketHttpTest {
  @TempDir Path root;

  @Test
  void sinaBatchRetainsDateAndConvertsShareVolumeWithoutAssumingMissingRows() {
    var fields = new String[39];
    java.util.Arrays.fill(fields, "0");
    fields[0] = "北交所测试样本";
    for (int i = 1; i <= 5; i++) fields[i] = "10.20";
    fields[8] = "20000";
    fields[9] = "204000";
    fields[30] = "2026-11-19";
    fields[31] = "15:30:00";
    String payload = "var hq_str_bj920085=\"" + String.join(",", fields) + "\";";
    var quote =
        SinaSnapshotResearchProvider.parseBatch(payload, java.util.Set.of("bj920085")).getFirst();
    assertEquals("920085", quote.path("f12").asText());
    assertEquals(200, quote.path("f5").intValue());
    assertEquals(204000, quote.path("f6").intValue());
    assertEquals(
        Instant.parse("2026-11-19T07:30:00Z").getEpochSecond(), quote.path("f124").asLong());
    assertThrows(
        IllegalStateException.class,
        () ->
            SinaSnapshotResearchProvider.parseBatch(
                payload, java.util.Set.of("bj920085", "sz002837")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void restrictedSupplierIsIsolatedAndPersisted() throws Exception {
    var clock = Clock.fixed(Instant.parse("2026-11-17T08:00:00Z"), ZoneOffset.UTC);
    var calls = new AtomicInteger();
    java.util.function.Function<URI, HttpResponse<byte[]>> transport =
        uri -> {
          calls.incrementAndGet();
          var response = (HttpResponse<byte[]>) mock(HttpResponse.class);
          when(response.statusCode()).thenReturn(uri.getHost().contains("eastmoney") ? 403 : 200);
          when(response.body()).thenReturn("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
          return response;
        };
    var east = URI.create("https://push2.eastmoney.com/api/test");
    var sina = URI.create("https://vip.stock.finance.sina.com.cn/test");
    try (var first = new FreeMarketHttp(clock, root, transport)) {
      assertThrows(IllegalStateException.class, () -> first.get(east));
      assertTrue(first.get(sina).path("ok").asBoolean());
      assertEquals(Instant.EPOCH, first.blockedUntil("sina"));
    }
    try (var restarted = new FreeMarketHttp(clock, root, transport)) {
      assertThrows(IllegalStateException.class, () -> restarted.get(east));
      assertEquals(2, calls.get(), "冷却中的重启不得触达上游");
      assertTrue(Files.exists(root.resolve("eastmoney-market-cooldown.txt")));
      assertFalse(Files.exists(root.resolve("sina-market-cooldown.txt")));
    }
  }
}

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

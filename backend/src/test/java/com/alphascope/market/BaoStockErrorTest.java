package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.common.diagnostic.FailureDiagnostics;
import com.alphascope.common.diagnostic.baostock.BaoStockApiException;
import com.alphascope.common.diagnostic.baostock.BaoStockErrors;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.provider.baostock.BaoStockClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 供应商错误码、明确拒绝不重试、安全原文和旧记录诚实展示；仅使用本地模拟协议。
 *
 * @author chenxuefeng
 */
class BaoStockErrorTest {
  @Test
  void distinguishesPermissionSessionParametersAndBlacklist() {
    assertEquals("用户权限不足", BaoStockErrors.diagnose("10001006", null).providerError().meaning());
    assertEquals("账号登陆数达到上限", BaoStockErrors.diagnose("10001005", null).providerError().meaning());
    assertEquals("无效的证券代码", BaoStockErrors.diagnose("10004011", null).providerError().meaning());
    assertTrue(BaoStockErrors.diagnose("10001011", null).action().contains("待释放时间"));
    assertTrue(BaoStockErrors.diagnose("10004011", null).action().contains("修正请求"));
    assertEquals("BAOSTOCK_API_ERROR_10002008", BaoStockErrors.diagnose("10002008", null).code());
  }

  @Test
  void oldCodeMeaningIsNeverPresentedAsOriginalResponse() {
    var value = FailureDiagnostics.code("BAOSTOCK_ACCESS_RESTRICTED_10001011");
    assertEquals("10001011", value.providerError().code());
    assertEquals("黑名单用户", value.providerError().meaning());
    assertNull(value.providerError().message());
    assertEquals("NOT_RECORDED", value.providerError().messageState());
    assertEquals(
        "NOT_RECORDED",
        FailureDiagnostics.code("BAOSTOCK_API_ERROR_10001006").providerError().messageState());
  }

  @Test
  void onlyKnownSafeOriginalMessagesAreExposedAndUnknownNumericCodesSurvive() {
    var value = FailureDiagnostics.from(new BaoStockApiException("10001011", "黑名单用户，请与管理员联系"));
    assertEquals("UPSTREAM_SAFE_TEXT", value.providerError().messageState());
    assertEquals("黑名单用户，请与管理员联系", value.providerError().message());
    var privateText =
        FailureDiagnostics.from(
            new BaoStockApiException("10001006", "token=secret https://private.example/"));
    assertEquals("WITHHELD", privateText.providerError().messageState());
    assertFalse(Jsons.write(privateText).contains("secret"));
    var unknown = FailureDiagnostics.from(new BaoStockApiException("19999999", "private"));
    assertEquals("19999999", unknown.providerError().code());
    assertEquals("未收录的供应商错误码", unknown.providerError().meaning());
    assertTrue(unknown.action().contains("不推断"));
  }

  @Test
  void aggregateTaskErrorsRetainTheNumericCodeAndMeaning() {
    var value =
        FailureDiagnostics.recorded(
            "所有真实行情通道不可用（不会回退模拟）：BAOSTOCK:BAOSTOCK_API_ERROR_10001006,SINA:UNSUPPORTED");
    assertTrue(value.reason().contains("10001006"));
    assertTrue(value.reason().contains("用户权限不足"));
    assertFalse(value.reason().contains("新浪"));
    assertEquals("UNCLASSIFIED_FAILURE", FailureDiagnostics.code("BAOSTOCK_API_ERROR_0").code());
  }

  @Test
  void explicitApiRefusalDoesNotReconnectEvenWithoutPersistentProtection() throws Exception {
    for (String code : List.of("10001006", "10001011", "10004011", "19999999")) {
      try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
          var executor = Executors.newSingleThreadExecutor()) {
        var reply =
            executor.submit(
                () -> {
                  try (var socket = server.accept()) {
                    var reader =
                        new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    assertTrue(reader.readLine().contains("login"));
                    NativeProviderTest.send(socket, "01", code + "\u0001" + "拒绝");
                    assertNull(reader.readLine(), "明确业务错误后关闭会话，不发送后续查询");
                  } catch (Exception error) {
                    throw new RuntimeException(error);
                  }
                });
        try (var client = new BaoStockClient("127.0.0.1", server.getLocalPort(), 1000, null)) {
          var failure =
              assertThrows(
                  BaoStockApiException.class,
                  () -> client.query("33", "query_trade_dates", 9, "2026-11-12", "2026-11-12"));
          assertEquals(code, failure.errorCode());
        }
        reply.get(5, TimeUnit.SECONDS);
        server.setSoTimeout(100);
        assertThrows(SocketTimeoutException.class, server::accept);
      }
    }
  }

  @Test
  void queryRefusalAfterSuccessfulLoginIsNotRetried() throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        var executor = Executors.newSingleThreadExecutor()) {
      var reply =
          executor.submit(
              () -> {
                try (var socket = server.accept()) {
                  var reader =
                      new BufferedReader(
                          new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                  assertTrue(reader.readLine().contains("login"));
                  NativeProviderTest.send(socket, "01", "0\u0001success");
                  assertTrue(reader.readLine().contains("query_daily_history_k_AStock"));
                  NativeProviderTest.send(socket, "99", "10001006\u0001用户权限不足");
                  assertNull(reader.readLine());
                } catch (Exception error) {
                  throw new RuntimeException(error);
                }
              });
      try (var client = new BaoStockClient("127.0.0.1", server.getLocalPort(), 1000, null)) {
        var failure =
            assertThrows(
                BaoStockApiException.class, () -> client.dailyStocks(LocalDate.of(2026, 11, 12)));
        assertEquals("用户权限不足", FailureDiagnostics.from(failure).providerError().message());
      }
      reply.get(5, TimeUnit.SECONDS);
      server.setSoTimeout(100);
      assertThrows(SocketTimeoutException.class, server::accept);
    }
  }

  @Test
  void channelViewRetainsSafeUpstreamMessageWithoutRepeatingTheRequest(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path root) {
    String previous = System.getProperty("alphascope.root");
    try {
      System.setProperty("alphascope.root", root.toString());
      var provider =
          org.mockito.Mockito.mock(com.alphascope.market.provider.MarketDataProvider.class);
      org.mockito.Mockito.when(provider.code()).thenReturn("BAOSTOCK");
      org.mockito.Mockito.when(provider.capabilities())
          .thenReturn(
              java.util.Set.of(com.alphascope.market.provider.MarketCapability.TRADING_CALENDAR));
      var day = LocalDate.of(2026, 11, 12);
      org.mockito.Mockito.when(provider.fetchCalendar(day, day))
          .thenThrow(new BaoStockApiException("10001006", "用户权限不足"));
      var route =
          new com.alphascope.market.provider.MultiChannelMarketDataProvider(
              List.of(provider), java.time.Duration.ofSeconds(30), java.time.Clock.systemUTC());
      var failure = assertThrows(IllegalStateException.class, () -> route.fetchCalendar(day, day));
      assertTrue(failure.getMessage().contains("BAOSTOCK_API_ERROR_10001006"));
      var observed = route.status().getFirst().diagnostic().providerError();
      assertEquals("10001006", observed.code());
      assertEquals("用户权限不足", observed.message());
      assertEquals("UPSTREAM_SAFE_TEXT", observed.messageState());
      route.status();
      org.mockito.Mockito.verify(provider, org.mockito.Mockito.times(1)).fetchCalendar(day, day);
    } finally {
      if (previous == null) System.clearProperty("alphascope.root");
      else System.setProperty("alphascope.root", previous);
    }
  }
}

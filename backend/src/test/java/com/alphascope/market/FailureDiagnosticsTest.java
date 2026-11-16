package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.common.diagnostic.FailureDiagnostics;
import com.alphascope.common.util.Jsons;
import java.net.*;
import org.junit.jupiter.api.Test;

/**
 * 故障分类、异常链证据及公开脱敏边界，不发起外部请求。
 *
 * @author chenxuefeng
 */
class FailureDiagnosticsTest {
  @Test
  void inspectsNestedNetworkCauseWithoutExposingItsMessage() {
    var value =
        FailureDiagnostics.from(
            new IllegalStateException(
                "SINA_UNAVAILABLE", new UnknownHostException("private-host?token=secret")));
    assertEquals("DNS_FAILURE", value.code());
    assertFalse(Jsons.write(value).contains("secret"));
    assertEquals("NETWORK_TIMEOUT", FailureDiagnostics.from(new SocketTimeoutException()).code());
    assertEquals("CONNECTION_FAILED", FailureDiagnostics.from(new ConnectException()).code());
  }

  @Test
  void keepsBudgetAndAccessControlsDistinctFromConnectivityAndMembership() {
    assertEquals(
        "DAILY_BUDGET_EXHAUSTED",
        FailureDiagnostics.code("BAOSTOCK_DAILY_BUDGET_EXHAUSTED：详情").code());
    assertEquals("INCOMPLETE_HISTORY", FailureDiagnostics.code("INCOMPLETE_HISTORY").code());
    assertTrue(FailureDiagnostics.code("INCOMPLETE_HISTORY").reason().contains("已返回行情"));
    assertEquals("SESSION_BUSY", FailureDiagnostics.code("BAOSTOCK_SESSION_BUSY").code());
    assertEquals(
        "POLICY_STATE_UNREADABLE",
        FailureDiagnostics.code("BAOSTOCK_BUDGET_STATE_UNREADABLE").code());
    assertTrue(
        FailureDiagnostics.code("BAOSTOCK_ACCESS_RESTRICTED_10001011").reason().contains("不证明此刻"));
    assertNull(FailureDiagnostics.code("AVAILABLE"));
  }

  @Test
  void freezesPerChannelReasonsInTheStageExplanation() {
    var value =
        FailureDiagnostics.from(
            new IllegalStateException(
                "所有真实行情通道不可用（不会回退模拟）：BAOSTOCK:ACCESS_RESTRICTED,SINA:UNSUPPORTED,EASTMONEY:NETWORK_TIMEOUT"));
    assertEquals("NO_AVAILABLE_CHANNEL", value.code());
    assertTrue(value.reason().contains("东方财富：供应商连接或读取超时"));
    assertFalse(value.reason().contains("新浪"), "正常跳过的来源不应列为阻断原因");
    assertTrue(value.action().contains("不要反复重试"));
  }

  @Test
  void unknownFailuresDoNotInventAnExactCauseOrExposeRawData() {
    var value = FailureDiagnostics.from(new IllegalStateException("token=secret; SQL private"));
    assertEquals("UNCLASSIFIED_FAILURE", value.code());
    assertFalse(Jsons.write(value).contains("secret"));
    assertTrue(value.action().contains("任务编号"));
  }

  @Test
  void routingRetainsTheKnownCauseInBothErrorAndChannelView() {
    var provider =
        org.mockito.Mockito.mock(com.alphascope.market.provider.MarketDataProvider.class);
    org.mockito.Mockito.when(provider.code()).thenReturn("SINA");
    org.mockito.Mockito.when(provider.capabilities())
        .thenReturn(
            java.util.Set.of(com.alphascope.market.provider.MarketCapability.TRADING_CALENDAR));
    var day = java.time.LocalDate.of(2026, 11, 11);
    org.mockito.Mockito.when(provider.fetchCalendar(day, day))
        .thenThrow(
            new IllegalStateException(
                "SINA_UNAVAILABLE", new UnknownHostException("private-host")));
    var router =
        new com.alphascope.market.provider.MultiChannelMarketDataProvider(
            java.util.List.of(provider),
            java.time.Duration.ofSeconds(30),
            java.time.Clock.systemUTC());
    var error = assertThrows(IllegalStateException.class, () -> router.fetchCalendar(day, day));
    assertTrue(error.getMessage().contains("SINA:DNS_FAILURE"));
    assertEquals("DNS_FAILURE", router.status().getFirst().diagnostic().code());
    assertTrue(Jsons.write(router.status().getFirst()).contains("diagnostic"));
    assertTrue(
        FailureDiagnostics.recorded("行情采集失败：000300.SH。" + error.getMessage())
            .reason()
            .contains("域名解析失败"));
  }

  @Test
  void distinguishesRecordedHttpStatusFromUnconfirmedHttpFailureAndHistoryGaps() {
    assertEquals("HTTP_429", FailureDiagnostics.code("SINA_HTTP_429").code());
    assertTrue(FailureDiagnostics.code("EASTMONEY_INDEX_HTTP_403").action().contains("不能仅凭"));
    assertEquals("HTTP_FAILURE", FailureDiagnostics.code("SINA_HTTP_ERROR").code());
    assertEquals(
        "HISTORY_NOT_ARCHIVED",
        FailureDiagnostics.code("PUBLIC_MARKET_HISTORY_NOT_ARCHIVED").code());
    var failure =
        new com.alphascope.common.exception.BusinessException(
            com.alphascope.common.response.ResultCode.DATA_UNAVAILABLE, 503, "数据源使用范围尚未就绪：SINA");
    assertEquals("ACCESS_DENIED", FailureDiagnostics.from(failure).code());
  }

  @Test
  void classifiesBodyTimeoutConnectionClosureAndTransportWithoutInventingPermission() {
    var timeout =
        FailureDiagnostics.from(
            new IllegalStateException(
                "EASTMONEY_INDEX_UNAVAILABLE",
                new java.io.IOException(
                    "private-url?token=secret", new java.util.concurrent.TimeoutException())));
    assertEquals("NETWORK_TIMEOUT", timeout.code());
    var closed =
        FailureDiagnostics.from(
            new IllegalStateException(
                "SINA_UNAVAILABLE",
                new java.io.IOException("private header", new java.io.EOFException("secret"))));
    assertEquals("CONNECTION_CLOSED", closed.code());
    assertFalse(Jsons.write(closed).contains("secret"));
    assertEquals(
        "TRANSPORT_IO_FAILURE", FailureDiagnostics.from(new java.io.IOException("private")).code());
    assertEquals(
        "HTTP_BODY_LIMIT_EXCEEDED",
        FailureDiagnostics.from(new java.io.IOException("HTTP_BODY_LIMIT_EXCEEDED")).code());
  }

  @Test
  void preservesSpecificDataAndPersistentCooldownCauses() {
    assertEquals(
        "SINA_CALENDAR_INSUFFICIENT", FailureDiagnostics.code("SINA_CALENDAR_INSUFFICIENT").code());
    assertEquals("SINA_FACTOR_MISSING", FailureDiagnostics.code("SINA_FACTOR_MISSING").code());
    assertEquals("SINA_MISSING_AMOUNT", FailureDiagnostics.code("SINA_MISSING_AMOUNT").code());
    assertEquals("SINA_DECODE_FAILED", FailureDiagnostics.code("SINA_DECODE_FAILED").code());
    assertEquals(
        "ACCESS_COOLDOWN", FailureDiagnostics.code("PUBLIC_MARKET_ACCESS_COOLDOWN").code());
    assertTrue(FailureDiagnostics.code("PUBLIC_MARKET_ACCESS_COOLDOWN").reason().contains("此前"));
  }

  @Test
  void storesFailureTimePerCapabilityWithoutUpdatingItDuringRead() {
    var provider =
        org.mockito.Mockito.mock(com.alphascope.market.provider.MarketDataProvider.class);
    org.mockito.Mockito.when(provider.code()).thenReturn("SINA");
    org.mockito.Mockito.when(provider.capabilities())
        .thenReturn(
            java.util.Set.of(com.alphascope.market.provider.MarketCapability.TRADING_CALENDAR));
    var day = java.time.LocalDate.of(2026, 11, 12);
    org.mockito.Mockito.when(provider.fetchCalendar(day, day))
        .thenThrow(new IllegalStateException("SINA_UNAVAILABLE", new SocketTimeoutException()));
    var now = java.time.Instant.parse("2026-11-12T09:00:00Z");
    var router =
        new com.alphascope.market.provider.MultiChannelMarketDataProvider(
            java.util.List.of(provider),
            java.time.Duration.ofSeconds(30),
            java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
    assertThrows(IllegalStateException.class, () -> router.fetchCalendar(day, day));
    var ability =
        router.status().getFirst().capabilities().stream()
            .filter(v -> v.supported())
            .findFirst()
            .orElseThrow();
    assertEquals(now, ability.lastFailure());
    assertEquals(now, router.status().getFirst().capabilities().getFirst().lastFailure());
    org.mockito.Mockito.verify(provider, org.mockito.Mockito.times(1)).fetchCalendar(day, day);
  }

  @Test
  void protectionEvidenceKeepsItsOriginalTimeAndIsReadOnly(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
    var file = dir.resolve("guard.json");
    String original =
        "{\"blocked\":true,\"code\":\"10001011\",\"observedAt\":\"2026-11-01T12:44:54.821Z\"}";
    java.nio.file.Files.writeString(file, original);
    var guard = new com.alphascope.market.provider.baostock.BaoStockAccessGuard(file);
    assertTrue(guard.blocked());
    assertEquals("10001011", guard.state().code());
    assertEquals(java.time.Instant.parse("2026-11-01T12:44:54.821Z"), guard.state().observedAt());
    assertEquals(original, java.nio.file.Files.readString(file));
    java.nio.file.Files.writeString(file, "{\"blocked\":true,\"observedAt\":\"invalid\"}");
    assertThrows(IllegalStateException.class, guard::blocked);
  }
}

package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.market.dto.DailyBarDto;
import com.alphascope.market.provider.*;
import com.alphascope.market.provider.baostock.BaoStockAccessGuard;
import com.alphascope.market.provider.baostock.BaoStockClient;
import com.alphascope.market.provider.sina.SinaDecoder;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;

/**
 * 固定公开样本与本地协议服务，不依赖供应商网络或Python环境。
 *
 * @author chenxuefeng
 */
class NativeProviderTest {
  static final String SEP = "\u0001";
  static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void accessRefusalIsPersistedAndNewClientsDoNotReconnect(
      @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
    var file = directory.resolve("access.json");
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
                  send(socket, "01", "10001011" + SEP + "黑名单用户，请与管理员联系");
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      var guard = new BaoStockAccessGuard(file);
      try (var client = new BaoStockClient("127.0.0.1", server.getLocalPort(), 1000, guard)) {
        var error =
            assertThrows(
                IllegalStateException.class,
                () -> client.query("33", "query_trade_dates", 9, "2026-10-30", "2026-10-30"));
        assertTrue(error.getMessage().contains("10001011"));
      }
      reply.get(5, TimeUnit.SECONDS);
      assertTrue(guard.blocked());
      assertEquals("黑名单用户，请与管理员联系", guard.state().errorMessage());
      try (var next =
          new BaoStockClient(
              "127.0.0.1", server.getLocalPort(), 1000, new BaoStockAccessGuard(file))) {
        assertThrows(
            IllegalStateException.class, () -> next.dailyStocks(LocalDate.of(2026, 10, 30)));
      }
      server.setSoTimeout(100);
      assertThrows(SocketTimeoutException.class, server::accept);
    }
  }

  @Test
  void budgetExhaustionDoesNotSendQueryAndReleasesSession(
      @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
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
                  send(socket, "01", "0" + SEP + "success");
                  assertNull(reader.readLine(), "预算耗尽后应断开，不能发送查询或重试登录");
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      var policy =
          new com.alphascope.market.provider.baostock.BaoStockRequestPolicy(
              directory, 1, Clock.systemUTC());
      try (var client =
          new BaoStockClient(
              new BaoStockClient.Settings(
                  "127.0.0.1", server.getLocalPort(), 1000, null, policy))) {
        assertTrue(
            assertThrows(
                    IllegalStateException.class,
                    () -> client.dailyStocks(LocalDate.of(2026, 11, 10)))
                .getMessage()
                .contains("DAILY_BUDGET_EXHAUSTED"));
      }
      reply.get(5, TimeUnit.SECONDS);
      try (var next =
          new com.alphascope.market.provider.baostock.BaoStockRequestPolicy(
              directory, 2, Clock.systemUTC())) {
        next.acquireSession();
        assertEquals(1, next.usage().requests());
      }
    }
  }

  @Test
  void damagedAccessStateFailsClosed(@org.junit.jupiter.api.io.TempDir Path directory)
      throws Exception {
    var file = directory.resolve("access.json");
    Files.writeString(file, "broken-json");
    var guard = new BaoStockAccessGuard(file);
    assertThrows(IllegalStateException.class, guard::check);
  }

  @Test
  void restrictedSourceIsSkippedAndStatusDoesNotPromiseAutomaticRetry(
      @org.junit.jupiter.api.io.TempDir Path directory) {
    String old = System.getProperty("alphascope.root");
    try {
      System.setProperty("alphascope.root", directory.toString());
      BaoStockAccessGuard.standard()
          .restrict(
              new com.alphascope.common.diagnostic.baostock.BaoStockApiException("10001011", null));
      var blocked = mock(MarketDataProvider.class);
      when(blocked.code()).thenReturn("BAOSTOCK");
      var fallback = mock(MarketDataProvider.class);
      when(fallback.code()).thenReturn("SINA");
      when(fallback.capabilities())
          .thenReturn(
              java.util.Set.of(com.alphascope.market.provider.MarketCapability.TRADING_CALENDAR));
      var day = LocalDate.of(2026, 10, 30);
      when(fallback.fetchCalendar(day, day)).thenReturn(Map.of(day, true));
      var multi =
          new MultiChannelMarketDataProvider(
              List.of(blocked, fallback), Duration.ofSeconds(30), Clock.systemUTC());
      assertTrue(multi.fetchCalendar(day, day).get(day));
      verify(blocked, never()).fetchCalendar(any(), any());
      assertEquals("ACCESS_RESTRICTED", multi.status().getFirst().state());
      assertNull(multi.status().getFirst().retryAt());
    } finally {
      if (old == null) System.clearProperty("alphascope.root");
      else System.setProperty("alphascope.root", old);
    }
  }

  @Test
  void unsupportedIndexDoesNotTripStockChannelCircuit() {
    MarketDataProvider provider =
        new MarketDataProvider() {
          public String code() {
            return "STOCK_ONLY";
          }

          public boolean supportsIndexBars() {
            return false;
          }

          public List<DailyBarDto> fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
            return List.of();
          }

          public Map<LocalDate, Boolean> fetchCalendar(LocalDate from, LocalDate to) {
            return Map.of(from, true);
          }
        };
    var multi =
        new MultiChannelMarketDataProvider(
            List.of(provider), Duration.ofSeconds(30), Clock.systemUTC());
    var date = LocalDate.of(2026, 10, 30);
    assertThrows(IllegalStateException.class, () -> multi.fetchIndexBars("000300.SH", date, date));
    assertEquals(0, multi.status().getFirst().consecutiveFailures());
    assertEquals(false, multi.status().getFirst().supportsIndexBars());
    assertTrue(multi.fetchCalendar(date, date).get(date));
  }

  String resource(String name) throws Exception {
    try (var stream = getClass().getResourceAsStream("/market/" + name)) {
      return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void sinaCalendarMatchesIndependentDecoderFixture() throws Exception {
    verifyFixture("calendar");
  }

  @Test
  void sinaPriceAndUnitsMatchIndependentDecoderFixture() throws Exception {
    verifyFixture("bars");
  }

  void verifyFixture(String name) throws Exception {
    var rows =
        new SinaDecoder().decode(SinaDecoder.assignmentData(resource("sina-" + name + ".txt")));
    var expected = JSON.readTree(resource("sina-" + name + "-expected.json"));
    assertEquals(expected.path("size").asInt(), rows.size());
    assertEquals(expected.path("first"), rows.get(0));
    assertEquals(expected.path("last"), rows.get(rows.size() - 1));
  }

  @Test
  void sinaAssignmentDoesNotExecuteTrailingCode() {
    assertEquals("ABC+/", SinaDecoder.assignmentData("var price=\"ABC+/\";evil();/* ignored */"));
    assertThrows(
        IllegalArgumentException.class, () -> SinaDecoder.assignmentData("evil();var x=\"ABC\""));
    assertThrows(
        IllegalArgumentException.class, () -> new SinaDecoder().decode("java.lang.Runtime"));
  }

  @Test
  void sinaMissingIndexAmountIsExplicit() {
    try (var provider = new SinaMarketDataProvider()) {
      assertThrows(
          UnsupportedOperationException.class,
          () -> provider.fetchIndexBars("000300.SH", LocalDate.now(), LocalDate.now()));
    }
  }

  @Test
  void requestFrameHasByteLengthAndUnsignedCrc() {
    String body = "query" + SEP + "中文";
    byte[] bytes = BaoStockClient.encode("95", body);
    String text = new String(bytes, StandardCharsets.UTF_8);
    assertEquals(
        body.getBytes(StandardCharsets.UTF_8).length, Integer.parseInt(text.substring(11, 21)));
    CRC32 crc = new CRC32();
    crc.update(Arrays.copyOf(bytes, 21 + body.getBytes(StandardCharsets.UTF_8).length));
    assertTrue(text.endsWith(SEP + crc.getValue() + "\n"));
  }

  @Test
  void corruptedCompressedFrameIsRejected() {
    assertThrows(IOException.class, () -> BaoStockClient.decodeBody("96", new byte[] {1, 2, 3}));
  }

  @Test
  void fragmentedCompressedPagesReuseLoginAndKeepAllRows() throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        var executor = Executors.newSingleThreadExecutor()) {
      Future<?> reply =
          executor.submit(
              () -> {
                try (var socket = server.accept()) {
                  var reader =
                      new BufferedReader(
                          new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                  assertTrue(reader.readLine().contains("login" + SEP + "anonymous"));
                  send(socket, "01", "0" + SEP + "success");
                  for (int page = 1; page <= 2; page++) {
                    assertTrue(
                        reader
                            .readLine()
                            .contains(
                                "query_history_k_data_plus"
                                    + SEP
                                    + "anonymous"
                                    + SEP
                                    + page
                                    + SEP
                                    + "2000"));
                    var rows = new ArrayList<List<String>>();
                    for (int i = 0; i < (page == 1 ? 2000 : 1); i++)
                      rows.add(List.of("row-" + page + "-" + i));
                    String records = JSON.writeValueAsString(Map.of("record", rows));
                    send(
                        socket,
                        "96",
                        String.join(SEP, "0", "success", "x", "x", "x", "x", records, "x", "date"));
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (var client = new BaoStockClient("127.0.0.1", server.getLocalPort(), 3000)) {
        var rows = client.query("95", "query_history_k_data_plus", 8, "sz.300476");
        assertEquals(2001, rows.size());
        assertEquals("row-2-0", rows.getLast().get("date"));
      }
      reply.get(10, TimeUnit.SECONDS);
    }
  }

  static void send(Socket socket, String type, String text) throws Exception {
    send(socket, type, text, false);
  }

  static void send(Socket socket, String type, String text, boolean characterLength)
      throws Exception {
    byte[] payload = text.getBytes(StandardCharsets.UTF_8);
    if (Set.of("96", "99").contains(type)) {
      var bytes = new ByteArrayOutputStream();
      try (var deflate = new DeflaterOutputStream(bytes)) {
        deflate.write(payload);
      }
      payload = bytes.toByteArray();
    }
    var out = socket.getOutputStream();
    byte[] header =
        ("00.9.40"
                + SEP
                + type
                + SEP
                + String.format(
                    Locale.ROOT, "%010d", characterLength ? text.length() : payload.length))
            .getBytes(StandardCharsets.US_ASCII);
    for (byte value : header) {
      out.write(value);
      out.flush();
    }
    out.write(payload);
    out.write((SEP + "0<![CDATA[]]>\n").getBytes(StandardCharsets.US_ASCII));
    out.flush();
  }

  @Test
  void chineseUniverseAndCompressedBulkDailyUseTheirOwnFrameLayouts() throws Exception {
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
                  send(socket, "01", "0" + SEP + "success");
                  assertTrue(reader.readLine().contains("query_all_stock"));
                  String records =
                      JSON.writeValueAsString(
                          Map.of(
                              "record", List.of(List.of("sh.600000", "1", "中文名称".repeat(1000)))));
                  send(
                      socket,
                      "36",
                      String.join(
                          SEP,
                          "0",
                          "success",
                          "x",
                          "x",
                          "x",
                          "x",
                          records,
                          "x",
                          "code,tradeStatus,code_name"),
                      true);
                  String request = reader.readLine();
                  assertTrue(
                      request.contains(
                          "query_daily_history_k_AStock" + SEP + "anonymous" + SEP + "2026-10-30"));
                  assertFalse(request.contains("2000"));
                  records =
                      JSON.writeValueAsString(
                          Map.of("record", List.of(List.of("sh.600000", "10.0200"))));
                  send(
                      socket,
                      "99",
                      String.join(SEP, "0", "success", "x", "x", records, "code,close"));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (var client = new BaoStockClient("127.0.0.1", server.getLocalPort(), 3000)) {
        assertEquals(
            "中文名称".repeat(1000),
            client.query("35", "query_all_stock", 8, "2026-10-30").getFirst().get("code_name"));
        assertEquals(
            "10.0200", client.dailyStocks(LocalDate.of(2026, 10, 30)).getFirst().get("close"));
      }
      reply.get(10, TimeUnit.SECONDS);
    }
  }

  @Test
  void factorsUseEffectiveDateAndPreserveUnitsWithoutFutureLeak() {
    var client = mock(BaoStockClient.class);
    LocalDate start = LocalDate.of(2025, 1, 2), end = start.plusDays(1);
    var rows =
        List.of(
            Map.of(
                "date",
                start.toString(),
                "open",
                "10",
                "high",
                "11",
                "low",
                "9",
                "close",
                "10",
                "volume",
                "12345",
                "amount",
                "123450",
                "tradestatus",
                "1"),
            Map.of(
                "date",
                end.toString(),
                "open",
                "10",
                "high",
                "11",
                "low",
                "9",
                "close",
                "10",
                "volume",
                "1",
                "amount",
                "10",
                "tradestatus",
                "1"));
    when(client.query(
            "95",
            "query_history_k_data_plus",
            8,
            "sz.300476",
            "date,open,high,low,close,volume,amount,tradestatus",
            start.toString(),
            end.toString(),
            "d",
            "3"))
        .thenReturn(rows);
    when(client.query("15", "query_adjust_factor", 10, "sz.300476", "1990-01-01", end.toString()))
        .thenReturn(
            List.of(
                Map.of("dividOperateDate", "2024-01-01", "backAdjustFactor", "2"),
                Map.of("dividOperateDate", end.toString(), "backAdjustFactor", "3")));
    var bars = new BaoStockMarketDataProvider(client).fetchDailyBars("300476.SZ", start, end);
    assertEquals("2", bars.getFirst().adjustmentFactor().toPlainString());
    assertEquals("3", bars.getLast().adjustmentFactor().toPlainString());
    assertEquals("12345", bars.getFirst().volume().toPlainString());
    assertEquals("123450", bars.getFirst().amount().toPlainString());
    assertFalse(bars.getFirst().isSynthetic());
  }
}

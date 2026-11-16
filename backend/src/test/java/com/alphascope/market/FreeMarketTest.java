package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.market.calculator.FreeMarketNormalizer;
import com.alphascope.market.dto.FreeMarketInput;
import com.alphascope.market.provider.MarketCapability;
import com.alphascope.market.provider.ProviderDataUnavailableException;
import com.alphascope.market.provider.research.EastmoneyResearchProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 免费目录与日期交叉核验，防止退市行、未收盘、旧报价和零成交被误标为完整真实截面。
 *
 * @author chenxuefeng
 */
class FreeMarketTest {
  private final ObjectMapper json = new ObjectMapper();
  private final LocalDate date = LocalDate.of(2026, 11, 10);
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC);

  @Test
  void currentRosterExcludesHistoricalQuoteDirectoryMembersAndKeepsRawUnits() {
    var result =
        FreeMarketNormalizer.normalize(
            input(
                List.of(roster("sh600000")),
                List.of(quote("600000", 1), quote("600001", 1)),
                List.of()));
    assertEquals(1, result.universe().size());
    assertEquals(1, result.daily().size());
    assertEquals("10000", result.daily().getFirst().get("volume"));
    assertEquals("123456.78", result.daily().getFirst().get("amount"));
    assertEquals("EASTMONEY", result.source());
  }

  @Test
  void staleOrZeroTradingRowsRemainUnknownRatherThanBeingFabricatedAsSuspensions() {
    var stale = quote("600000", 1).deepCopy();
    stale.put("f124", Instant.parse("2026-11-09T07:30:00Z").getEpochSecond());
    var zero = quote("000001", 0).deepCopy();
    zero.put("f5", 0);
    zero.put("f6", 0);
    var result =
        FreeMarketNormalizer.normalize(
            input(
                List.of(roster("sh600000"), roster("sz000001")), List.of(stale, zero), List.of()));
    assertTrue(result.daily().isEmpty());
    assertTrue(
        result.universe().stream().allMatch(row -> row.get("tradeStatus").equals("UNKNOWN")));
  }

  @Test
  void suspensionMustCoverWholeDayAndResumeDateIsNotBackdated() throws Exception {
    var halt =
        json.readTree(
            "{\"SECUCODE\":\"600000.SH\",\"SUSPEND_START_TIME\":\"2026-11-09 09:30:00\",\"SUSPEND_END_TIME\":\"2026-11-10 15:00:00\"}");
    var intraday =
        json.readTree(
            "{\"SECUCODE\":\"000001.SZ\",\"SUSPEND_START_TIME\":\"2026-11-10 10:00:00\",\"SUSPEND_END_TIME\":null}");
    var resumed =
        json.readTree(
            "{\"SECUCODE\":\"600001.SH\",\"SUSPEND_START_TIME\":\"2026-11-09 09:30:00\",\"SUSPEND_END_TIME\":\"2026-11-09 15:00:00\"}");
    var result =
        FreeMarketNormalizer.normalize(
            input(
                List.of(roster("sh600000"), roster("sz000001"), roster("sh600001")),
                List.of(),
                List.of(halt, intraday, resumed)));
    assertEquals(
        "0",
        result.universe().stream()
            .filter(row -> row.get("code").equals("sh.600000"))
            .findFirst()
            .orElseThrow()
            .get("tradeStatus"));
    assertEquals(
        2,
        result.universe().stream().filter(row -> row.get("tradeStatus").equals("UNKNOWN")).count());
  }

  @Test
  void actualTradesTakePrecedenceOverStaleSuspensionRecordsAndWrongMarketIsRejected()
      throws Exception {
    var halt =
        json.readTree(
            "{\"SECUCODE\":\"600000.SH\",\"SUSPEND_START_TIME\":\"2026-11-09 09:30:00\",\"SUSPEND_END_TIME\":null}");
    var result =
        FreeMarketNormalizer.normalize(
            input(List.of(roster("sh600000")), List.of(quote("600000", 1)), List.of(halt)));
    assertEquals("1", result.universe().getFirst().get("tradeStatus"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            FreeMarketNormalizer.normalize(
                input(List.of(roster("sh600000")), List.of(quote("600000", 0)), List.of())));
  }

  @Test
  void unarchivedHistoryAndIntradayRequestsNeverFetchNetwork() {
    AtomicInteger reads = new AtomicInteger();
    try (var provider =
        new EastmoneyResearchProvider(
            clock,
            uri -> {
              reads.incrementAndGet();
              return null;
            })) {
      assertThrows(
          ProviderDataUnavailableException.class, () -> provider.snapshot(date.minusDays(1)));
      assertFalse(provider.historicalSnapshots());
      assertEquals(Set.of(MarketCapability.MARKET_SNAPSHOT), provider.capabilities());
      assertEquals(Set.of("SINA", "EASTMONEY"), provider.requiredSources());
    }
    try (var provider =
        new EastmoneyResearchProvider(
            Clock.fixed(Instant.parse("2026-11-10T06:00:00Z"), ZoneOffset.UTC),
            uri -> {
              reads.incrementAndGet();
              return null;
            })) {
      assertThrows(ProviderDataUnavailableException.class, () -> provider.snapshot(date));
    }
    assertEquals(0, reads.get());
  }

  @Test
  void repeatedRosterPageFailsInsteadOfPretendingItIsACompleteUniverse() {
    try (var provider =
        new EastmoneyResearchProvider(
            clock,
            uri -> {
              if (uri.toString().contains("StockCount"))
                return json.getNodeFactory().textNode("4000");
              return json.createArrayNode().add(roster("sh600000"));
            })) {
      assertThrows(IllegalStateException.class, () -> provider.snapshot(date));
    }
  }

  @Test
  void completePaginationIsCachedAndEveryOriginalPageRemainsAuditable() {
    AtomicInteger requests = new AtomicInteger();
    try (var provider =
        new EastmoneyResearchProvider(
            clock,
            uri -> {
              requests.incrementAndGet();
              String url = uri.toString();
              if (url.contains("StockCount")) return json.getNodeFactory().textNode("4000");
              if (url.contains("getHQNodeData")) {
                int page = Integer.parseInt(url.split("page=")[1].split("&")[0]);
                ArrayNode rows = json.createArrayNode();
                for (int i = (page - 1) * 100; i < page * 100; i++)
                  rows.add(roster("sh" + (600000 + i)));
                return rows;
              }
              if (url.contains("clist/get")) {
                int page = Integer.parseInt(url.split("pn=")[1].split("&")[0]);
                ArrayNode rows = json.createArrayNode();
                for (int i = (page - 1) * 100; i < page * 100; i++)
                  rows.add(quote(String.valueOf(600000 + i), 1));
                return json.createObjectNode()
                    .put("rc", 0)
                    .set("data", json.createObjectNode().put("total", 4000).set("diff", rows));
              }
              return json.createObjectNode()
                  .put("success", true)
                  .set(
                      "result",
                      json.createObjectNode()
                          .put("pages", 1)
                          .put("count", 0)
                          .set("data", json.createArrayNode()));
            })) {
      var first = provider.snapshot(date);
      int used = requests.get();
      assertEquals(4000, first.universe().size());
      assertEquals(4000, first.daily().size());
      assertSame(first, provider.snapshot(date));
      assertEquals(used, requests.get());
      assertTrue(first.upstreamJson().contains("rosterSource"));
      assertEquals(40, JsonsForTest.parse(first.upstreamJson()).path("rosterPages").size());
    }
  }

  private JsonNode roster(String symbol) {
    return json.createObjectNode().put("symbol", symbol).put("code", symbol.substring(2));
  }

  private com.fasterxml.jackson.databind.node.ObjectNode quote(String code, int market) {
    return json.createObjectNode()
        .put("f12", code)
        .put("f13", market)
        .put("f2", 11)
        .put("f18", 10)
        .put("f5", 100)
        .put("f6", 123456.78)
        .put("f17", 10.5)
        .put("f15", 12)
        .put("f16", 10)
        .put("f124", Instant.parse("2026-11-10T07:30:00Z").getEpochSecond());
  }

  private FreeMarketInput input(
      List<JsonNode> roster, List<JsonNode> quotes, List<JsonNode> suspensions) {
    return FreeMarketInput.builder()
        .date(date)
        .roster(roster)
        .quotes(quotes)
        .suspensions(suspensions)
        .upstreamJson("{}")
        .build();
  }

  /**
   * 测试JSON读取辅助，避免检查结果退回对字符串长度的猜测。
   *
   * @author chenxuefeng
   */
  private static final class JsonsForTest {
    static JsonNode parse(String value) {
      try {
        return new ObjectMapper().readTree(value);
      } catch (Exception error) {
        throw new AssertionError(error);
      }
    }
  }
}

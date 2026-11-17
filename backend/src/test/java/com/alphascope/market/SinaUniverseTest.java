package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.common.util.Jsons;
import com.alphascope.market.provider.research.SinaUniverseProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 当前在市目录的独立分页与时点契约；不靠报价是否非零来筛选股票。
 *
 * @author chenxuefeng
 */
class SinaUniverseTest {
  private final ObjectMapper json = new ObjectMapper();
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T06:00:00Z"), ZoneOffset.UTC);
  private final LocalDate date = LocalDate.of(2026, 11, 17);

  private JsonNode response(URI uri) {
    if (uri.toString().contains("StockCount")) return json.getNodeFactory().textNode("4000");
    int page = Integer.parseInt(uri.getQuery().split("page=")[1].split("&")[0]);
    var rows = json.createArrayNode();
    for (int i = (page - 1) * 100; i < page * 100; i++) {
      String code =
          i < 2
              ? "bj" + (920001 + i)
              : i < 2000
                  ? "sh" + (600000 + i - 2)
                  : "sz" + String.format(java.util.Locale.ROOT, "%06d", i - 2000);
      rows.add(
          json.createObjectNode()
              .put("symbol", code)
              .put("code", code.substring(2))
              .put("name", "测试" + i)
              .put("trade", "0"));
    }
    return rows;
  }

  @Test
  void verifiesEveryPageAndIndependentCountBeforeExtractingBeijing() {
    var reads = new AtomicInteger();
    var provider =
        new SinaUniverseProvider(
            clock,
            uri -> {
              reads.incrementAndGet();
              return response(uri);
            });
    var batch = provider.fetch(date);
    assertEquals(2, batch.members().size());
    assertEquals("920001.BJ", batch.members().getFirst().symbol());
    assertEquals(43, reads.get());
    var evidence = Jsons.evidenceObject(Jsons.read(batch.evidenceJson(), Object.class));
    assertEquals(4000, evidence.get("expectedAllA"));
    assertTrue(batch.evidenceJson().contains("firstPageConfirmed"));
    // trade=0仍属于在市目录，不能把报价缺失推断为不在市或停牌。
    assertEquals(date, batch.businessDate());
  }

  @Test
  void pastDateNeverQueriesTheCurrentDirectory() {
    var reads = new AtomicInteger();
    var provider =
        new SinaUniverseProvider(
            clock,
            uri -> {
              reads.incrementAndGet();
              return response(uri);
            });
    assertThrows(
        com.alphascope.market.provider.ProviderDataUnavailableException.class,
        () -> provider.fetch(date.minusDays(1)));
    assertEquals(0, reads.get());
  }

  @Test
  void duplicateAndTruncatedPagesNeverPublishPartialMembers() {
    var duplicate =
        new SinaUniverseProvider(
            clock,
            uri ->
                uri.toString().contains("page=2&")
                    ? response(URI.create(uri.toString().replace("page=2&", "page=1&")))
                    : response(uri));
    assertThrows(IllegalStateException.class, () -> duplicate.fetch(date));
    var truncated =
        new SinaUniverseProvider(
            clock,
            uri -> {
              var rows = response(uri);
              if (rows.isArray()) ((com.fasterxml.jackson.databind.node.ArrayNode) rows).remove(0);
              return rows;
            });
    assertThrows(IllegalStateException.class, () -> truncated.fetch(date));
  }

  @Test
  void intradayPriceChangesDoNotPretendThatMembershipChanged() {
    var firstReads = new AtomicInteger();
    var provider =
        new SinaUniverseProvider(
            clock,
            uri -> {
              var rows = response(uri);
              if (uri.getQuery().startsWith("page=1&") && firstReads.incrementAndGet() > 1)
                ((com.fasterxml.jackson.databind.node.ObjectNode) rows.get(0))
                    .put("trade", "999.99");
              return rows;
            });
    assertEquals(2, provider.fetch(date).members().size());
  }

  @Test
  void changedCountOrFirstPageIsRejectedEvenWhenPagesLookComplete() {
    var counts = new AtomicInteger();
    var changed =
        new SinaUniverseProvider(
            clock,
            uri ->
                uri.toString().contains("StockCount") && counts.incrementAndGet() == 2
                    ? json.getNodeFactory().textNode("4001")
                    : response(uri));
    assertThrows(IllegalStateException.class, () -> changed.fetch(date));
    var firsts = new AtomicInteger();
    var changedFirst =
        new SinaUniverseProvider(
            clock,
            uri -> {
              var rows = response(uri);
              if (uri.toString().contains("page=1&") && firsts.incrementAndGet() == 2)
                ((com.fasterxml.jackson.databind.node.ObjectNode) rows.get(0)).put("name", "更名");
              return rows;
            });
    assertThrows(IllegalStateException.class, () -> changedFirst.fetch(date));
  }
}

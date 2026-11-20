package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alphascope.common.util.Jsons;
import com.alphascope.market.calculator.HistoricalFullMarketAssembler;
import com.alphascope.market.calculator.UniverseCoverageCalculator;
import com.alphascope.market.dto.HistoricalFullMarketArchive;
import com.alphascope.market.dto.HistoricalFullMarketArchive.Comparison;
import com.alphascope.market.dto.HistoricalFullMarketArchive.Member;
import com.alphascope.market.dto.HistoricalFullMarketArchive.Quote;
import com.alphascope.market.dto.HistoricalFullMarketArchive.Suspension;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 历史导入门槛和不可变截面核验，使用本地构造的测试资料。
 *
 * @author chenxuefeng
 */
class HistoricalFullMarketAssemblerTest {
  private final LocalDate date = LocalDate.of(2026, 11, 17);
  private final OffsetDateTime exported = OffsetDateTime.parse("2026-11-18T09:12:30+08:00");

  @Test
  void verifiesTheFullRosterAndRecordsActualFactorAndImportTime() {
    var input = sample();
    var imported = exported.plusHours(1).toLocalDateTime();
    var snapshot = HistoricalFullMarketAssembler.assemble(input, imported);
    assertEquals(imported, snapshot.getFetchedAt());
    assertEquals("BIGQUANT", snapshot.getSource());
    assertTrue(UniverseCoverageCalculator.calculate(snapshot).coverage().rosterVerified());
    var raw = Jsons.read(snapshot.getRawJson(), JsonNode.class);
    assertEquals(3999, raw.path("daily").size());
    assertEquals("1.25", raw.path("daily").get(0).path("factor").asText());
    assertEquals("SUPPLIER_HISTORY_EXPORT", raw.path("rosterEvidence").path("origin").asText());
  }

  @Test
  void rejectsMissingTradingSharesAndFactorsRatherThanCountingThemAsSuspended() {
    var input = sample();
    var latest = input.quotes().removeLast();
    var now = exported.plusHours(1).toLocalDateTime();
    var missing =
        assertThrows(
            IllegalArgumentException.class,
            () -> HistoricalFullMarketAssembler.assemble(input, now));
    assertTrue(missing.getMessage().contains(latest.symbol()));
    input
        .quotes()
        .add(
            new Quote(
                latest.symbol(), latest.close(), latest.previousClose(), latest.amount(), null));
    assertThrows(
        IllegalArgumentException.class, () -> HistoricalFullMarketAssembler.assemble(input, now));
  }

  @Test
  void rejectsAnUnverifiedRosterCountAndAnExportBeforeClose() {
    var input = sample();
    var now = exported.plusHours(1).toLocalDateTime();
    var missing =
        new HistoricalFullMarketArchive(
            date,
            input.source(),
            exported,
            Map.of("SSE", 2000, "SZSE", 1900, "BSE", 101),
            input.rosterSources(),
            input.members(),
            input.quotes());
    assertThrows(
        IllegalArgumentException.class, () -> HistoricalFullMarketAssembler.assemble(missing, now));
    var early =
        new HistoricalFullMarketArchive(
            date,
            input.source(),
            exported.minusDays(1),
            input.expectedCounts(),
            input.rosterSources(),
            input.members(),
            input.quotes());
    assertThrows(
        IllegalArgumentException.class, () -> HistoricalFullMarketAssembler.assemble(early, now));
  }

  @Test
  void rejectsSelfReportedRosterAndAnIntradayHaltAsFullDayEvidence() {
    var input = sample();
    var now = exported.plusHours(1).toLocalDateTime();
    var supplierOnly =
        new HistoricalFullMarketArchive(
            date,
            input.source(),
            exported,
            input.expectedCounts(),
            Map.of(
                "SSE",
                "https://bigquant.com/data",
                "SZSE",
                "https://www.szse.cn/fixture",
                "BSE",
                "https://www.bse.cn/fixture"),
            input.members(),
            input.quotes());
    assertThrows(
        IllegalArgumentException.class,
        () -> HistoricalFullMarketAssembler.assemble(supplierOnly, now));
    var last = input.members().getLast();
    input
        .members()
        .set(
            input.members().size() - 1,
            new Member(
                last.symbol(),
                true,
                new Suspension(
                    date.atTime(10, 0),
                    date.atTime(15, 0),
                    "https://www.bse.cn/disclosure/fixture")));
    assertThrows(
        IllegalArgumentException.class, () -> HistoricalFullMarketAssembler.assemble(input, now));
  }

  @Test
  void reconcilesPublicHistoryAndKeepsUnknownPreviousCloseAndFactorExplicit() {
    var legacy = sample();
    var quote = legacy.quotes().getFirst();
    legacy.quotes().set(0, new Quote(quote.symbol(), quote.close(), null, quote.amount(), null));
    var comparison =
        new Comparison(
            date,
            3901,
            100,
            "https://www.csdata.cn/report.pdf",
            "b".repeat(64),
            null,
            Map.of("689009.SH", "https://www.sse.com.cn/disclosure/cdr.shtml"));
    var input =
        new HistoricalFullMarketArchive(
            date,
            HistoricalFullMarketAssembler.PUBLIC_HISTORY_SOURCE,
            exported,
            legacy.expectedCounts(),
            legacy.rosterSources(),
            legacy.members(),
            legacy.quotes(),
            comparison);
    var snapshot =
        HistoricalFullMarketAssembler.assemble(input, exported.plusHours(1).toLocalDateTime());
    assertTrue(UniverseCoverageCalculator.calculate(snapshot).coverage().rosterVerified());
    var metrics = Jsons.read(snapshot.getMetricsJson(), JsonNode.class);
    assertEquals(3999, metrics.path("observed").asInt());
    assertTrue(metrics.path("advancers").isNull());
    var raw = Jsons.read(snapshot.getRawJson(), JsonNode.class);
    assertTrue(
        java.util.stream.StreamSupport.stream(raw.path("daily").spliterator(), false)
            .anyMatch(
                row ->
                    "sh.600000".equals(row.path("code").asText())
                        && row.path("factorUnavailable").asBoolean()
                        && !row.has("factor")));
    var impossible =
        new HistoricalFullMarketArchive(
            date,
            input.source(),
            exported,
            input.expectedCounts(),
            input.rosterSources(),
            input.members(),
            input.quotes(),
            new Comparison(
                date,
                3901,
                101,
                comparison.sourceUrl(),
                comparison.sha256(),
                null,
                comparison.excludedInstruments()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            HistoricalFullMarketAssembler.assemble(
                impossible, exported.plusHours(1).toLocalDateTime()));
  }

  private HistoricalFullMarketArchive sample() {
    var members = new ArrayList<Member>();
    var quotes = new ArrayList<Quote>();
    for (int index = 0; index < 4000; index++) {
      String symbol =
          index < 2000
              ? String.format(Locale.ROOT, "%06d.SH", 600000 + index)
              : index < 3900
                  ? String.format(Locale.ROOT, "%06d.SZ", index - 2000)
                  : String.format(Locale.ROOT, "%06d.BJ", 920000 + index - 3900);
      boolean suspended = index == 3999;
      members.add(
          new Member(
              symbol,
              suspended,
              suspended
                  ? new Suspension(
                      date.atTime(9, 30),
                      date.atTime(15, 0),
                      "https://www.bse.cn/disclosure/fixture")
                  : null));
      if (!suspended)
        quotes.add(
            new Quote(
                symbol,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.valueOf(10000),
                new BigDecimal("1.25")));
    }
    return new HistoricalFullMarketArchive(
        date,
        "BIGQUANT",
        exported,
        Map.of("SSE", 2000, "SZSE", 1900, "BSE", 100),
        Map.of(
            "SSE",
            "https://www.sse.com.cn/fixture",
            "SZSE",
            "https://www.szse.cn/fixture",
            "BSE",
            "https://www.bse.cn/fixture"),
        members,
        quotes);
  }
}

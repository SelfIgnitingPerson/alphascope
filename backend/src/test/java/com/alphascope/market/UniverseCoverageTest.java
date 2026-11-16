package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.common.util.Jsons;
import com.alphascope.market.calculator.UniverseCoverageCalculator;
import com.alphascope.market.entity.MarketBreadthSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * 全A准入依赖完整声明和逐证券对齐，不能凭沪深样本数或非空列表通过。
 *
 * @author chenxuefeng
 */
class UniverseCoverageTest {
  private final LocalDate date = LocalDate.of(2026, 10, 30);

  @Test
  void shenzhenShanghaiOnlyIsMeasuredButCannotBecomeFullA() {
    var row = row(List.of("sh.600000", "sz.000001"), false, false);
    var result = UniverseCoverageCalculator.calculate(row);
    assertFalse(result.coverage().rosterVerified());
    assertEquals(0, result.coverage().expected());
    assertEquals(2, result.coverage().observed());
    assertEquals(1, result.exchangeMembers().get("SSE"));
    assertEquals(1, result.exchangeMembers().get("SZSE"));
    assertTrue(result.missing().stream().anyMatch(value -> value.contains("BSE")));
  }

  @Test
  void explicitlyDeclaredFullRosterWithAllThreeExchangesCanPassTheGate() {
    var row = row(List.of("sh.600000", "sz.000001", "bj.920001"), true, false);
    var result = UniverseCoverageCalculator.calculate(row);
    assertTrue(result.coverage().rosterVerified());
    assertTrue(result.coverage().sufficient(.95));
    assertEquals(3, result.coverage().expected());
    assertTrue(result.missing().isEmpty());
  }

  @Test
  void fullLabelWithoutRosterProofOrWithPriceGapDoesNotPass() {
    var absent = row(List.of("sh.600000", "sz.000001", "bj.920001"), false, false);
    absent.setUniverseScope("FULL_A");
    assertFalse(UniverseCoverageCalculator.calculate(absent).coverage().rosterVerified());
    var gap = row(List.of("sh.600000", "sz.000001", "bj.920001"), true, true);
    assertFalse(UniverseCoverageCalculator.calculate(gap).coverage().rosterVerified());
  }

  @Test
  void duplicateOrWrongDeclaredFingerprintIsRejected() {
    var duplicate = row(List.of("sh.600000", "sh.600000", "bj.920001"), true, false);
    assertFalse(UniverseCoverageCalculator.calculate(duplicate).coverage().rosterVerified());
    var changed = row(List.of("sh.600000", "sz.000001", "bj.920001"), true, false);
    changed.setRawJson(changed.getRawJson().replace("rosterSha256", "missingHash"));
    assertFalse(UniverseCoverageCalculator.calculate(changed).coverage().rosterVerified());
  }

  @Test
  void unknownStatusStillCountsObservedMembersButCannotPassFullRosterGate() throws Exception {
    var row = row(List.of("sh.600000", "sz.000001", "bj.920001"), true, true);
    var raw = new com.fasterxml.jackson.databind.ObjectMapper().readTree(row.getRawJson());
    ((com.fasterxml.jackson.databind.node.ObjectNode) raw.path("universe").get(2))
        .put("tradeStatus", "UNKNOWN");
    row.setRawJson(Jsons.write(raw));
    row.setContentHash(Jsons.hash(row.getRawJson()));
    var result = UniverseCoverageCalculator.calculate(row);
    assertEquals(3, result.exchangeMembers().values().stream().mapToInt(Integer::intValue).sum());
    assertFalse(result.coverage().rosterVerified());
    assertTrue(result.missing().stream().anyMatch(value -> value.contains("尚未核验")));
  }

  private MarketBreadthSnapshot row(List<String> codes, boolean proof, boolean gap) {
    var members = codes.stream().map(code -> Map.of("code", code, "tradeStatus", "1")).toList();
    var daily =
        codes.stream()
            .limit(codes.size() - (gap ? 1 : 0))
            .map(
                code ->
                    Map.of(
                        "code",
                        code,
                        "date",
                        date.toString(),
                        "tradestatus",
                        "1",
                        "close",
                        "10",
                        "amount",
                        "1000"))
            .toList();
    var normalized = new TreeSet<>(List.of("600000.SH", "000001.SZ", "920001.BJ"));
    var raw = new java.util.HashMap<String, Object>();
    raw.put("universe", members);
    raw.put("daily", daily);
    if (proof)
      raw.put(
          "universeProof",
          Map.of(
              "businessDate",
              date.toString(),
              "source",
              "TEST_FIXTURE",
              "scope",
              "FULL_A",
              "completeRoster",
              true,
              "exchanges",
              List.of("SSE", "SZSE", "BSE"),
              "expectedCount",
              codes.size(),
              "rosterSha256",
              Jsons.hash(Jsons.write(normalized)),
              "sourceUrl",
              "https://example.test/dated-roster"));
    var row = new MarketBreadthSnapshot();
    row.setId(1L);
    row.setTradeDate(date);
    row.setSource("TEST_FIXTURE");
    row.setUniverseScope(proof ? "FULL_A" : "SH_SZ_A");
    row.setRawJson(Jsons.write(raw));
    row.setContentHash(Jsons.hash(row.getRawJson()));
    return row;
  }
}

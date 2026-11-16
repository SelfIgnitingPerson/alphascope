package com.alphascope.fundamental;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.fundamental.provider.EastmoneyFinancialProvider;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import org.junit.jupiter.api.Test;

/**
 * 公开财报供应商解析、金额单位及披露时点的验证。
 *
 * @author chenxuefeng
 */
class FinancialProviderTest {
  final ObjectMapper json = new ObjectMapper();
  final LocalDateTime fetched = LocalDateTime.of(2026, 10, 31, 18, 0);

  JsonNode income(String update) throws Exception {
    return json.readTree(
        """
      [{"SECUCODE":"300476.SZ","REPORT_DATE":"2026-06-30 00:00:00","REPORT_TYPE":"中报",
        "NOTICE_DATE":"2026-08-28 00:00:00","OPERATE_INCOME":100000000,"OPERATE_COST":70000000,
        "PARENT_NETPROFIT":10000000,"OPERATE_INCOME_YOY":25.5,"PARENT_NETPROFIT_YOY":4.25%s}]
      """
            .formatted(update.isEmpty() ? "" : ",\"UPDATE_DATE\":\"" + update + " 00:00:00\""));
  }

  JsonNode cash() throws Exception {
    return json.readTree(
        """
      [{"SECUCODE":"300476.SZ","REPORT_DATE":"2026-06-30 00:00:00","REPORT_TYPE":"中报",
        "NOTICE_DATE":"2026-08-28 00:00:00","UPDATE_DATE":"2026-08-28 00:00:00","NETCASH_OPERATE":-123456789}]
      """);
  }

  @Test
  void preservesUnitsAndConservativeDateAvailability() throws Exception {
    var s =
        EastmoneyFinancialProvider.parse(
                "300476.SZ", income("2026-08-28"), cash(), fetched, "https://example.test")
            .getFirst();
    assertEquals("H1_CUMULATIVE", s.reportType());
    assertEquals(100000000, s.revenue().doubleValue());
    assertEquals(.255, s.revenueYoy().doubleValue(), 1e-10);
    assertEquals(.0425, s.profitYoy().doubleValue(), 1e-10);
    assertEquals(.3, s.grossMargin().doubleValue(), 1e-10);
    assertEquals(-123456789, s.operatingCashflow().doubleValue());
    assertEquals(LocalDateTime.of(2026, 8, 29, 0, 0), s.availableAt());
    assertEquals(64, s.hash().length());
  }

  @Test
  void revisedVersionCannotBeBackdatedToOriginalNotice() throws Exception {
    var s =
        EastmoneyFinancialProvider.parse(
                "300476.SZ", income("2026-10-30"), cash(), fetched, "https://example.test")
            .getFirst();
    assertEquals(LocalDateTime.of(2026, 10, 31, 0, 0), s.availableAt());
  }

  @Test
  void unknownRevisionAvailabilityStartsAtFetch() throws Exception {
    var s =
        EastmoneyFinancialProvider.parse(
                "300476.SZ", income(""), cash(), fetched, "https://example.test")
            .getFirst();
    assertEquals(fetched, s.availableAt());
  }

  @Test
  void cannotMergeDifferentPeriodsSymbolsOrQuarterlyBasis() throws Exception {
    var c = cash().deepCopy();
    ((ObjectNode) c.get(0)).put("REPORT_TYPE", "单季度");
    var s =
        EastmoneyFinancialProvider.parse(
                "300476.SZ", income("2026-08-28"), c, fetched, "https://example.test")
            .getFirst();
    assertNull(s.operatingCashflow());
    assertTrue(
        EastmoneyFinancialProvider.parse(
                "002837.SZ", income("2026-08-28"), cash(), fetched, "https://example.test")
            .isEmpty());
  }

  @Test
  void identicalResponsesKeepFingerprintWhileRevisionsChangeIt() throws Exception {
    var a =
        EastmoneyFinancialProvider.parse(
                "300476.SZ", income("2026-08-28"), cash(), fetched, "https://example.test")
            .getFirst();
    var b =
        EastmoneyFinancialProvider.parse(
                "300476.SZ",
                income("2026-08-28"),
                cash(),
                fetched.plusDays(1),
                "https://example.test")
            .getFirst();
    var c =
        EastmoneyFinancialProvider.parse(
                "300476.SZ", income("2026-10-30"), cash(), fetched, "https://example.test")
            .getFirst();
    assertEquals(a.hash(), b.hash());
    assertNotEquals(a.hash(), c.hash());
  }
}

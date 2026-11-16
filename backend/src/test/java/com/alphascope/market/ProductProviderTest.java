package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.market.provider.EastmoneyIndexProvider;
import com.alphascope.market.provider.InstrumentDirectoryProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 备用指数与新证券目录的边界验证，缺字段或不匹配代码不能登记成真实行情。
 *
 * @author chenxuefeng
 */
class ProductProviderTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void directoryRejectsWrongMarketAndNonAshareCandidates() throws Exception {
    var provider = new InstrumentDirectoryProvider(json);
    var result =
        provider.parse(
            json.readTree(
                "{\"QuotationCodeTable\":{\"Data\":[{\"Code\":\"600519\",\"Name\":\"贵州茅台\",\"QuoteID\":\"1.600519\",\"SecurityTypeName\":\"沪A股\"},{\"Code\":\"600519\",\"Name\":\"错误市场\",\"QuoteID\":\"0.600519\",\"SecurityTypeName\":\"A股\"},{\"Code\":\"510300\",\"Name\":\"基金\",\"QuoteID\":\"1.510300\",\"SecurityTypeName\":\"ETF\"}]}}"));
    assertEquals(1, result.size());
    assertEquals("600519.SH", result.getFirst().symbol());
  }

  @Test
  void indexKeepsRealAmountAndConvertsVolumeWithoutPretendingToProvideCalendar() throws Exception {
    try (var provider = new EastmoneyIndexProvider()) {
      LocalDate date = LocalDate.of(2026, 10, 30);
      var rows =
          provider.parse(
              json.readTree(
                  "{\"data\":{\"code\":\"000300\",\"klines\":[\"2026-10-30,4000,4010,4020,3990,12345,234567890\"]}}"),
              "000300.SH",
              date,
              date,
              LocalDateTime.of(2026, 10, 30, 16, 0));
      assertEquals(1, rows.size());
      assertEquals(0, new java.math.BigDecimal("1234500").compareTo(rows.getFirst().volume()));
      assertEquals(0, new java.math.BigDecimal("234567890").compareTo(rows.getFirst().amount()));
      assertThrows(UnsupportedOperationException.class, () -> provider.fetchCalendar(date, date));
      assertThrows(
          IllegalStateException.class,
          () ->
              provider.parse(
                  json.readTree("{\"data\":{\"code\":\"000001\",\"klines\":[]}}"),
                  "000300.SH",
                  date,
                  date,
                  LocalDateTime.now()));
    }
  }
}

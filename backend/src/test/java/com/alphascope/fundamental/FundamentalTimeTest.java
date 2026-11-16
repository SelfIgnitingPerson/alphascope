package com.alphascope.fundamental;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.common.util.Jsons;
import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.fundamental.service.impl.FundamentalServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * 财报业务时间与冻结 JSON 日期序列化的验证。
 *
 * @author chenxuefeng
 */
class FundamentalTimeTest {
  @Test
  void typedFinancialTimesRemainLocalInFrozenEvidence() {
    var published = LocalDateTime.of(2026, 8, 28, 0, 0);
    var row = new FinancialReport();
    row.setPublishedAt(published);
    row.setAvailableAt(published.plusDays(1));
    row.setPeriodEnd(LocalDate.parse("2026-06-30"));
    row.setReportType("H1_CUMULATIVE");
    var service = new FundamentalServiceImpl((symbol, cutoff) -> List.of(row));
    var result = service.reports("300476.SZ", LocalDate.of(2026, 10, 30)).getFirst();
    assertEquals(published, result.getPublishedAt());
    assertTrue(Jsons.write(result).contains("2026-08-28T00:00:00"));
    assertEquals(LocalDate.of(2026, 6, 30), result.getPeriodEnd());
  }
}

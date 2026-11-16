package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.market.provider.MarketFailureSummary;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 用真实日期证据生成用户摘要，不根据条数、当前时间或来源名称猜原因。
 *
 * @author chenxuefeng
 */
class MarketFailureSummaryTest {
  @Test
  void namesTheStockMissingDayAndLatestSavedDayWithoutMachineCodes() {
    String text =
        MarketFailureSummary.describe(
            MarketFailureSummary.Evidence.builder()
                .symbol("002837.SZ")
                .name("英维克")
                .latestDate(LocalDate.of(2026, 11, 11))
                .missingDates(List.of(LocalDate.of(2026, 11, 12)))
                .build());
    assertTrue(text.contains("英维克（002837.SZ）缺少2026-11-12的收盘行情"));
    assertTrue(text.contains("本地已保存到2026-11-11"));
    assertFalse(text.contains("485"));
    assertFalse(text.contains("尚未发布"));
  }

  @Test
  void describesARefreshFailureWithoutInventingAMissingDay() {
    String text =
        MarketFailureSummary.describe(
            MarketFailureSummary.Evidence.builder()
                .symbol("000300.SH")
                .missingDates(List.of())
                .build());
    assertTrue(text.contains("刷新或完整性核验没有通过"));
    assertFalse(text.contains("缺少"));
  }
}

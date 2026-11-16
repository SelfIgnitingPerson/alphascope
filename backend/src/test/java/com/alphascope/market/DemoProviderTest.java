package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.market.provider.DemoMarketDataProvider;
import java.time.*;
import org.junit.jupiter.api.Test;

/**
 * 演示通道数据、复权与日历行为的验证。
 *
 * @author chenxuefeng
 */
class DemoProviderTest {
  @Test
  void deterministicBarsAreAlwaysSynthetic() {
    var p = new DemoMarketDataProvider();
    var a = p.fetchDailyBars("300476.SZ", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30));
    var b = p.fetchDailyBars("300476.SZ", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30));
    assertEquals(a.size(), b.size());
    for (int i = 0; i < a.size(); i++) {
      assertTrue(a.get(i).isSynthetic());
      assertEquals(a.get(i).close(), b.get(i).close());
      assertTrue(a.get(i).tradeDate().getDayOfWeek().getValue() < 6);
    }
  }
}

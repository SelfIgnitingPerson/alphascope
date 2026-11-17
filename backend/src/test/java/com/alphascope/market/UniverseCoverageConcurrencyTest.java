package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.market.entity.MarketBreadthSnapshot;
import com.alphascope.market.mapper.MarketBreadthMapper;
import com.alphascope.market.provider.research.ResearchDataRouter;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.service.impl.UniverseCoverageServiceImpl;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 同一不可变截面并发只解析一次，新指纹必须重新核验。
 *
 * @author chenxuefeng
 */
class UniverseCoverageConcurrencyTest {
  @Test
  void coldReadsShareOneVerificationWithoutCachingOnlyByDate() throws Exception {
    var mapper = mock(MarketBreadthMapper.class);
    var market = mock(MarketService.class);
    var router = mock(ResearchDataRouter.class);
    when(router.sources()).thenReturn(List.of("BAOSTOCK"));
    var date = LocalDate.of(2026, 11, 16);
    var row = new MarketBreadthSnapshot();
    row.setId(1L);
    row.setTradeDate(date);
    row.setSource("BAOSTOCK");
    row.setUniverseScope("SH_SZ_A");
    row.setContentHash("first");
    when(mapper.coverageCandidates(date)).thenReturn(List.of(row));
    when(mapper.coverageRaw(1))
        .thenAnswer(
            i -> {
              Thread.sleep(100);
              return "{}";
            });
    var service =
        new UniverseCoverageServiceImpl(
            mapper,
            market,
            router,
            mock(com.alphascope.market.service.MarketUniverseService.class));
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(8)) {
      var results = new ArrayList<Future<?>>();
      for (int i = 0; i < 8; i++)
        results.add(
            executor.submit(
                () -> {
                  start.await();
                  return service.inspect(date);
                }));
      start.countDown();
      for (var result : results) assertNotNull(result.get(3, TimeUnit.SECONDS));
    }
    verify(mapper, times(1)).coverageRaw(1);
    row.setContentHash("second");
    service.inspect(date);
    verify(mapper, times(2)).coverageRaw(1);
  }
}

package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.mapper.ResearchCollectionMapper;
import com.alphascope.market.dto.MarketBarQuery;
import com.alphascope.market.entity.Instrument;
import com.alphascope.market.mapper.MarketMapper;
import com.alphascope.market.provider.MarketDataProvider;
import com.alphascope.market.provider.research.ResearchDataRouter;
import com.alphascope.market.service.MarketWriteService;
import com.alphascope.market.service.impl.MarketServiceImpl;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 十年研究加固定预热是一个同源窗口，不通过分段请求混合供应商。
 *
 * @author chenxuefeng
 */
class ResearchWindowTest {
  @Test
  void tenYearSignalWindowLoadsWarmupWithoutRelaxingThePublicPriceQuery() {
    var mapper = mock(MarketMapper.class);
    var provider = mock(MarketDataProvider.class);
    var instrument = new Instrument();
    instrument.setId(1L);
    when(mapper.instrument("300476.SZ")).thenReturn(instrument);
    when(mapper.latestSourceAt(anyLong(), anyBoolean(), any())).thenReturn("TEST_FIXTURE");
    when(provider.acceptsSource("TEST_FIXTURE")).thenReturn(true);
    var service =
        new MarketServiceImpl(
            mapper,
            provider,
            mock(MarketWriteService.class),
            mock(ResearchCollectionMapper.class),
            mock(ResearchDataRouter.class),
            new AlphaScopeProperties());
    var range =
        DateRangeRequest.builder()
            .start(LocalDate.of(2016, 10, 30))
            .end(LocalDate.of(2026, 10, 30))
            .build();
    service.researchBars("300476.SZ", range);
    var query = ArgumentCaptor.forClass(MarketBarQuery.class);
    verify(mapper).bars(query.capture());
    assertEquals(LocalDate.of(2016, 6, 30), query.getValue().getStartDate());
    assertEquals("TEST_FIXTURE", query.getValue().getSource());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.bars(
                "300476.SZ",
                DateRangeRequest.builder()
                    .start(range.getStart().minusMonths(4))
                    .end(range.getEnd())
                    .build()));
  }
}

package com.alphascope.intelligence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.vo.UserView;
import com.alphascope.intelligence.entity.ResearchObservation;
import com.alphascope.intelligence.mapper.ResearchObservationMapper;
import com.alphascope.intelligence.service.HoldingResearchService;
import com.alphascope.intelligence.service.impl.ResearchObservationServiceImpl;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import com.alphascope.operations.mapper.CapacityMapper;
import com.alphascope.portfolio.entity.PortfolioAccount;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.portfolio.vo.PortfolioContext;
import com.alphascope.portfolio.vo.WatchlistView;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 实际形成时间和不可变留档的边界，不能把事后回看伪装成前瞻记录。
 *
 * @author chenxuefeng
 */
class ObservationJournalTest {
  private final LocalDate date = LocalDate.of(2026, 11, 18);
  private final ResearchObservationMapper mapper = mock(ResearchObservationMapper.class);
  private final HoldingResearchService research = mock(HoldingResearchService.class);
  private final PortfolioService portfolio = mock(PortfolioService.class);
  private final CapacityMapper capacity = mock(CapacityMapper.class);
  private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

  private ResearchObservationServiceImpl service(String time) {
    return new ResearchObservationServiceImpl(
        mapper,
        research,
        portfolio,
        capacity,
        Clock.fixed(Instant.parse(time), ZoneOffset.UTC),
        transactions);
  }

  private void given(LocalDate at) {
    when(portfolio.watchlistView())
        .thenReturn(List.of(new WatchlistView("600000.SH", "测试", 1, true)));
    when(portfolio.context(at)).thenReturn(new PortfolioContext(List.of(), new PortfolioAccount()));
    when(research.read(eq("600000.SH"), any()))
        .thenReturn(
            HoldingResearchView.builder()
                .version("HOLDING_RESEARCH_V5")
                .symbol("600000.SH")
                .name("测试")
                .asOf(at)
                .quoteDate(at)
                .close(50d)
                .source("SINA")
                .inputSources(Set.of("SINA", "EASTMONEY_FINANCIAL"))
                .horizons(List.of())
                .priceVolume(PriceVolumeStudy.builder().status("AVAILABLE").build())
                .build());
  }

  @Test
  void realDayCaptureIsStableAndReuseDoesNotRewriteFormationTime() {
    given(date);
    when(mapper.save(any())).thenReturn(1, 0);
    var rows = new ArrayList<ResearchObservation>();
    doAnswer(
            i -> {
              rows.add(i.getArgument(0));
              return rows.size() == 1 ? 1 : 0;
            })
        .when(mapper)
        .save(any());
    try (var ignored = TenantContext.bind(new UserView(2, "test", "USER"))) {
      var service = service("2026-11-18T08:00:00Z");
      assertEquals(1, service.capture(date).saved());
      assertEquals(1, service.capture(date).reused());
    }
    assertEquals("LIVE", rows.getFirst().getCaptureKind());
    assertEquals(rows.getFirst().getEvidenceHash(), rows.getLast().getEvidenceHash());
    assertEquals(rows.getFirst().getEvidenceJson(), rows.getLast().getEvidenceJson());
  }

  @Test
  void backdatedCaptureIsOnlyRetrospectiveAndBeforeCloseIsRejected() {
    given(date.minusDays(1));
    when(mapper.save(any())).thenReturn(1);
    try (var ignored = TenantContext.bind(new UserView(2, "test", "USER"))) {
      var result = service("2026-11-18T08:00:00Z").capture(date.minusDays(1));
      assertEquals("RETROSPECTIVE", result.captureKind());
    }
    assertThrows(
        IllegalArgumentException.class, () -> service("2026-11-18T01:00:00Z").capture(date));
    assertThrows(
        IllegalArgumentException.class,
        () -> service("2026-11-18T08:00:00Z").capture(date.plusDays(1)));
  }
}

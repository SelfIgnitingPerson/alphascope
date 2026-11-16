package com.alphascope.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.MarketSourceDO;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.vo.ProviderStatusView;
import com.alphascope.operations.maintenance.DatabaseMaintenance.BackupOutcome;
import com.alphascope.operations.service.DatabaseBackupService;
import com.alphascope.operations.service.impl.OperationsStatusServiceImpl;
import com.alphascope.operations.vo.BackupRunStatus;
import com.alphascope.portfolio.service.PortfolioService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 运维只读状态核验，损坏或过期数据不被技术健康掩盖，不触发任何实际备份。
 *
 * @author chenxuefeng
 */
class OperationsStatusTest {
  @TempDir Path root;
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC);
  private final MarketService market = mock(MarketService.class);
  private final PortfolioService portfolio = mock(PortfolioService.class);
  private final DatabaseBackupService backup = mock(DatabaseBackupService.class);
  private String previous;

  @BeforeEach
  void prepare() {
    previous = System.getProperty("alphascope.root");
    System.setProperty("alphascope.root", root.toString());
    when(portfolio.watchlist()).thenReturn(List.of());
    when(market.providerStatus())
        .thenReturn(
            new ProviderStatusView(
                "MULTI",
                "REAL",
                false,
                "BAOSTOCK",
                List.of(),
                List.of(source(LocalDate.of(2026, 11, 5))),
                List.of()));
    when(market.expectedDates(any())).thenReturn(Set.of(LocalDate.of(2026, 11, 6)));
  }

  @AfterEach
  void restore() {
    if (previous == null) System.clearProperty("alphascope.root");
    else System.setProperty("alphascope.root", previous);
  }

  @Test
  void staleQuoteAndMissingBackupAreVisibleWithoutSideEffects() {
    var value = service().status();
    assertEquals("STALE", value.instruments().getFirst().status());
    assertEquals("MISSING", value.backup().status());
    assertEquals("NOT_CONFIGURED", value.secondary().status());
    assertEquals("ATTENTION", value.status());
    verifyNoInteractions(backup);
  }

  @Test
  void disabledOptionalBackupsDoNotDemandCreatingNewCopies() {
    when(market.expectedDates(any())).thenReturn(Set.of(LocalDate.of(2026, 11, 5)));
    var value = service().status();
    assertFalse(value.scheduledBackupEnabled());
    assertEquals("MISSING", value.backup().status());
    assertEquals("NOT_CONFIGURED", value.secondary().status());
    assertEquals("HEALTHY", value.status());
    assertTrue(value.issues().isEmpty());
    assertFalse(Files.exists(root.resolve(".runtime/backups")));
    verifyNoInteractions(backup);
  }

  @Test
  void unknownCalendarCannotTreatOldDataAsFresh() {
    when(market.expectedDates(any())).thenThrow(new IllegalStateException("missing"));
    var value = service().status();
    assertNull(value.expectedTradeDate());
    assertEquals("UNKNOWN_CALENDAR", value.calendarStatus());
    assertEquals("UNKNOWN_CALENDAR", value.instruments().getFirst().status());
  }

  @Test
  void actualBackupBytesAreCheckedAndTamperingIsVisible() throws Exception {
    Path directory = root.resolve(".runtime/backups");
    Files.createDirectories(directory);
    String name = "alphascope-20261106T170000-deadbeef.sql.gz";
    Path file = directory.resolve(name);
    Files.writeString(file, "fixture backup bytes");
    String hash = Jsons.hashFile(file);
    Files.writeString(
        directory.resolve("latest-success.json"),
        Jsons.write(
            new BackupOutcome(name, hash, clock.instant().minusSeconds(3600), true, 35, 1, 0)));
    Files.writeString(
        directory.resolve(name + ".verified.json"), Jsons.write(Map.of("sha256", hash)));
    assertEquals("AVAILABLE", service().status().backup().status());
    Files.writeString(file, "changed bytes");
    assertEquals("INVALID", service().status().backup().status());
  }

  @Test
  void aRunningFileFromAPreviousProcessDoesNotStayRunningForever() throws Exception {
    Path directory = root.resolve(".runtime/backups");
    Files.createDirectories(directory);
    Files.writeString(
        directory.resolve("latest-run.json"),
        Jsons.write(
            new BackupRunStatus(
                "RUNNING", clock.instant().minusSeconds(30), null, null, "old process")));
    assertEquals("INTERRUPTED", service().status().latestAttempt().status());
    when(backup.isActive()).thenReturn(true);
    assertEquals("RUNNING", service().status().latestAttempt().status());
  }

  private OperationsStatusServiceImpl service() {
    return new OperationsStatusServiceImpl(
        market,
        portfolio,
        new AlphaScopeProperties(),
        clock,
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
        backup);
  }

  private MarketSourceDO source(LocalDate date) {
    var row = new MarketSourceDO();
    row.setSymbol("000300.SH");
    row.setTradeDate(date);
    row.setSource("BAOSTOCK");
    return row;
  }
}

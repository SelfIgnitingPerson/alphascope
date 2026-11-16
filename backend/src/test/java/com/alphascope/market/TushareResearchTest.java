package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alphascope.market.dto.TushareRequest;
import com.alphascope.market.provider.research.TushareResearchProvider;
import com.alphascope.market.provider.tushare.TushareClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 官方历史名册、停牌和量额标准化协议测试，账号权限不足时不退回当前成分伪造历史。
 *
 * @author chenxuefeng
 */
class TushareResearchTest {
  private final LocalDate date = LocalDate.of(2026, 10, 30);

  @Test
  void historicalRosterAndCurrencyUnitsArePreserved() {
    var input =
        new TushareResearchProvider.SnapshotRows(
            List.of(
                Map.of("trade_date", "20261030", "ts_code", "600000.SH", "list_date", "19991110")),
            List.of(
                Map.of(
                    "trade_date",
                    "20261030",
                    "ts_code",
                    "600000.SH",
                    "close",
                    "10",
                    "pre_close",
                    "9",
                    "amount",
                    "1.25")),
            List.of());
    var result = TushareResearchProvider.normalize(date, input);
    assertEquals("TUSHARE", result.source());
    assertEquals("1250.00", result.daily().getFirst().get("amount"));
    assertEquals("sh.600000", result.universe().getFirst().get("code"));
    assertEquals("1", result.universe().getFirst().get("tradeStatus"));
  }

  @Test
  void missingDailyDoesNotBecomeSuspensionWithoutEvidence() {
    var basic =
        List.of(Map.of("trade_date", "20261030", "ts_code", "600000.SH", "list_date", "19991110"));
    var absent =
        TushareResearchProvider.normalize(
            date, new TushareResearchProvider.SnapshotRows(basic, List.of(), List.of()));
    assertEquals("1", absent.universe().getFirst().get("tradeStatus"));
    var stopped =
        TushareResearchProvider.normalize(
            date,
            new TushareResearchProvider.SnapshotRows(
                basic,
                List.of(),
                List.of(
                    Map.of(
                        "ts_code", "600000.SH", "trade_date", "20261030", "suspend_type", "S"))));
    assertEquals("0", stopped.universe().getFirst().get("tradeStatus"));
  }

  @Test
  void aDifferentDatesRosterIsRejected() {
    assertThrows(
        IllegalStateException.class,
        () ->
            TushareResearchProvider.normalize(
                date,
                new TushareResearchProvider.SnapshotRows(
                    List.of(
                        Map.of(
                            "trade_date",
                            "20261029",
                            "ts_code",
                            "600000.SH",
                            "list_date",
                            "19991110")),
                    List.of(),
                    List.of())));
  }

  @Test
  void permissionFailureDoesNotFallBackToCurrentStockBasic() {
    var client = mock(TushareClient.class);
    var provider = new TushareResearchProvider(client);
    when(client.query(any())).thenThrow(new SecurityException("ACCESS_DENIED"));
    assertThrows(SecurityException.class, () -> provider.snapshot(date));
    var request = org.mockito.ArgumentCaptor.forClass(TushareRequest.class);
    verify(client).query(request.capture());
    assertEquals("bak_basic", request.getValue().api());
  }
}

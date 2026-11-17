package com.alphascope.market;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.common.util.Jsons;
import com.alphascope.market.calculator.*;
import com.alphascope.market.dto.*;
import com.alphascope.market.entity.MarketBreadthSnapshot;
import com.alphascope.market.vo.MarketUniverseView;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 三所同日目录与截面证明回归；日期错位及缺失成员不得标记完整。 @author chenxuefeng */
class FullMarketAssemblerTest {
  private final LocalDate date = LocalDate.of(2026, 11, 17);

  private MarketUniverseView roster(LocalDate day, String code) {
    return new MarketUniverseView(
        day,
        "AVAILABLE",
        "SINA",
        1,
        1L,
        "fixture",
        day.atTime(16, 10),
        "https://vip.stock.finance.sina.com.cn/",
        List.of(),
        List.of(new UniverseMember(code, "测试")));
  }

  private ResearchMarketSnapshot snapshot() {
    var universe =
        List.of("sh.600000", "sz.000001", "bj.920000").stream()
            .map(code -> Map.of("code", code, "tradeStatus", "1"))
            .toList();
    var daily =
        universe.stream()
            .map(
                row ->
                    Map.of(
                        "code",
                        row.get("code"),
                        "date",
                        date.toString(),
                        "tradestatus",
                        "1",
                        "close",
                        "10",
                        "amount",
                        "100",
                        "preclose",
                        "9"))
            .toList();
    return new ResearchMarketSnapshot(
        "EASTMONEY", date, universe, daily, "{\"rosterCount\":3}", List.of());
  }

  @Test
  void realMatchedRosterCanBeIndependentlyVerified() {
    String raw = FullMarketAssembler.assemble(snapshot(), roster(date, "920000.BJ"));
    var row =
        MarketBreadthSnapshot.builder()
            .tradeDate(date)
            .source("EASTMONEY")
            .universeScope("FULL_A")
            .rawJson(raw)
            .contentHash(Jsons.hash(raw))
            .build();
    assertTrue(UniverseCoverageCalculator.calculate(row).coverage().rosterVerified());
  }

  @Test
  void wrongDateMembersAndPaginationProofAreRejected() {
    assertThrows(
        IllegalStateException.class,
        () -> FullMarketAssembler.assemble(snapshot(), roster(date.minusDays(1), "920000.BJ")));
    assertThrows(
        IllegalStateException.class,
        () -> FullMarketAssembler.assemble(snapshot(), roster(date, "920001.BJ")));
    var incomplete = snapshot();
    incomplete =
        new ResearchMarketSnapshot(
            incomplete.source(),
            date,
            incomplete.universe(),
            incomplete.daily(),
            "{\"rosterCount\":4}",
            List.of());
    var bad = incomplete;
    assertThrows(
        IllegalStateException.class,
        () -> FullMarketAssembler.assemble(bad, roster(date, "920000.BJ")));
  }
}

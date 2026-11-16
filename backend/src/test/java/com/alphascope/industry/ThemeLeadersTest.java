package com.alphascope.industry;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.common.exception.BusinessException;
import com.alphascope.industry.dto.CohortDefinition;
import com.alphascope.industry.service.impl.ThemeLeadersServiceImpl;
import com.alphascope.industry.vo.ThemeLeadersView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 当前核心企业与原披露成分分别读取，防止研究展示被误当成历史行业排名。
 *
 * @author chenxuefeng
 */
class ThemeLeadersTest {
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  @Test
  void opticalCoreIncludesYiZhongTianWithoutCallingComponentSupplierThirdModuleMaker()
      throws Exception {
    var service = new ThemeLeadersServiceImpl(json);
    var optical = service.get("CPO");
    assertEquals(
        List.of("300308.SZ", "300502.SZ", "300394.SZ"),
        optical.members().stream().map(ThemeLeadersView.Member::symbol).toList());
    assertTrue(optical.marketAlias().contains("易中天"));
    var tfc = optical.members().get(2);
    assertTrue(tfc.segment().contains("光器件"));
    assertNull(tfc.formalRanking());
    assertTrue(tfc.rationale().contains("不能标为第三大光模块"));
    assertEquals(5, service.list().size());
    for (var group : service.list()) {
      assertNotNull(group.reviewedAt());
      assertTrue(group.limitation().contains("不倒填历史"));
      assertTrue(group.members().stream().allMatch(member -> !member.sources().isEmpty()));
    }
  }

  @Test
  void currentLeaderReadDoesNotReplaceTheFourMemberHistoricalOpticalCohort() throws Exception {
    var service = new ThemeLeadersServiceImpl(json);
    service.get("CPO");
    try (var in = getClass().getResourceAsStream("/research/theme-cohorts.json")) {
      var original = List.of(json.readValue(in, CohortDefinition[].class));
      var optical =
          original.stream()
              .filter(group -> group.themeCode().equals("CPO"))
              .findFirst()
              .orElseThrow();
      assertEquals(4, optical.members().size());
      assertFalse(
          optical.members().stream().anyMatch(member -> member.symbol().equals("300394.SZ")));
    }
    assertEquals(
        404, assertThrows(BusinessException.class, () -> service.get("UNKNOWN")).getStatus());
  }

  @Test
  void semiconductorCorePrioritizesPlatformEtchAndCleaningAndCatalogCannotBeMutated()
      throws Exception {
    var service = new ThemeLeadersServiceImpl(json);
    assertEquals(
        List.of("002371.SZ", "688012.SH", "688082.SH"),
        service.get("SEMI_EQUIPMENT").members().stream()
            .map(ThemeLeadersView.Member::symbol)
            .toList());
    assertThrows(UnsupportedOperationException.class, () -> service.list().clear());
    assertThrows(UnsupportedOperationException.class, () -> service.get("PCB").members().clear());
    assertThrows(
        UnsupportedOperationException.class,
        () -> service.get("PCB").members().getFirst().sources().clear());
    assertTrue(
        service.get("PCB").members().stream()
            .anyMatch(member -> member.symbol().equals("600183.SH")));
    assertTrue(
        service.get("MEMORY").members().stream()
            .anyMatch(member -> member.symbol().equals("688008.SH")));
  }
}

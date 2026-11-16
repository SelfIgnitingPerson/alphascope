package com.alphascope.industry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.industry.dto.CohortDefinition;
import com.alphascope.industry.entity.ThemeResearchCohort;
import com.alphascope.industry.mapper.*;
import com.alphascope.industry.service.impl.ThemeCohortServiceImpl;
import com.alphascope.industry.validator.ThemeCohortValidator;
import com.alphascope.market.service.MarketService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.*;

/**
 * 披露研究样本来源、可用时点及不可覆盖约束的验证。
 *
 * @author chenxuefeng
 */
class ThemeCohortTest {
  final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  CohortDefinition[] definitions() throws Exception {
    try (var in = getClass().getResourceAsStream("/research/theme-cohorts.json")) {
      return json.readValue(in, CohortDefinition[].class);
    }
  }

  @Test
  void catalogHasTraceableDisclosedPeersAndExplicitKnowledgeTimes() throws Exception {
    var d = definitions();
    assertEquals(5, d.length);
    assertEquals(
        Set.of("PCB", "COOLING", "CPO", "SEMI_EQUIPMENT", "MEMORY"),
        Arrays.stream(d)
            .map(CohortDefinition::themeCode)
            .collect(java.util.stream.Collectors.toSet()));
    assertEquals(4, d[2].members().size());
    assertEquals(6, d[3].members().size());
    assertEquals(3, d[4].members().size());
    assertEquals(6, d[0].members().size());
    assertEquals(3, d[1].members().size());
    for (var definition : d) {
      ThemeCohortValidator.validate(definition);
      assertEquals("DISCLOSED_RESEARCH_SAMPLE", definition.scope());
      assertTrue(definition.availableAt().isAfter(definition.publishedAt()));
      assertTrue(definition.sourceUrl().contains("cninfo.com.cn"));
    }
  }

  @Test
  void laterSourcesAndDemoAreNotImportedIntoEarlierResearch() throws Exception {
    var mapper = mock(ThemeCohortMapper.class);
    var market = mock(MarketService.class);
    var tx = mock(TransactionTemplate.class);
    var service = new ThemeCohortServiceImpl(mapper, mock(IndustryMapper.class), market, tx, json);
    var firstAvailable =
        Arrays.stream(definitions())
            .map(CohortDefinition::availableAt)
            .min(Comparator.naturalOrder())
            .orElseThrow()
            .toLocalDate();
    service.prepare(firstAvailable.minusDays(1));
    verifyNoInteractions(mapper, tx);
    when(market.synthetic()).thenReturn(true);
    service.prepare(LocalDate.of(2026, 10, 30));
    verifyNoInteractions(mapper, tx);
  }

  @Test
  void existingDefinitionsCannotBeSilentlyReplaced() throws Exception {
    var mapper = mock(ThemeCohortMapper.class);
    var tx = mock(TransactionTemplate.class);
    doAnswer(
            a -> {
              ((Consumer<TransactionStatus>) a.getArgument(0))
                  .accept(mock(TransactionStatus.class));
              return null;
            })
        .when(tx)
        .executeWithoutResult(any());
    when(mapper.get(anyString())).thenReturn(fixtureThemeResearchCohort1());
    var service =
        new ThemeCohortServiceImpl(
            mapper, mock(IndustryMapper.class), mock(MarketService.class), tx, json);
    assertThrows(IllegalStateException.class, () -> service.prepare(LocalDate.of(2026, 10, 30)));
    verify(mapper, never()).instrument(any());
    verify(mapper, never()).cohort(any());
  }

  @Test
  void duplicateMembersAndSameDayDateOnlyAvailabilityAreRejected() throws Exception {
    var base = json.valueToTree(definitions()[0]);
    ((ObjectNode) base).set("availableAt", base.path("publishedAt"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ThemeCohortValidator.validate(json.treeToValue(base, CohortDefinition.class)));
    var duplicate = json.valueToTree(definitions()[0]);
    ((ArrayNode) duplicate.path("members")).add(duplicate.path("members").get(0));
    assertThrows(
        IllegalArgumentException.class,
        () -> ThemeCohortValidator.validate(json.treeToValue(duplicate, CohortDefinition.class)));
  }

  /** 明确设置该用例需要的字段，避免JSON转换掩盖测试数据类型。 */
  private ThemeResearchCohort fixtureThemeResearchCohort1() {
    var row = new ThemeResearchCohort();
    row.setId(1L);
    row.setContentHash("different");
    return row;
  }
}

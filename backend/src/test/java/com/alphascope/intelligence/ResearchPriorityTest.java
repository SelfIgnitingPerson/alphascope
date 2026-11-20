package com.alphascope.intelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.intelligence.calculator.ResearchFocusCalculator;
import com.alphascope.intelligence.calculator.ResearchPriorityCalculator;
import com.alphascope.intelligence.dto.ResearchFocusInput;
import com.alphascope.intelligence.vo.HoldingResearchView;
import com.alphascope.intelligence.vo.PositionStrategy.Condition;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 观察优先级必须服从实际证据；弱观察与已确认组合分开，缺项不解释为风险解除。
 *
 * @author chenxuefeng
 */
class ResearchPriorityTest {
  private final LocalDate date = LocalDate.of(2026, 11, 17);

  private HoldingResearchView.Action.ActionBuilder exit() {
    return HoldingResearchView.Action.builder()
        .code("REDUCE")
        .title("风险复核")
        .conditionLogic("ANY")
        .status("WATCH")
        .conclusion("弱走势待观察")
        .conditions(List.of(new Condition("趋势与量能组合", "趋势弱，但量能未确认", "同时满足", false)))
        .invalidation(List.of("对照原风险线复核"));
  }

  private HoldingResearchView.Horizon horizon(HoldingResearchView.Action action) {
    return HoldingResearchView.Horizon.builder()
        .code("SHORT")
        .title("短期")
        .status("WATCH")
        .actions(List.of(action))
        .evidence(List.of("MA20下方"))
        .missing(List.of())
        .build();
  }

  private HoldingResearchView view(List<HoldingResearchView.Horizon> horizons) {
    return HoldingResearchView.builder()
        .version("HOLDING_RESEARCH_V2")
        .symbol("600000.SH")
        .name("测试")
        .asOf(date)
        .quoteDate(date)
        .source("TEST")
        .close(100.0)
        .horizons(horizons)
        .build();
  }

  @Test
  void businessRiskAcrossHorizonsOutranksWeakShortTermObservation() {
    var weak = horizon(exit().build());
    var businessAction =
        horizon(
            exit()
                .status("CONDITIONAL_MATCH")
                .conditions(List.of(new Condition("经营风险", "利润同比下降且现金流转负", "经营风险触发", true)))
                .build());
    var business =
        HoldingResearchView.Horizon.builder()
            .code("LONG")
            .title("长期")
            .status("WATCH")
            .actions(businessAction.actions())
            .evidence(List.of())
            .build();
    var now = view(List.of(weak, business));
    var priorities = ResearchPriorityCalculator.observations(now);
    assertEquals("BUSINESS_RISK", priorities.getFirst().code());
    assertEquals("LONG", priorities.getFirst().horizon());
    assertTrue(priorities.getFirst().facts().getFirst().contains("现金流转负"));
    assertEquals("TREND_WATCH", priorities.get(1).code());
    assertEquals("WATCH", priorities.get(1).level());
    var focused =
        ResearchFocusCalculator.compare(ResearchFocusInput.builder().current(now).build());
    assertEquals("LONG", focused.horizon());
    assertEquals("BUSINESS_RISK", focused.priorities().getFirst().code());
  }

  @Test
  void structureTouchComesFirstAndDoesNotResetAnOriginalPlanBoundary() {
    var now = view(List.of(horizon(exit().stopReference(100.0).build())));
    var priority = ResearchPriorityCalculator.observations(now).getFirst();
    assertEquals("STRUCTURE_RISK", priority.code());
    assertEquals("FIRST", priority.level());
    assertTrue(priority.facts().getFirst().contains("100.00"));
    assertTrue(priority.nextChecks().getFirst().contains("不能替换"));
    var weak = now.toBuilder().close(100.01).build();
    assertEquals("TREND_WATCH", ResearchPriorityCalculator.observations(weak).getFirst().code());
  }

  @Test
  void continuedRiskIsNotNewAndIncomparableSourceCannotClaimChange() {
    var now = view(List.of(horizon(exit().stopReference(101.0).build())));
    var previousDate = date.minusDays(1);
    var previous = now.toBuilder().asOf(previousDate).quoteDate(previousDate).build();
    var input =
        ResearchFocusInput.builder().current(now).previous(previous).previousDate(previousDate);
    assertEquals("上期也有此信号", ResearchFocusCalculator.compare(input.build()).changeLabel());
    assertTrue(
        ResearchFocusCalculator.compare(
                input.previous(previous.toBuilder().close(102.0).build()).build())
            .changeLabel()
            .contains("新增或改变"));
    assertTrue(
        ResearchFocusCalculator.compare(
                input.previous(previous.toBuilder().source("OTHER").build()).build())
            .changeLabel()
            .contains("数据来源已变化"));
  }

  @Test
  void staleOrInvalidQuoteNeverBecomesAnEntryOrRiskConfirmation() {
    var now = view(List.of(horizon(exit().stopReference(101.0).build())));
    for (var invalid :
        List.of(
            now.toBuilder().quoteDate(date.minusDays(1)).build(),
            now.toBuilder().close(Double.NaN).build(),
            now.toBuilder().close(0.0).build(),
            now.toBuilder().source("").build())) {
      assertEquals("DATA_GAP", ResearchPriorityCalculator.observations(invalid).getFirst().code());
    }
  }
}

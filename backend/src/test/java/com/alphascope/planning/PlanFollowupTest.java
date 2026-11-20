package com.alphascope.planning;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.common.response.PageResponse;
import com.alphascope.planning.service.ResearchPlanService;
import com.alphascope.planning.service.impl.PlanFollowupServiceImpl;
import com.alphascope.planning.vo.PlanReviewView;
import com.alphascope.planning.vo.PlanSummary;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 全量待办、幂等跟踪和单项失败隔离，不接触实际账号和行情。
 *
 * @author chenxuefeng
 */
class PlanFollowupTest {
  private final LocalDate date = LocalDate.of(2026, 10, 30);

  private PlanSummary row(long id, LocalDate reviewed, String status, boolean acknowledged) {
    return new PlanSummary(
        id,
        "600000.SH",
        "测试",
        "SHORT",
        "OPEN_PULLBACK",
        false,
        null,
        date.minusDays(2),
        reviewed,
        status,
        "复核说明",
        acknowledged,
        null,
        null,
        java.util.Set.of());
  }

  @Test
  void todosReadAllPagesBeforeSelectingRiskAndNeverWrite() {
    var plans = mock(ResearchPlanService.class);
    when(plans.page(any()))
        .thenAnswer(
            inv -> {
              com.alphascope.planning.dto.PlanQuery q = inv.getArgument(0);
              return new PageResponse<>(
                  q.getPage() == 1
                      ? List.of(row(1, date, "UNCHANGED", true))
                      : List.of(row(2, date, "ORIGINAL_STOP_BREACHED", false)),
                  101,
                  q.getPage(),
                  100);
            });
    var result = new PlanFollowupServiceImpl(plans).todos(date);
    assertEquals(1, result.risk());
    assertEquals(2, result.total());
    assertEquals(2, result.items().getFirst().id());
    verify(plans, never()).review(anyLong(), any());
  }

  @Test
  void scheduledFollowupSkipsValidTodayAndPreservesSingleFailure() {
    var plans = mock(ResearchPlanService.class);
    when(plans.page(any()))
        .thenReturn(
            new PageResponse<>(
                List.of(
                    row(1, date, "UNCHANGED", false),
                    row(2, null, null, false),
                    row(3, null, null, false)),
                3,
                1,
                100));
    when(plans.review(eq(2L), any())).thenThrow(new IllegalStateException("内部原因不直接公开"));
    when(plans.review(eq(3L), any()))
        .thenReturn(new PlanReviewView(3, date, "UNCHANGED", "完成", 90.0, 100.0, "WAIT", List.of()));
    var result = new PlanFollowupServiceImpl(plans).review(date);
    assertEquals(1, result.reviewed());
    assertEquals(1, result.remaining());
    assertFalse(result.issues().getFirst().contains("内部原因"));
    verify(plans, never()).review(eq(1L), any());
  }

  @Test
  void acknowledgedMissingEvidenceNeverBecomesAUsableReview() {
    var plans = mock(ResearchPlanService.class);
    when(plans.page(any()))
        .thenReturn(
            new PageResponse<>(List.of(row(1, date, "DATA_INSUFFICIENT", true)), 1, 1, 100));
    var result = new PlanFollowupServiceImpl(plans).todos(date);
    assertEquals(1, result.acknowledged());
    assertEquals(0, result.reviewed());
    assertEquals(0, result.data(), "已阅读只清理提醒，不改变有效复核计数");
  }

  @Test
  void originalRiskListIncludesPreviouslyReadRisksBeyondDisplayLimit() {
    var plans = mock(ResearchPlanService.class);
    var risks =
        java.util.stream.LongStream.rangeClosed(1, 6)
            .mapToObj(id -> row(id, date, "ORIGINAL_STOP_BREACHED", id == 6))
            .toList();
    when(plans.pageWithOriginalRisk(any())).thenReturn(new PageResponse<>(risks, 6, 1, 100));
    var result = new PlanFollowupServiceImpl(plans).originalRiskAlerts(date);
    assertEquals(6, result.size());
    assertTrue(result.getLast().acknowledged());
  }

  @Test
  void backgroundFollowupDoesNotStarvePlansBeyondHttpBatchLimit() {
    var plans = mock(ResearchPlanService.class);
    when(plans.page(any()))
        .thenAnswer(
            inv -> {
              com.alphascope.planning.dto.PlanQuery q = inv.getArgument(0);
              int start = (q.getPage() - 1) * 100;
              var rows =
                  java.util.stream.IntStream.range(start, Math.min(101, start + 100))
                      .mapToObj(i -> row(i + 1, null, null, false))
                      .toList();
              return new PageResponse<>(rows, 101, q.getPage(), 100);
            });
    when(plans.review(anyLong(), any()))
        .thenAnswer(
            inv ->
                new PlanReviewView(
                    inv.getArgument(0), date, "UNCHANGED", "完成", 90.0, 100.0, "WAIT", List.of()));
    var service = new PlanFollowupServiceImpl(plans);
    assertEquals(100, service.review(date).reviewed());
    var all = service.reviewAll(date);
    assertEquals(101, all.reviewed());
    assertEquals(0, all.remaining());
  }
}

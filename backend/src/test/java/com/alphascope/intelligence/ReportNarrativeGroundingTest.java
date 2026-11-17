package com.alphascope.intelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.intelligence.dto.ReportResult;
import com.alphascope.intelligence.policy.ReportNarrativePolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 防止把已存在的数字换证券、换日期或换单位后通过模型检查。
 *
 * @author chenxuefeng
 */
class ReportNarrativeGroundingTest {
  @Test
  void crossCompanyOrUnitReassignmentCannotReuseTheSameNumbers() {
    var facts = result("甲公司：2026年第三季收入10亿元");
    assertTrue(ReportNarrativePolicy.matches(facts, facts));
    assertFalse(ReportNarrativePolicy.matches(result("乙公司：2026年第三季收入10亿元"), facts));
    assertFalse(ReportNarrativePolicy.matches(result("甲公司：2026年第三季收入10万元"), facts));
  }

  @Test
  void modelCannotSilentlyDropKnownRisksOrUncertainty() {
    var facts =
        new ReportResult(
            "UNKNOWN",
            "已核验摘要",
            List.of(),
            List.of("已核验风险"),
            List.of(),
            List.of("个股风险"),
            List.of(),
            List.of("test-source"),
            List.of("证据尚未完成核验"));
    var dropped =
        new ReportResult(
            "UNKNOWN",
            "已核验摘要",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("test-source"),
            List.of());
    assertFalse(ReportNarrativePolicy.matches(dropped, facts));
  }

  private ReportResult result(String positive) {
    return new ReportResult(
        "UNKNOWN",
        "已核验摘要",
        List.of(positive),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of("test-source"),
        List.of());
  }
}

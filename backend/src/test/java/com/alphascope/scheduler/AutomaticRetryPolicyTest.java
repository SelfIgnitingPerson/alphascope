package com.alphascope.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.scheduler.entity.JobExecution;
import com.alphascope.scheduler.policy.AutomaticRetryPolicy;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 自动重试预算覆盖重启、成功去重及用户取消意图，不重测普通手动重试。
 *
 * @author chenxuefeng
 */
class AutomaticRetryPolicyTest {
  private final LocalDateTime now = LocalDateTime.of(2026, 11, 10, 16, 40);

  @Test
  void retryRequiresElapsedBackoffAndRemainingPersistentBudget() {
    var previous = new JobExecution();
    previous.setStatus("FAILED");
    previous.setEndedAt(now.minusMinutes(30));
    assertTrue(AutomaticRetryPolicy.allowed(previous, 3, now));
    assertFalse(AutomaticRetryPolicy.allowed(previous, 4, now));
    previous.setEndedAt(now.minusMinutes(29));
    assertFalse(AutomaticRetryPolicy.allowed(previous, 1, now));
  }

  @Test
  void successfulActiveAndUserCancelledWorkIsNotAutomaticallyRecreated() {
    var previous = new JobExecution();
    previous.setEndedAt(now.minusHours(1));
    for (String status :
        new String[] {
          "SUCCESS", "SKIPPED", "PENDING", "RUNNING", "CANCEL_REQUESTED", "CANCELLED"
        }) {
      previous.setStatus(status);
      assertFalse(AutomaticRetryPolicy.allowed(previous, 1, now), status);
    }
  }
}

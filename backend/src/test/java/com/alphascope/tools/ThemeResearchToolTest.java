package com.alphascope.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 本机主题维护入口的业务日期边界，不连接业务库或上游。
 *
 * @author chenxuefeng
 */
class ThemeResearchToolTest {
  @Test
  void rejectsFutureAndIntradayCloseBeforeCreatingApplicationContext() {
    var now = LocalDateTime.of(2026, 11, 12, 9, 30);
    assertThrows(
        IllegalArgumentException.class,
        () -> ThemeResearchTool.requireCompletedDate(LocalDate.of(2026, 11, 13), now));
    assertThrows(
        IllegalArgumentException.class,
        () -> ThemeResearchTool.requireCompletedDate(LocalDate.of(2026, 11, 12), now));
    assertThrows(
        IllegalArgumentException.class, () -> ThemeResearchTool.requireCompletedDate(null, now));
  }

  @Test
  void preservesExplicitPastDateAndAllowsCloseWindowWithoutSubstitutingDate() {
    var now = LocalDateTime.of(2026, 11, 12, 15, 30);
    assertDoesNotThrow(() -> ThemeResearchTool.requireCompletedDate(now.toLocalDate(), now));
    assertDoesNotThrow(
        () -> ThemeResearchTool.requireCompletedDate(LocalDate.of(2026, 11, 11), now));
  }
}

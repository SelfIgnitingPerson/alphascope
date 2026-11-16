package com.alphascope.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.ExecutionProgress;
import com.alphascope.common.task.PipelineStage;
import com.alphascope.common.task.TaskStopReason;
import com.alphascope.common.task.TaskStoppedException;
import com.alphascope.common.util.Jsons;
import com.alphascope.scheduler.control.JobExecutionControl;
import com.alphascope.scheduler.dto.*;
import com.alphascope.scheduler.entity.JobExecution;
import com.alphascope.scheduler.entity.JobStage;
import com.alphascope.scheduler.mapper.JobMapper;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

/**
 * 持久取消、硬截止、进度和线程上下文的回归，所有等待均有明确测试时限。
 *
 * @author chenxuefeng
 */
class JobExecutionControlTest {
  private final JobMapper mapper = mock(JobMapper.class);
  private final AlphaScopeProperties properties = new AlphaScopeProperties();
  private final MutableClock clock = new MutableClock();
  private final JobExecutionControl control = new JobExecutionControl(mapper, properties, clock);
  private final JobExecution row = new JobExecution();

  JobExecutionControlTest() {
    row.setId(1L);
    row.setOwnerId("worker");
    row.setStatus("RUNNING");
    when(mapper.get(1L)).thenReturn(row);
    when(mapper.requestStop(any()))
        .thenAnswer(
            call -> {
              JobStopRequest request = call.getArgument(0);
              if (!"RUNNING".equals(row.getStatus())) return 0;
              row.setStatus(request.status().name());
              return 1;
            });
  }

  @Test
  void successfulExecutionDoesNotPromotePartialDataToAvailable() {
    StageQuality quality =
        new StageQuality(
            DataQualityStatus.PARTIAL,
            1,
            2,
            List.of(new StageQualityIssue("B", "SOURCE", "UNAVAILABLE", "没有证据")),
            "已执行但仍有缺项");
    var result = control.execute(execution(() -> {}, () -> quality));
    assertEquals(DataQualityStatus.PARTIAL, result.status());
    var stage = ArgumentCaptor.forClass(JobStage.class);
    verify(mapper).stageEnd(stage.capture());
    assertEquals("SUCCESS", stage.getValue().getStatus());
    assertEquals("PARTIAL", stage.getValue().getDataStatus());
    StageDetails details = Jsons.read(stage.getValue().getDetailsJson(), StageDetails.class);
    assertEquals(2, details.quality().expected());
    assertNotNull(details.deadline());
  }

  @Test
  void persistentCancellationIsCheckedBeforeTheAction() {
    row.setStatus("CANCEL_REQUESTED");
    Runnable action = mock(Runnable.class);
    var stopped =
        assertThrows(
            TaskStoppedException.class,
            () -> control.execute(execution(action, () -> StageQuality.available("ok"))));
    assertEquals(TaskStopReason.CANCELLED, stopped.getReason());
    verifyNoInteractions(action);
  }

  @Test
  void activeCancellationInterruptsOnlyTheOwnedWorkerAndRetainsIntent() throws Exception {
    var started = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      Future<?> future =
          executor.submit(
              () ->
                  control.execute(execution(blocked(started), () -> StageQuality.available("ok"))));
      assertTrue(started.await(5, TimeUnit.SECONDS));
      control.cancel(1L);
      var error = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
      assertEquals(TaskStopReason.CANCELLED, ((TaskStoppedException) error.getCause()).getReason());
      assertEquals("CANCEL_REQUESTED", row.getStatus());
      var stage = ArgumentCaptor.forClass(JobStage.class);
      verify(mapper).stageEnd(stage.capture());
      assertEquals("CANCELLED", stage.getValue().getStatus());
    }
  }

  @Test
  void hardDeadlineExpiresDespiteAHealthyTaskOwner() throws Exception {
    properties.getJobs().setStageTimeout(Duration.ofSeconds(1));
    var started = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      Future<?> future =
          executor.submit(
              () ->
                  control.execute(execution(blocked(started), () -> StageQuality.available("ok"))));
      assertTrue(started.await(5, TimeUnit.SECONDS));
      clock.advance(Duration.ofSeconds(2));
      control.watchdog();
      var error = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
      assertEquals(TaskStopReason.TIMED_OUT, ((TaskStoppedException) error.getCause()).getReason());
      assertEquals("TIMEOUT_REQUESTED", row.getStatus());
    }
  }

  @Test
  void deadlineStillInterruptsWhenTheWatchdogDatabaseWriteFails() throws Exception {
    properties.getJobs().setStageTimeout(Duration.ofSeconds(1));
    var started = new CountDownLatch(1);
    doThrow(new IllegalStateException("database unavailable")).when(mapper).requestStop(any());
    try (var executor = Executors.newSingleThreadExecutor()) {
      Future<?> future =
          executor.submit(
              () ->
                  control.execute(execution(blocked(started), () -> StageQuality.available("ok"))));
      assertTrue(started.await(5, TimeUnit.SECONDS));
      clock.advance(Duration.ofSeconds(2));
      control.watchdog();
      var error = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
      assertEquals(TaskStopReason.TIMED_OUT, ((TaskStoppedException) error.getCause()).getReason());
    }
  }

  @Test
  void deadlineInterruptIsIssuedBeforeAStuckDatabaseWriteReturns() throws Exception {
    properties.getJobs().setStageTimeout(Duration.ofSeconds(1));
    var started = new CountDownLatch(1);
    var databaseEntered = new CountDownLatch(1);
    var releaseDatabase = new CountDownLatch(1);
    doAnswer(
            call -> {
              databaseEntered.countDown();
              assertTrue(releaseDatabase.await(5, TimeUnit.SECONDS));
              return 1;
            })
        .when(mapper)
        .requestStop(any());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var worker =
          executor.submit(
              () ->
                  control.execute(execution(blocked(started), () -> StageQuality.available("ok"))));
      assertTrue(started.await(5, TimeUnit.SECONDS));
      clock.advance(Duration.ofSeconds(2));
      var monitor = executor.submit(control::watchdog);
      try {
        assertTrue(databaseEntered.await(5, TimeUnit.SECONDS));
        var error = assertThrows(ExecutionException.class, () -> worker.get(2, TimeUnit.SECONDS));
        assertEquals(
            TaskStopReason.TIMED_OUT, ((TaskStoppedException) error.getCause()).getReason());
      } finally {
        releaseDatabase.countDown();
        monitor.get(5, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void progressWritesAreThrottledButFinalDetailsKeepTheLastProgress() {
    control.execute(
        execution(
            () -> {
              ExecutionContext.progress(new ExecutionProgress(1, 2, "first", clock.instant()));
              ExecutionContext.progress(new ExecutionProgress(2, 2, "second", clock.instant()));
            },
            () -> StageQuality.available("done")));
    verify(mapper, times(1)).stageProgress(any());
    var stage = ArgumentCaptor.forClass(JobStage.class);
    verify(mapper).stageEnd(stage.capture());
    assertEquals(
        2,
        Jsons.read(stage.getValue().getDetailsJson(), StageDetails.class).progress().completed());
  }

  @Test
  void failureRestoresLoggingContextAndDoesNotLeakTheStageRegistration() {
    MDC.put("requestId", "outer");
    try {
      doThrow(new IllegalStateException("first")).doNothing().when(mapper).stage(1L, "CALENDAR");
      assertThrows(
          IllegalStateException.class,
          () -> control.execute(execution(() -> {}, () -> StageQuality.available("ok"))));
      assertEquals("outer", MDC.get("requestId"));
      assertNull(MDC.get("jobId"));
      assertDoesNotThrow(
          () -> control.execute(execution(() -> {}, () -> StageQuality.available("ok"))));
    } finally {
      MDC.clear();
    }
  }

  @Test
  void designedBusinessFailureIsPersistedButInternalMessagesRemainPrivate() {
    var failure =
        new com.alphascope.common.exception.BusinessException(
            com.alphascope.common.response.ResultCode.DATA_UNAVAILABLE,
            503,
            "行情采集失败：000300.SH；BAOSTOCK:ACCESS_RESTRICTED");
    assertThrows(
        com.alphascope.common.exception.BusinessException.class,
        () ->
            control.execute(
                execution(
                    () -> {
                      throw failure;
                    },
                    () -> StageQuality.available("unused"))));
    var stage = ArgumentCaptor.forClass(JobStage.class);
    verify(mapper).stageEnd(stage.capture());
    assertEquals(failure.getMessage(), stage.getValue().getError());
    clearInvocations(mapper);
    assertThrows(
        IllegalStateException.class,
        () ->
            control.execute(
                execution(
                    () -> {
                      throw new IllegalStateException("token=private-value");
                    },
                    () -> StageQuality.available("unused"))));
    verify(mapper).stageEnd(stage.capture());
    assertFalse(stage.getValue().getError().contains("private-value"));
  }

  private StageExecution execution(Runnable action, Supplier<StageQuality> quality) {
    return StageExecution.builder()
        .jobId(1L)
        .ownerId("worker")
        .stage(PipelineStage.CALENDAR)
        .action(action)
        .quality(quality)
        .build();
  }

  private Runnable blocked(CountDownLatch started) {
    return () -> {
      started.countDown();
      try {
        new CountDownLatch(1).await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        ExecutionContext.checkpoint();
      }
    };
  }

  /**
   * 可控测试时钟，用实际线程验证截止时间而不等待真实超时。
   *
   * @author chenxuefeng
   */
  private static final class MutableClock extends Clock {
    private final AtomicReference<Instant> current =
        new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

    void advance(Duration duration) {
      current.updateAndGet(value -> value.plus(duration));
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return current.get();
    }
  }
}

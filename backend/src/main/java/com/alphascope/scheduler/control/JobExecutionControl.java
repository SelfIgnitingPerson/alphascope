package com.alphascope.scheduler.control;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.diagnostic.FailureDiagnosis;
import com.alphascope.common.diagnostic.FailureDiagnostics;
import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.ExecutionProgress;
import com.alphascope.common.task.TaskStopReason;
import com.alphascope.common.task.TaskStoppedException;
import com.alphascope.common.util.Jsons;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.vo.UserView;
import com.alphascope.scheduler.dto.DataQualityStatus;
import com.alphascope.scheduler.dto.JobStatus;
import com.alphascope.scheduler.dto.JobStopRequest;
import com.alphascope.scheduler.dto.StageDetails;
import com.alphascope.scheduler.dto.StageExecution;
import com.alphascope.scheduler.dto.StageQuality;
import com.alphascope.scheduler.dto.StageQualityIssue;
import com.alphascope.scheduler.entity.JobStage;
import com.alphascope.scheduler.mapper.JobMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 持久取消、阶段硬截止和进度控制；心跳正常不会使卡住的阶段无限运行。
 *
 * @author chenxuefeng
 */
@Component
public class JobExecutionControl {
  private static final Logger LOGGER = LoggerFactory.getLogger(JobExecutionControl.class);
  private final JobMapper mapper;
  private final AlphaScopeProperties properties;
  private final Clock clock;
  private final Map<Long, Running> running = new ConcurrentHashMap<>();

  public JobExecutionControl(JobMapper mapper, AlphaScopeProperties properties, Clock clock) {
    this.mapper = mapper;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * 执行一个受控阶段，正常返回后另外核验数据质量。
   *
   * @param execution 当前任务、阶段、动作及核验回调
   * @return 阶段实际数据质量
   */
  public StageQuality execute(StageExecution execution) {
    var duration =
        properties
            .getJobs()
            .getStageTimeouts()
            .getOrDefault(execution.stage().name(), properties.getJobs().getStageTimeout());
    Running state = new Running(execution, Thread.currentThread(), clock.instant().plus(duration));
    if (running.putIfAbsent(execution.jobId(), state) != null) {
      throw new IllegalStateException("同一任务不能并行执行阶段");
    }
    Map<String, String> previous = MDC.getCopyOfContextMap();
    MDC.put("jobId", String.valueOf(execution.jobId()));
    MDC.put("stage", execution.stage().name());
    try (var scope =
        ExecutionContext.bind(() -> check(state), progress -> progress(state, progress))) {
      mapper.stage(execution.jobId(), execution.stage().name());
      checkPersistent(state);
      progress(state, new ExecutionProgress(0, 0, "开始执行阶段", clock.instant()));
      LOGGER.info(
          "stage_started deadline={} recoveryMode={}",
          state.deadline,
          execution.retryOf() == null ? "FRESH_RUN" : "REVALIDATE_STORED_INPUTS");
      execution.action().run();
      check(state);
      StageQuality quality = execution.quality().get();
      check(state);
      mapper.stageEnd(stage(state, "SUCCESS", null, quality));
      LOGGER.info(
          "stage_completed execution=SUCCESS dataStatus={} available={} expected={}",
          quality.status(),
          quality.available(),
          quality.expected());
      return quality;
    } catch (RuntimeException e) {
      if (!(e instanceof TaskStoppedException))
        LOGGER.error(
            "stage_failed tenantId={} jobId={} stage={}",
            TenantContext.id(),
            execution.jobId(),
            execution.stage(),
            e);
      state.diagnostic = FailureDiagnostics.from(e);
      TaskStopReason stopped = state.stop;
      if (stopped == null && e instanceof TaskStoppedException terminated) {
        stopped = terminated.getReason();
      }
      // JDBC收尾不能带着中断标志执行；完成持久化后恢复标记，由队列入口统一处理。
      boolean interrupted = Thread.interrupted();
      try {
        String status = stopped == null ? "FAILED" : stopped.name();
        String message =
            stopped == null
                ? e instanceof BusinessException business
                    ? business.getMessage()
                    : "阶段失败，错误类型：" + e.getClass().getSimpleName()
                : new TaskStoppedException(stopped).getMessage();
        if (stopped == null && state.diagnostic.code().startsWith("DATABASE_"))
          message =
              state.diagnostic.title()
                  + "："
                  + state.diagnostic.reason()
                  + "。"
                  + state.diagnostic.action();
        StageQuality quality =
            new StageQuality(
                DataQualityStatus.UNAVAILABLE,
                0,
                0,
                List.of(
                    new StageQualityIssue(
                        execution.stage().name(), MDC.get("provider"), status, message)),
                "阶段未完成，不沿用成功质量状态");
        mapper.stageEnd(stage(state, status, message, quality));
        LOGGER.warn(
            "stage_stopped execution={} failureType={} reason={}",
            status,
            e.getClass().getSimpleName(),
            message);
      } finally {
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
      if (stopped != null) {
        throw new TaskStoppedException(stopped);
      }
      throw e;
    } finally {
      running.remove(execution.jobId(), state);
      if (previous == null) {
        MDC.clear();
      } else {
        MDC.setContextMap(previous);
      }
    }
  }

  /**
   * 持久化取消意图，等待任务直接终止；运行任务由其线程释放资源并收尾。
   *
   * @param id 任务主键
   */
  public void cancel(long id) {
    int changed =
        mapper.requestStop(
            JobStopRequest.builder()
                .jobId(id)
                .status(JobStatus.CANCEL_REQUESTED)
                .reason("用户请求取消")
                .build());
    Running state = running.get(id);
    if (changed > 0 && state != null && state.stop == null) {
      state.stop = TaskStopReason.CANCELLED;
      state.thread.interrupt();
    }
  }

  /** 每秒核查持久终止意图和硬截止，只中断本实例实际持有的线程。 */
  @Scheduled(fixedDelay = 1000, scheduler = "controlScheduler")
  public void watchdog() {
    for (Running state : running.values()) {
      try (var ignored = TenantContext.bind(state.user)) {
        if (!clock.instant().isBefore(state.deadline) && state.stop == null) {
          state.stop = TaskStopReason.TIMED_OUT;
          // 本地时限先中断线程，再持久化；不能先等待可能阻塞的数据库查询。
          state.thread.interrupt();
          mapper.requestStop(
              JobStopRequest.builder()
                  .jobId(state.execution.jobId())
                  .ownerId(state.execution.ownerId())
                  .status(JobStatus.TIMEOUT_REQUESTED)
                  .reason("当前阶段达到配置截止时间")
                  .build());
        } else if (state.stop == null) {
          checkPersistent(state);
        }
        if (state.stop != null) {
          state.thread.interrupt();
        }
      } catch (RuntimeException e) {
        // 监控连接短暂失败不伪造取消，也不延长已有硬截止；工作线程的checkpoint仍会检查时限。
        LOGGER.warn(
            "watchdog_check_failed jobId={} failureType={}",
            state.execution.jobId(),
            e.getClass().getSimpleName());
      } finally {
        if (!clock.instant().isBefore(state.deadline) && state.stop == null) {
          state.stop = TaskStopReason.TIMED_OUT;
        }
        if (state.stop != null) {
          state.thread.interrupt();
        }
      }
    }
  }

  private void checkPersistent(Running state) {
    var row = mapper.get(state.execution.jobId());
    if (row == null) {
      state.stop = TaskStopReason.INTERRUPTED;
      return;
    }
    if ("CANCEL_REQUESTED".equals(row.getStatus())) {
      state.stop = TaskStopReason.CANCELLED;
    } else if ("TIMEOUT_REQUESTED".equals(row.getStatus())) {
      state.stop = TaskStopReason.TIMED_OUT;
    } else if (!"RUNNING".equals(row.getStatus())
        || !state.execution.ownerId().equals(row.getOwnerId())) {
      state.stop = TaskStopReason.INTERRUPTED;
    }
  }

  private void check(Running state) {
    if (state.stop != null) {
      throw new TaskStoppedException(state.stop);
    }
    if (!clock.instant().isBefore(state.deadline)) {
      state.stop = TaskStopReason.TIMED_OUT;
      throw new TaskStoppedException(state.stop);
    }
    if (Thread.currentThread().isInterrupted()) {
      throw new TaskStoppedException(TaskStopReason.INTERRUPTED);
    }
  }

  private void progress(Running state, ExecutionProgress value) {
    check(state);
    state.progress = value;
    Instant now = clock.instant();
    if (state.lastSaved != null && now.isBefore(state.lastSaved.plusSeconds(1))) {
      return;
    }
    JobStage row = stage(state, "RUNNING", null, null);
    mapper.stageProgress(row);
    state.lastSaved = now;
  }

  private JobStage stage(Running state, String status, String error, StageQuality quality) {
    return JobStage.builder()
        .jobId(state.execution.jobId())
        .stageName(state.execution.stage().name())
        .status(status)
        .error(error)
        .dataStatus(quality == null ? null : quality.status().name())
        .detailsJson(
            Jsons.write(
                new StageDetails(
                    quality,
                    state.progress,
                    state.deadline,
                    state.execution.retryOf(),
                    state.execution.retryOf() == null ? "FRESH_RUN" : "REVALIDATE_STORED_INPUTS",
                    state.diagnostic)))
        .build();
  }

  /**
   * 单个运行阶段的线程控制状态，持久意图仍以数据库为准。
   *
   * @author chenxuefeng
   */
  private static final class Running {
    /** 阶段业务及归属。 */
    final StageExecution execution;

    /** 执行线程领取时的身份；监控线程必须显式恢复。 */
    final UserView user;

    /** 只允许中断本实例拥有的线程。 */
    final Thread thread;

    /** 不因心跳而延长的截止时间。 */
    final Instant deadline;

    /** 已确认的终止原因。 */
    volatile TaskStopReason stop;

    /** 当前失败的公开分类，成功时为空。 */
    FailureDiagnosis diagnostic;

    /** 最近实际工作进度。 */
    volatile ExecutionProgress progress;

    /** 最近一次持久化进度的时刻，用于节流。 */
    Instant lastSaved;

    Running(StageExecution execution, Thread thread, Instant deadline) {
      this.execution = execution;
      this.user =
          TenantContext.user() == null
              ? new UserView(1, "local-owner", "ADMIN")
              : TenantContext.user();
      this.thread = thread;
      this.deadline = deadline;
    }
  }
}

package com.alphascope.scheduler.service.impl;

import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.exception.NotFoundException;
import com.alphascope.common.response.PageResponse;
import com.alphascope.common.response.ResultCode;
import com.alphascope.common.util.Jsons;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.vo.UserView;
import com.alphascope.market.service.MarketService;
import com.alphascope.operations.config.ProductProperties;
import com.alphascope.operations.service.CapacityService;
import com.alphascope.scheduler.control.JobExecutionControl;
import com.alphascope.scheduler.dto.CompletedJobQuery;
import com.alphascope.scheduler.dto.JobCompletion;
import com.alphascope.scheduler.dto.JobStatus;
import com.alphascope.scheduler.dto.JobSubmissionCommand;
import com.alphascope.scheduler.dto.PipelineCommand;
import com.alphascope.scheduler.dto.ScheduledJobQuery;
import com.alphascope.scheduler.entity.JobExecution;
import com.alphascope.scheduler.entity.JobStage;
import com.alphascope.scheduler.mapper.JobMapper;
import com.alphascope.scheduler.policy.AutomaticRetryPolicy;
import com.alphascope.scheduler.service.JobService;
import com.alphascope.scheduler.service.JobSnapshotService;
import com.alphascope.scheduler.service.ResearchPipelineService;
import com.alphascope.scheduler.vo.JobView;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JobService 的业务实现。
 *
 * <p>数据库持久队列为唯一任务入口；互斥使用独立连接，心跳与业务事务分离。
 *
 * @author chenxuefeng
 */
@Service
public class JobServiceImpl implements JobService {
  private final JobMapper mapper;
  private final SqlSessionFactory sessions;
  private final MarketService market;
  private final JobSnapshotService snapshots;
  private final ResearchPipelineService pipeline;
  private final JobExecutionControl control;
  private final String owner = UUID.randomUUID().toString();
  private final java.util.concurrent.ConcurrentHashMap<Long, OwnedJob> running =
      new java.util.concurrent.ConcurrentHashMap<>();
  private TransactionTemplate submissionTx;
  private Clock clock = Clock.systemUTC();

  @Autowired
  public void setClock(Clock clock) {
    this.clock = clock;
  }

  @Autowired
  public void setSubmissionTransactions(PlatformTransactionManager manager) {
    submissionTx = new TransactionTemplate(manager);
    submissionTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    submissionTx.setTimeout(30);
  }

  @Autowired private ProductProperties resourceOptions;
  private CapacityService capacity;

  @Autowired
  public void setCapacity(CapacityService capacity) {
    this.capacity = capacity;
  }

  public JobServiceImpl(
      JobMapper mapper,
      SqlSessionFactory sessions,
      MarketService market,
      JobSnapshotService snapshots,
      ResearchPipelineService pipeline,
      JobExecutionControl control) {
    this.mapper = mapper;
    this.sessions = sessions;
    this.market = market;
    this.snapshots = snapshots;
    this.pipeline = pipeline;
    this.control = control;
  }

  /** {@inheritDoc} */
  @Override
  public PageResponse<JobView> page(PageRequest request) {
    int page = request.getPage();
    int size = request.getSize();

    request.validate();
    var query =
        request instanceof com.alphascope.scheduler.dto.JobQuery q
            ? q
            : new com.alphascope.scheduler.dto.JobQuery();
    query.setPage(page);
    query.setSize(size);
    return new PageResponse<>(
        mapper.page(query).stream().map(JobView::from).toList(), mapper.count(query), page, size);
  }

  /** {@inheritDoc} */
  @Override
  public JobView get(long id) {
    var row = mapper.get(id);
    if (row == null) {
      throw new NotFoundException("任务不存在");
    }
    return new JobView(
        row.getId(),
        row.getJobName(),
        row.getDataScope(),
        row.getBusinessDate(),
        row.getAttempt(),
        row.getStatus(),
        row.getError(),
        row.getStartedAt(),
        row.getEndedAt(),
        row.getHeartbeatAt(),
        row.getRetryOf(),
        row.getInputHash(),
        mapper.stages(id).stream().map(JobView.Stage::from).toList(),
        row.getDataStatus());
  }

  /** {@inheritDoc} */
  @Override
  public PageResponse<JobView> history(long id, PageRequest request) {
    var current = get(id);
    request.validate();
    var snapshots =
        mapper.attempts(
            new com.alphascope.scheduler.dto.JobHistoryQuery(
                id, request.getSize(), (request.getPage() - 1) * request.getSize()));
    var items = new java.util.ArrayList<JobView>();
    for (var row : snapshots) {
      if (!Jsons.hash(row.getExecutionJson()).equals(row.getContentHash()))
        throw new IllegalStateException("执行历史指纹不符");
      items.add(Jsons.read(row.getExecutionJson(), JobView.class));
    }
    // 旧版已经产生独立重试记录，保留原ID和关联，不改写冻结报告。
    var parents = new java.util.ArrayList<JobView>();
    var seen = new java.util.HashSet<Long>();
    var parent = current.retryOf();
    while (parent != null && seen.add(parent) && parents.size() < 1000) {
      var previous = get(parent);
      parents.add(previous);
      parent = previous.retryOf();
    }
    long own = mapper.attemptCount(id);
    long begin = (long) (request.getPage() - 1) * request.getSize();
    if (items.size() < request.getSize()) {
      long offset = Math.max(0, begin - own);
      for (int i = (int) offset; i < parents.size() && items.size() < request.getSize(); i++)
        items.add(parents.get(i));
    }
    return new PageResponse<>(items, own + parents.size(), request.getPage(), request.getSize());
  }

  private String key(String type, LocalDate date) {
    return "alphascope:" + TenantContext.id() + ":" + type + ":" + date;
  }

  /** {@inheritDoc} */
  @Override
  public long submit(JobSubmissionCommand command) {
    return submissionTx == null
        ? enqueue(command)
        : Objects.requireNonNull(submissionTx.execute(status -> enqueue(command)));
  }

  private long enqueue(JobSubmissionCommand command) {
    String type = command.reportType();
    LocalDate date = command.businessDate();
    boolean force = command.force();
    Long retryOf = command.retryOf();
    if (type == null
        || date == null
        || date.isBefore(LocalDate.of(1990, 1, 1))
        || !Set.of("DAILY", "WEEKLY", "MARKET_HISTORY", "MARKET_UNIVERSE", "MARKET_FULL")
            .contains(type)
        || date.isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))))) {
      throw new BusinessException(
          ResultCode.INVALID_ARGUMENT, 400, "任务类型须为日报、周报、市场历史或北交所目录或三所截面补齐，业务日期须在1990年至上海当前日期之间");
    }
    if (Set.of("MARKET_HISTORY", "MARKET_UNIVERSE", "MARKET_FULL").contains(type)
        && (TenantContext.user() == null || !"ADMIN".equals(TenantContext.user().role()))) {
      throw new BusinessException(ResultCode.FORBIDDEN, 403, "市场历史补齐需要管理员权限");
    }
    var now = java.time.ZonedDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
    if (!market.synthetic()
        && !"MARKET_UNIVERSE".equals(type)
        && date.equals(now.toLocalDate())
        && now.toLocalTime().isBefore(java.time.LocalTime.of(15, 30))) {
      throw new BusinessException(
          ResultCode.DATA_UNAVAILABLE,
          409,
          "当前日期的收盘行情尚未形成；本系统生成收盘报告，当日最早15:30后提交，之后仍需等待供应商发布。请选前一已完成交易日补跑，不会自动更换业务日期。");
    }
    try (var session = sessions.openSession(true)) {
      var locks = session.getMapper(JobMapper.class);
      String key = key(type, date);
      if (!Integer.valueOf(1).equals(locks.lock(key))) {
        var active = mapper.active(type, date, market.dataScope());
        if (active != null) {
          return active;
        }
        throw new IllegalStateException("该日期任务正在运行");
      }
      try {
        if (command.automatic()) {
          var query =
              ScheduledJobQuery.builder()
                  .jobName(type)
                  .businessDate(date)
                  .dataScope(market.dataScope())
                  .build();
          var previous = mapper.latestForSchedule(query);
          if (previous != null) {
            if (!AutomaticRetryPolicy.allowed(
                previous,
                mapper.scheduledAttempts(query),
                LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai"))))
              return previous.getId();
            retryOf = previous.getId();
          }
        }
        String hash = Jsons.hash(Jsons.write(snapshots.capture(date)));
        Long active = mapper.active(type, date, market.dataScope());
        if (active != null) {
          return active;
        }
        if (!force) {
          Long done =
              mapper.completed(
                  CompletedJobQuery.builder()
                      .jobName(type)
                      .businessDate(date)
                      .dataScope(market.dataScope())
                      .inputHash(hash)
                      .build());
          if (done != null) {
            return done;
          }
        }
        if (retryOf != null) {
          if (mapper.lockRow(retryOf) == null) throw new NotFoundException("原任务不存在");
          var previous = get(retryOf);
          if (JobStatus.valueOf(previous.status()).active()) return retryOf;
          if (!JobStatus.valueOf(previous.status()).retryable())
            throw new IllegalArgumentException("该任务已结束，不能重复重试");
          if (!previous.jobName().equals(type) || !previous.businessDate().equals(date))
            throw new IllegalArgumentException("重试必须沿用原业务范围");
          if (capacity != null) capacity.requireJobSlot();
          var history = new com.alphascope.scheduler.entity.JobAttemptHistory();
          history.setJobId(retryOf);
          history.setAttempt(previous.attempt());
          history.setExecutionJson(Jsons.write(previous));
          history.setContentHash(Jsons.hash(history.getExecutionJson()));
          mapper.archiveAttempt(history);
          long reserved =
              resourceOptions == null
                  ? 0
                  : Set.of("DAILY", "WEEKLY").contains(type)
                      ? resourceOptions.getReportReservationBytes()
                      : resourceOptions.getCollectionReservationBytes();
          if (mapper.requeue(
                  new com.alphascope.scheduler.dto.JobRetryCommand(
                      retryOf, mapper.attempt(type, date), market.dataScope(), hash, reserved))
              != 1) throw new IllegalStateException("任务状态已变化，请刷新");
          mapper.clearStages(retryOf);
          return retryOf;
        }
        if (capacity != null) capacity.requireJobSlot();
        JobExecution row = new JobExecution();
        row.setJobName(type);
        row.setReservationBytes(
            resourceOptions == null
                ? 0
                : Set.of("MARKET_HISTORY", "MARKET_UNIVERSE", "MARKET_FULL").contains(type)
                    ? resourceOptions.getCollectionReservationBytes()
                    : resourceOptions.getReportReservationBytes());
        row.setDataScope(market.dataScope());
        row.setBusinessDate(date);
        row.setInputHash(hash);
        row.setRetryOf(retryOf);
        row.setAttempt(mapper.attempt(type, date));
        mapper.start(row);
        return row.getId();
      } finally {
        locks.unlock(key);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public long run(String type, LocalDate date) {
    long id =
        submit(
            JobSubmissionCommand.builder()
                .reportType(type)
                .businessDate(date)
                .force(false)
                .retryOf(null)
                .build());
    process(id);
    return id;
  }

  /** {@inheritDoc} */
  @Override
  public long retry(long id) {
    var old = get(id);
    if (JobStatus.valueOf(old.status()).active()) return id;
    if (!JobStatus.valueOf(old.status()).retryable()) {
      throw new IllegalArgumentException("只能重试失败、中断、取消或超时任务");
    }
    return submit(
        JobSubmissionCommand.builder()
            .reportType(old.jobName())
            .businessDate(old.businessDate())
            .force(true)
            .retryOf(id)
            .build());
  }

  /** {@inheritDoc} */
  @Override
  public void heartbeat() {
    for (OwnedJob current : running.values())
      try (var ignored = TenantContext.bind(current.user())) {
        mapper.heartbeat(current.id(), owner);
      }
  }

  /** {@inheritDoc} */
  @Override
  public void recover() {
    for (var job : mapper.stale()) {
      try (var session = sessions.openSession(true)) {
        var locks = session.getMapper(JobMapper.class);
        String k = key(job.getJobName(), job.getBusinessDate());
        // 持有工作锁的活进程即使暂时心跳延迟，也不会被误判或双重执行。
        if (Integer.valueOf(1).equals(locks.lock(k))) {
          try {
            long id = job.getId();
            String stopped =
                "CANCEL_REQUESTED".equals(job.getStatus())
                    ? "CANCELLED"
                    : "TIMEOUT_REQUESTED".equals(job.getStatus()) ? "TIMED_OUT" : "INTERRUPTED";
            mapper.finish(id, stopped, "进程失联且工作锁已释放；保留终止意图，可显式重试");
            mapper.stopRunningStages(
                JobStage.builder().jobId(id).status(stopped).error("执行进程已退出，阶段没有完成").build());
          } finally {
            locks.unlock(k);
          }
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void processNext() {
    recover();
    Long id = mapper.pending();
    if (id != null) {
      process(id);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void processSharedNext() {
    if (TenantContext.user() != null && !"ADMIN".equals(TenantContext.user().role())) return;
    recover();
    var id = mapper.pendingShared();
    if (id != null) process(id);
  }

  /** {@inheritDoc} */
  @Override
  public void process(long id) {
    var job = get(id);
    if (!"PENDING".equals(job.status())) {
      return;
    }
    if (!market.dataScope().equals(job.dataScope())) {
      mapper.finish(id, "INTERRUPTED", "数据源配置已变更，请重试创建当前配置的任务");
      return;
    }
    String type = job.jobName();
    LocalDate requested = job.businessDate();
    String k = key(type, requested);
    try (var session = sessions.openSession(true)) {
      var locks = session.getMapper(JobMapper.class);
      if (!Integer.valueOf(1).equals(locks.lock(k))) {
        return;
      }
      try {
        if (mapper.claim(id, owner) != 1) {
          return;
        }
        running.put(
            id,
            new OwnedJob(
                id,
                TenantContext.user() == null
                    ? new UserView(1, "local-owner", "ADMIN")
                    : TenantContext.user()));
        boolean restoreInterrupt = false;
        var previousContext = MDC.getCopyOfContextMap();
        MDC.put("jobId", String.valueOf(id));
        try {
          var result =
              pipeline.run(
                  new PipelineCommand(
                      id,
                      type,
                      requested,
                      mapper.attemptCount(id) > 0 ? null : job.retryOf(),
                      owner));
          restoreInterrupt = Thread.interrupted();
          if (result.inputHash() != null) {
            mapper.inputHash(id, result.inputHash());
          }
          complete(
              JobCompletion.builder()
                  .jobId(id)
                  .ownerId(owner)
                  .status(result.status())
                  .dataStatus(result.quality())
                  .error(result.error())
                  .build());
        } finally {
          if (previousContext == null) {
            MDC.clear();
          } else {
            MDC.setContextMap(previousContext);
          }
          if (restoreInterrupt) {
            Thread.currentThread().interrupt();
          }
        }
      } finally {
        running.remove(id);
        boolean interrupted = Thread.interrupted();
        try {
          locks.unlock(k);
        } finally {
          if (interrupted) {
            Thread.currentThread().interrupt();
          }
        }
      }
    }
  }

  private void complete(JobCompletion completion) {
    if (mapper.complete(completion) != 0) {
      return;
    }
    var current = mapper.get(completion.jobId());
    if (current == null || !completion.ownerId().equals(current.getOwnerId())) {
      return;
    }
    JobStatus stopped =
        "CANCEL_REQUESTED".equals(current.getStatus())
            ? JobStatus.CANCELLED
            : "TIMEOUT_REQUESTED".equals(current.getStatus()) ? JobStatus.TIMED_OUT : null;
    if (stopped != null) {
      mapper.complete(
          JobCompletion.builder()
              .jobId(completion.jobId())
              .ownerId(completion.ownerId())
              .status(stopped)
              .dataStatus(completion.dataStatus())
              .error("已按持久终止请求结束；先前完成的证据和已归档报告保留")
              .build());
    }
  }

  /** {@inheritDoc} */
  @Override
  public JobView cancel(long id) {
    get(id);
    control.cancel(id);
    return get(id);
  }

  /**
   * 一次领取的任务归属，心跳线程不能从其他请求推断身份。
   *
   * @author chenxuefeng
   * @param id 任务主键
   * @param user 任务所属的已启用身份
   */
  private record OwnedJob(long id, UserView user) {}
}

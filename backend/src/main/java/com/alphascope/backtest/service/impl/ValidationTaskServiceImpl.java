package com.alphascope.backtest.service.impl;

import com.alphascope.backtest.calculator.ResearchValidationVerifier;
import com.alphascope.backtest.dto.ResearchValidationRequest;
import com.alphascope.backtest.dto.ValidationCommand;
import com.alphascope.backtest.dto.ValidationTaskMutation;
import com.alphascope.backtest.entity.ResearchValidationTask;
import com.alphascope.backtest.mapper.ValidationTaskMapper;
import com.alphascope.backtest.service.ResearchValidationService;
import com.alphascope.backtest.service.ValidationTaskService;
import com.alphascope.backtest.vo.ValidationSummary;
import com.alphascope.backtest.vo.ValidationTaskView;
import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.exception.NotFoundException;
import com.alphascope.common.response.PageResponse;
import com.alphascope.common.response.ResultCode;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.ExecutionProgress;
import com.alphascope.common.task.TaskStopReason;
import com.alphascope.common.task.TaskStoppedException;
import com.alphascope.common.util.Jsons;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.vo.UserView;
import com.alphascope.market.service.MarketService;
import com.alphascope.operations.policy.SourceEvidence;
import com.alphascope.operations.service.CapacityService;
import com.alphascope.operations.service.SourceLicenseService;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.signal.rule.RiskRules;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单消费者验证队列；计算期间的进度只写内存，独立监控线程持久化，避免写入只读捕获事务。
 *
 * <p>最终SQL按归属、持久停止意图和截止发布结果，取消或过期不会被成功回调覆盖。
 *
 * @author chenxuefeng
 */
@Service
public class ValidationTaskServiceImpl implements ValidationTaskService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ValidationTaskServiceImpl.class);
  private final ValidationTaskMapper mapper;
  private final ResearchValidationService validation;
  private final MarketService market;
  private final PortfolioService portfolio;
  private final RiskRules rules;
  private final AlphaScopeProperties properties;
  private final Clock clock;
  private CapacityService capacity;

  @Autowired
  public void setCapacity(CapacityService capacity) {
    this.capacity = capacity;
  }

  private final String owner = UUID.randomUUID().toString();
  private final AtomicReference<ActiveRun> active = new AtomicReference<>();

  public ValidationTaskServiceImpl(
      ValidationTaskMapper mapper,
      ResearchValidationService validation,
      MarketService market,
      PortfolioService portfolio,
      RiskRules rules,
      AlphaScopeProperties properties,
      Clock clock) {
    this.mapper = mapper;
    this.validation = validation;
    this.market = market;
    this.portfolio = portfolio;
    this.rules = rules;
    this.properties = properties;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public synchronized ValidationTaskView submit(ResearchValidationRequest request) {
    if (market.synthetic())
      throw new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "演示模式不接收真实研究验证");
    if (request == null
        || request.getStart() == null
        || request.getEnd() == null
        || request.getScope() == null
        || request.getStart().isAfter(request.getEnd())
        || request.getStart().plusYears(10).isBefore(request.getEnd())
        || request.getEnd().isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))))) {
      throw new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "验证起止日期必填、顺序正确，最多十年且不能使用未来日期");
    }
    var available =
        request.getScope() == ResearchValidationRequest.Scope.MARKET
            ? List.<String>of()
            : portfolio.watchlist().stream()
                .filter(row -> Boolean.TRUE.equals(row.getEnabled()))
                .map(row -> row.getSymbol())
                .distinct()
                .sorted()
                .toList();
    var selected =
        request.getScope() == ResearchValidationRequest.Scope.MARKET
            ? List.<String>of()
            : request.getSymbols() == null || request.getSymbols().isEmpty()
                ? available
                : request.getSymbols().stream().distinct().sorted().toList();
    if (selected.size() > properties.getValidation().getMaximumSymbols()
        || !available.containsAll(selected)) {
      throw new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "验证证券须来自已启用自选，且不超过配置的最多数量");
    }
    if (request.getScope() != ResearchValidationRequest.Scope.MARKET && selected.isEmpty())
      throw new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "尚无已启用自选，请先登记研究范围");
    var frozen =
        ResearchValidationRequest.builder()
            .start(request.getStart())
            .end(request.getEnd())
            .scope(request.getScope())
            .symbols(List.copyOf(selected))
            .build();
    var command = new ValidationCommand(frozen, rules.snapshot());
    String json = Jsons.write(command), scope = market.providerStatus().dataScope();
    String key = Jsons.hash(json + "\n" + scope);
    var duplicate = mapper.active(key);
    if (duplicate != null) return view(duplicate);
    if (capacity != null)
      capacity.requireEvidenceSpace(properties.getValidation().getMaximumEvidenceBytes());
    if (mapper.activeCount() >= properties.getValidation().getMaximumActive())
      throw new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "活动验证任务已达上限，请等待或取消后再提交");
    var row = new ResearchValidationTask();
    row.setReservationBytes(properties.getValidation().getMaximumEvidenceBytes());
    row.setScope(frozen.getScope().name());
    row.setDataScope(scope);
    row.setActiveKey(key);
    row.setRequestJson(json);
    try {
      mapper.insert(row);
    } catch (DuplicateKeyException e) {
      var concurrent = mapper.active(key);
      if (concurrent == null) throw e;
      return view(concurrent);
    }
    return get(row.getId());
  }

  /** {@inheritDoc} */
  @Override
  public PageResponse<ValidationTaskView> page(PageRequest request) {
    request.validate();
    return new PageResponse<>(
        mapper.page(request).stream().map(this::view).toList(),
        mapper.count(),
        request.getPage(),
        request.getSize());
  }

  /** {@inheritDoc} */
  @Override
  public ValidationTaskView get(long id) {
    return view(require(id));
  }

  /** {@inheritDoc} */
  @Override
  public ValidationTaskView cancel(long id) {
    require(id);
    int changed = mapper.cancel(ValidationTaskMutation.builder().id(id).now(now()).build());
    var run = active.get();
    if (changed > 0 && run != null && run.id == id) run.stop(TaskStopReason.CANCELLED);
    return get(id);
  }

  /** {@inheritDoc} */
  @Override
  public String evidence(long id) {
    var row = require(id);
    if (!"SUCCESS".equals(row.getStatus()))
      throw new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "该验证任务尚无成功发布的完整证据");
    String evidence = mapper.evidence(id);
    if (evidence == null || !Jsons.hash(evidence).equals(row.getEvidenceSha256()))
      throw new IllegalStateException("验证证据指纹不一致，拒绝下载");
    return evidence;
  }

  /** {@inheritDoc} */
  @Override
  public void processNext() {
    if (active.get() != null) return;
    Long id = mapper.pending();
    if (id == null) return;
    Instant deadline = clock.instant().plusSeconds(properties.getValidation().getTimeoutSeconds());
    var run = new ActiveRun(id, Thread.currentThread(), deadline);
    if (!active.compareAndSet(null, run)) return;
    boolean claimed = false;
    try {
      claimed =
          mapper.claim(
                  ValidationTaskMutation.builder()
                      .id(id)
                      .owner(owner)
                      .now(now())
                      .deadline(local(deadline))
                      .build())
              == 1;
      if (!claimed) return;
      run.claimed = true;
      var row = require(id);
      if (market.synthetic() || !row.getDataScope().equals(market.providerStatus().dataScope()))
        throw new IllegalStateException("冻结数据范围与当前配置不符，不能混用真实或演示来源");
      var command = Jsons.read(row.getRequestJson(), ValidationCommand.class);
      RiskRules.restore(command.definition());
      try (var ignored =
          ExecutionContext.bind(() -> checkpoint(run), progress -> run.progress.set(progress))) {
        var report = validation.validate(command);
        ExecutionContext.progress(new ExecutionProgress(0, 1, "正在逐层复核冻结证据", clock.instant()));
        var verified = ResearchValidationVerifier.verify(report);
        ExecutionContext.checkpoint();
        String evidence = Jsons.write(report);
        if (evidence.getBytes(StandardCharsets.UTF_8).length
            > properties.getValidation().getMaximumEvidenceBytes())
          throw new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "完整证据超出大小限制，请减少证券或日期区间");
        String summary = Jsons.write(ValidationSummary.from(report, verified));
        mapper.finish(
            ValidationTaskMutation.builder()
                .id(id)
                .owner(owner)
                .now(now())
                .status("SUCCESS")
                .summary(summary)
                .evidence(evidence)
                .evidenceHash(Jsons.hash(evidence))
                .message("验证计算完成；请分别查看证据完整性和方法结论")
                .build());
      }
    } catch (TaskStoppedException e) {
      if (claimed) finishStopped(run, e.getReason());
    } catch (RuntimeException e) {
      LOGGER.error(
          "validation_failed tenantId={} taskId={} type={}",
          TenantContext.id(),
          id,
          e.getClass().getSimpleName(),
          e);
      if (claimed) {
        TaskStopReason reason = run.stop.get();
        if (reason != null || Thread.currentThread().isInterrupted())
          finishStopped(run, reason == null ? TaskStopReason.INTERRUPTED : reason);
        else
          mapper.finish(
              ValidationTaskMutation.builder()
                  .id(id)
                  .owner(owner)
                  .now(now())
                  .status("FAILED")
                  .message(safeFailure(e))
                  .build());
      }
    } finally {
      active.compareAndSet(run, null);
      Thread.interrupted();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void monitor() {
    var run = active.get();
    if (run == null) {
      mapper.recover(
          ValidationTaskMutation.builder()
              .now(now())
              .staleBefore(
                  local(clock.instant().minusSeconds(properties.getValidation().getStaleSeconds())))
              .build());
      return;
    }
    if (!run.claimed) return;
    try (var ignored = TenantContext.bind(run.user)) {
      monitorActive(run);
    }
  }

  private void monitorActive(ActiveRun run) {
    // 先中断到期执行，再做数据库读写，避免停止被连接等待阻塞。
    if (!clock.instant().isBefore(run.deadline)) run.stop(TaskStopReason.TIMED_OUT);
    var row = mapper.get(run.id);
    if (row == null || !owner.equals(row.getOwnerToken())) {
      run.stop(TaskStopReason.INTERRUPTED);
      return;
    }
    if ("CANCEL_REQUESTED".equals(row.getStatus())) run.stop(TaskStopReason.CANCELLED);
    if ("TIMEOUT_REQUESTED".equals(row.getStatus())) run.stop(TaskStopReason.TIMED_OUT);
    var progress = run.progress.get();
    mapper.pulse(
        ValidationTaskMutation.builder()
            .id(run.id)
            .owner(owner)
            .now(now())
            .completed((int) progress.completed())
            .total((int) progress.total())
            .message(progress.message())
            .build());
  }

  private void checkpoint(ActiveRun run) {
    if (!clock.instant().isBefore(run.deadline))
      run.stop.compareAndSet(null, TaskStopReason.TIMED_OUT);
    var reason = run.stop.get();
    if (reason != null) throw new TaskStoppedException(reason);
  }

  private void finishStopped(ActiveRun run, TaskStopReason reason) {
    Thread.interrupted();
    String status =
        reason == TaskStopReason.CANCELLED
            ? "CANCELLED"
            : reason == TaskStopReason.TIMED_OUT ? "TIMED_OUT" : "INTERRUPTED";
    mapper.finish(
        ValidationTaskMutation.builder()
            .id(run.id)
            .owner(owner)
            .now(now())
            .status(status)
            .message("验证已停止，未发布成功证据")
            .build());
  }

  private ResearchValidationTask require(long id) {
    var row = mapper.get(id);
    if (row == null) throw new NotFoundException("验证任务不存在");
    return row;
  }

  @org.springframework.beans.factory.annotation.Autowired private SourceLicenseService licenses;

  private final java.util.concurrent.ConcurrentHashMap<String, java.util.Set<String>> sourceCache =
      new java.util.concurrent.ConcurrentHashMap<>();

  private java.util.Set<String> frozenSources(ResearchValidationTask row) {
    if (licenses == null || !licenses.isEnforced() || row.getSummaryJson() == null) return null;
    String key = TenantContext.id() + ":" + row.getId() + ":" + row.getEvidenceSha256();
    if (sourceCache.size() > 128 && !sourceCache.containsKey(key)) sourceCache.clear();
    return sourceCache.computeIfAbsent(
        key,
        ignored -> {
          String raw = mapper.evidence(row.getId());
          if (raw == null || !Jsons.hash(raw).equals(row.getEvidenceSha256()))
            throw new IllegalStateException("验证冻结证据指纹不符");
          return SourceEvidence.inspect(
                  Jsons.read(raw, com.fasterxml.jackson.databind.JsonNode.class))
              .sources();
        });
  }

  private ValidationTaskView view(ResearchValidationTask row) {
    var command = Jsons.read(row.getRequestJson(), ValidationCommand.class);
    return ValidationTaskView.builder()
        .id(row.getId())
        .status(row.getStatus())
        .scope(row.getScope())
        .start(command.request().getStart())
        .end(command.request().getEnd())
        .symbols(command.request().getSymbols())
        .dataScope(row.getDataScope())
        .completed(row.getCompletedUnits() == null ? 0 : row.getCompletedUnits())
        .total(row.getTotalUnits() == null ? 0 : row.getTotalUnits())
        .message(row.getMessage())
        .createdAt(row.getCreatedAt())
        .startedAt(row.getStartedAt())
        .finishedAt(row.getFinishedAt())
        .heartbeatAt(row.getHeartbeatAt())
        .deadlineAt(row.getDeadlineAt())
        .evidenceSha256(row.getEvidenceSha256())
        .inputSources(frozenSources(row))
        .summary(
            row.getSummaryJson() == null
                ? null
                : Jsons.read(row.getSummaryJson(), ValidationSummary.class))
        .build();
  }

  private String safeFailure(RuntimeException error) {
    String text =
        error instanceof IllegalArgumentException || error instanceof BusinessException
            ? error.getMessage()
            : "验证执行失败：" + error.getClass().getSimpleName();
    return text == null ? "验证执行失败" : text.substring(0, Math.min(450, text.length()));
  }

  private LocalDateTime now() {
    return local(clock.instant());
  }

  private LocalDateTime local(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneId.of("Asia/Shanghai"));
  }

  /**
   * 当前本机执行上下文；停止原因只允许第一个请求写入，持久SQL最终裁定终态。
   *
   * @author chenxuefeng
   */
  private static final class ActiveRun {
    private final long id;

    /** 领取任务的已验证身份。 */
    private final UserView user;

    private final Thread thread;
    private final Instant deadline;
    private volatile boolean claimed;
    private final AtomicReference<TaskStopReason> stop = new AtomicReference<>();
    private final AtomicReference<ExecutionProgress> progress =
        new AtomicReference<>(new ExecutionProgress(0, 0, "正在读取研究资料", Instant.EPOCH));

    private ActiveRun(long id, Thread thread, Instant deadline) {
      this.id = id;
      this.user =
          TenantContext.user() == null
              ? new UserView(1, "local-owner", "ADMIN")
              : TenantContext.user();
      this.thread = thread;
      this.deadline = deadline;
    }

    private void stop(TaskStopReason reason) {
      stop.compareAndSet(null, reason);
      thread.interrupt();
    }
  }
}

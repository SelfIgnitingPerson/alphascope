package com.alphascope.market.service.impl;

import com.alphascope.common.diagnostic.FailureDiagnosis;
import com.alphascope.common.diagnostic.FailureDiagnostics;
import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.response.ResultCode;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.market.dto.ProviderProbeRequest;
import com.alphascope.market.provider.MarketCapability;
import com.alphascope.market.provider.MarketDataProvider;
import com.alphascope.market.service.ProviderProbeService;
import com.alphascope.market.vo.ProviderProbeView;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 全局单任务、无等待队列、20秒截止与每通道60秒限频；超时后旧调用结束前不接收新探测。
 *
 * @author chenxuefeng
 */
@Service
public class ProviderProbeServiceImpl implements ProviderProbeService {
  private static final Set<String> CHANNELS =
      Set.of("BAOSTOCK", "SINA", "EASTMONEY", "TUSHARE", "EXCHANGE_CALENDAR", "DEMO");
  private static final Set<MarketCapability> CAPABILITIES =
      Set.of(
          MarketCapability.TRADING_CALENDAR,
          MarketCapability.STOCK_DAILY,
          MarketCapability.INDEX_DAILY);
  private final MarketDataProvider provider;
  private final Clock clock;
  private final Duration timeout;
  private final AtomicBoolean running = new AtomicBoolean();
  private final Map<String, Instant> nextAttempts = new HashMap<>();
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(
          1,
          1,
          0,
          TimeUnit.SECONDS,
          new SynchronousQueue<>(),
          Thread.ofPlatform().daemon().name("provider-probe-", 0).factory(),
          new ThreadPoolExecutor.AbortPolicy());

  @Autowired
  public ProviderProbeServiceImpl(MarketDataProvider provider, Clock clock) {
    this(provider, clock, Duration.ofSeconds(20));
  }

  /**
   * 为隔离测试提供短截止时间，不修改生产网络与访问保护。
   *
   * @param provider 指定路由
   * @param clock 限频时钟
   * @param timeout 本次等待上限
   */
  ProviderProbeServiceImpl(MarketDataProvider provider, Clock clock, Duration timeout) {
    this.provider = provider;
    this.clock = clock;
    this.timeout = timeout;
  }

  /** {@inheritDoc} */
  @Override
  public ProviderProbeView test(ProviderProbeRequest input) {
    var user = TenantContext.user();
    if (user == null || !"ADMIN".equals(user.role()))
      throw new BusinessException(ResultCode.FORBIDDEN, 403, "测试共享行情通道需要管理员权限");
    if (input == null
        || input.getChannel() == null
        || input.getCapability() == null
        || !CHANNELS.contains(input.getChannel())
        || !CAPABILITIES.contains(input.getCapability())
        || input.getDate() == null
        || input.getDate().isBefore(LocalDate.of(1990, 1, 1))
        || input.getDate().isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")))))
      throw new IllegalArgumentException("请选择已配置通道、基础能力及1990年以来且不晚于上海今天的日期");
    // 复制绑定后的条件，避免调用方在异步执行期间更改对象。
    var request =
        new ProviderProbeRequest(input.getChannel(), input.getCapability(), input.getDate());
    var initial = new ProbeAttempt(request, clock.instant(), System.nanoTime(), null);
    Instant next;
    synchronized (nextAttempts) {
      if (running.get()) return skipped(initial, SkipReason.BUSY);
      next = nextAttempts.get(request.getChannel());
      if (next != null && initial.started().isBefore(next))
        return skipped(
            new ProbeAttempt(request, initial.started(), initial.begin(), next),
            SkipReason.RATE_LIMITED);
      running.set(true);
      next = initial.started().plusSeconds(60);
      nextAttempts.put(request.getChannel(), next);
    }
    final var attempt = new ProbeAttempt(request, initial.started(), initial.begin(), next);
    final Map<String, String> context = MDC.getCopyOfContextMap();
    var workerEntered = new AtomicBoolean();
    java.util.concurrent.Future<ProviderProbeView> future;
    try {
      future =
          executor.submit(
              () -> {
                workerEntered.set(true);
                try (var ignored = TenantContext.bind(user)) {
                  if (context != null) MDC.setContextMap(context);
                  return perform(attempt);
                } finally {
                  MDC.clear();
                  running.set(false);
                }
              });
    } catch (RejectedExecutionException failure) {
      running.set(false);
      return skipped(attempt, SkipReason.EXECUTOR_CLOSED);
    }
    try {
      return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException failure) {
      cancel(future, workerEntered);
      return result(
          attempt,
          new Outcome(
              "TIMED_OUT",
              null,
              new FailureDiagnosis(
                  "PROBE_TIMEOUT",
                  "本次测试已超时",
                  "在20秒内未能完成指定能力的样本核验，不能据此判断永久不可用。",
                  "不会保存不完整行情，也不会自动切换其他通道。",
                  "等待底层请求退出并经过限频后再试；检查网络、代理及供应商响应。")));
    } catch (InterruptedException failure) {
      cancel(future, workerEntered);
      Thread.currentThread().interrupt();
      return skipped(attempt, SkipReason.INTERRUPTED);
    } catch (ExecutionException failure) {
      return result(
          attempt, new Outcome("FAILED", null, FailureDiagnostics.from(failure.getCause())));
    }
  }

  /** 先核对已保存的能力和保护，再调用唯一指定通道；状态检查也受整体截止时间约束。 */
  private ProviderProbeView perform(ProbeAttempt attempt) {
    var request = attempt.request();
    ExecutionContext.checkpoint();
    var channel =
        provider.status().stream()
            .filter(value -> value.channel().equals(request.getChannel()))
            .findFirst()
            .orElse(null);
    if (channel == null) return skipped(attempt, SkipReason.NOT_CONFIGURED);
    if (provider.synthetic()) return skipped(attempt, SkipReason.DEMO);
    var ability =
        channel.capabilities() == null
            ? null
            : channel.capabilities().stream()
                .filter(value -> value.capability() == request.getCapability())
                .findFirst()
                .orElse(null);
    if (ability != null && !ability.supported()) return skipped(attempt, SkipReason.UNSUPPORTED);
    var diagnostic =
        ability != null && ability.diagnostic() != null
            ? ability.diagnostic()
            : channel.diagnostic();
    if ("ACCESS_RESTRICTED".equals(channel.state())
        || (ability != null
            && Set.of("COOLDOWN", "ACCESS_RESTRICTED", "ACCESS_COOLDOWN")
                .contains(ability.state())))
      return result(attempt, new Outcome("SKIPPED", null, diagnostic));
    ExecutionContext.checkpoint();
    try {
      var sample = provider.probe(request);
      if (sample == null) throw new IllegalStateException("EMPTY_RESPONSE");
      return result(attempt, new Outcome("SUCCESS", sample, null));
    } catch (RuntimeException failure) {
      var observed =
          provider.status().stream()
              .filter(value -> value.channel().equals(request.getChannel()))
              .flatMap(value -> value.capabilities().stream())
              .filter(
                  value ->
                      value.capability() == request.getCapability()
                          && value.lastFailure() != null
                          && !value.lastFailure().isBefore(attempt.started())
                          && value.diagnostic() != null)
              .findFirst();
      var reason =
          observed.isPresent() ? observed.get().diagnostic() : FailureDiagnostics.from(failure);
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn(
              "provider_probe_failed channel={} capability={} diagnostic={}",
              request.getChannel(),
              request.getCapability(),
              reason.code());
      return result(attempt, new Outcome("FAILED", null, reason));
    }
  }

  /**
   * 一次测试的不可变执行上下文，禁止后台读取可变表单。
   *
   * @author chenxuefeng
   */
  private record ProbeAttempt(
      /** 完整测试条件。 */
      ProviderProbeRequest request,
      /** 实际开始时间。 */
      Instant started,
      /** 单调计时起点。 */
      long begin,
      /** 本机限频截止。 */
      Instant retryAt) {}

  /**
   * 单次执行结论，不把网络失败转换成零条成功。
   *
   * @author chenxuefeng
   */
  private record Outcome(
      /** 执行状态。 */
      String status,
      /** 核验摘要。 */
      com.alphascope.market.vo.ProviderProbeSample sample,
      /** 受控诊断。 */
      FailureDiagnosis diagnostic) {}

  /**
   * 未执行的确定原因，与已访问上游后失败区分。
   *
   * @author chenxuefeng
   */
  private enum SkipReason {
    /** 测试仍在运行或退出。 */
    BUSY,
    /** 同通道60秒限频。 */
    RATE_LIMITED,
    /** 执行器未接收请求。 */
    EXECUTOR_CLOSED,
    /** 路由未配置该来源。 */
    NOT_CONFIGURED,
    /** 演示输入不能证明连通。 */
    DEMO,
    /** 未实现该基础能力。 */
    UNSUPPORTED,
    /** 请求线程已中断。 */
    INTERRUPTED
  }

  /** 构造本地未执行结论；检查时间不替换原始拒绝时间。 */
  private ProviderProbeView skipped(ProbeAttempt attempt, SkipReason reason) {
    String title =
        switch (reason) {
          case BUSY -> "已有通道测试正在执行";
          case RATE_LIMITED -> "当前通道测试过于频繁";
          case EXECUTOR_CLOSED -> "测试执行器暂不可用";
          case NOT_CONFIGURED -> "该通道当前未配置";
          case DEMO -> "演示模式不核验真实连通性";
          case UNSUPPORTED -> "该通道未实现此项能力";
          case INTERRUPTED -> "本次测试已中断";
        };
    String action =
        switch (reason) {
          case BUSY -> "请等待当前测试结束；超时后的底层连接未退出前也不会启动新测试。";
          case RATE_LIMITED -> "同一通道每60秒最多测试一次，请在提示时间后再试。";
          case EXECUTOR_CLOSED -> "请稍后再试，不会进入无限等待队列。";
          case NOT_CONFIGURED -> "请在本机配置并重启后再核验；不临时创建供应商连接。";
          case DEMO -> "请在真实数据模式下测试；模拟样本不能证明真实通道可用。";
          case UNSUPPORTED -> "请改选该通道支持的能力；不支持不等于网络故障。";
          case INTERRUPTED -> "稍后核查通道状态，不重复快速点击。";
        };
    String code =
        switch (reason) {
          case BUSY, EXECUTOR_CLOSED -> "PROBE_BUSY";
          case RATE_LIMITED -> "PROBE_RATE_LIMITED";
          default -> reason.name();
        };
    return result(
        attempt,
        new Outcome(
            "SKIPPED",
            null,
            new FailureDiagnosis(code, title, "本次没有完成上游样本核验。", "不能据此认定通道恢复或取得当日行情。", action)));
  }

  /** 汇总实际耗时与原始查询条件，不添加供应商原文、秘密或堆栈。 */
  private ProviderProbeView result(ProbeAttempt attempt, Outcome outcome) {
    var request = attempt.request();
    return new ProviderProbeView(
        request.getChannel(),
        request.getCapability(),
        request.getDate(),
        outcome.status(),
        attempt.started(),
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - attempt.begin()),
        outcome.sample(),
        outcome.diagnostic(),
        attempt.retryAt());
  }

  /** 取消尚未开始或正在执行的调用；未进入工作线程的取消也必须释放准入状态。 */
  private void cancel(java.util.concurrent.Future<?> future, AtomicBoolean entered) {
    if (future.cancel(true) && !entered.get()) running.set(false);
  }

  /** 停止接收测试并中断尚未退出的调用，不等待无限上游连接。 */
  @PreDestroy
  public void close() {
    executor.shutdownNow();
  }
}

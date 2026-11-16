package com.alphascope.market.provider;

import com.alphascope.common.diagnostic.FailureDiagnosis;
import com.alphascope.common.diagnostic.FailureDiagnostics;
import com.alphascope.common.diagnostic.baostock.BaoStockErrors;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.market.dto.DailyBarDto;
import com.alphascope.market.dto.MarketWindow;
import com.alphascope.market.provider.baostock.BaoStockAccessGuard;
import com.alphascope.market.vo.CapabilityView;
import com.alphascope.market.vo.ProviderChannelView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 整个标的时间窗在一个通道内取得，失败再整批切换，绝不逐日拼接不同口径。
 *
 * @author chenxuefeng
 */
public class MultiChannelMarketDataProvider implements MarketDataProvider {
  private final List<MarketDataProvider> channels;
  private final Clock clock;
  private final Duration cooldown;
  private final Map<String, State> states = new ConcurrentHashMap<>();
  private volatile String calendarSource = "NONE";
  private Map<LocalDate, Boolean> lastCalendar = Map.of();

  /**
   * 单个真实通道的连续失败、冷却截止及最近成功状态。
   *
   * @author chenxuefeng
   */
  private static class State {
    volatile int failures;
    volatile Instant retryAt = Instant.EPOCH;
    volatile Instant success;
    volatile Instant failure;
    volatile String error = "";
    volatile FailureDiagnosis diagnostic;
  }

  public MultiChannelMarketDataProvider(
      List<MarketDataProvider> channels, Duration cooldown, Clock clock) {
    if (channels.isEmpty()
        || channels.stream().anyMatch(MarketDataProvider::synthetic)
        || channels.stream().map(MarketDataProvider::code).distinct().count() != channels.size()) {
      throw new IllegalArgumentException("通道必须非空、真实且唯一");
    }
    this.channels = List.copyOf(channels);
    this.cooldown = cooldown;
    this.clock = clock;
    channels.forEach(p -> states.put(p.code(), new State()));
  }

  @Override
  public boolean acceptsSource(String source) {
    return channels.stream().anyMatch(channel -> channel.acceptsSource(source));
  }

  @Override
  public void close() {
    channels.forEach(MarketDataProvider::close);
  }

  public String code() {
    return "MULTI";
  }

  public String scope() {
    return "REAL:" + String.join(",", channels.stream().map(MarketDataProvider::code).toList());
  }

  public String calendarSource() {
    return calendarSource;
  }

  /** 失败短暂熔断，后续请求直接尝试下一通道；冷却后自动恢复探测。 */
  private synchronized <T> T fetch(
      Function<MarketDataProvider, T> request, MarketCapability capability) {
    return execute(new FetchOperation<>(request, capability, channels, true));
  }

  /**
   * 共用状态、保护与冷却的调用条件；探测不会替换生产日历窗口。
   *
   * @author chenxuefeng
   */
  private record FetchOperation<T>(
      /** 指定能力的实际调用。 */
      Function<MarketDataProvider, T> request,
      /** 独立能力状态键。 */
      MarketCapability capability,
      /** 允许访问的候选；测试时只能有一个。 */
      List<MarketDataProvider> candidates,
      /** 只有业务采集允许更新日历路由及校验窗口。 */
      boolean updateCalendar) {}

  private synchronized <T> T execute(FetchOperation<T> operation) {
    var capability = operation.capability();
    List<String> failures = new ArrayList<>();
    for (var channel : operation.candidates()) {
      ExecutionContext.checkpoint();
      if (!channel.capabilities().contains(capability)) {
        failures.add(channel.code() + ":UNSUPPORTED");
        continue;
      }
      var state = states.computeIfAbsent(channel.code() + ":" + capability, key -> new State());
      states.put(channel.code(), state);
      if (channel.code().equals("BAOSTOCK") && BaoStockAccessGuard.standard().blocked()) {
        failures.add("BAOSTOCK:" + protectedDiagnostic().code());
        continue;
      }
      Instant now = clock.instant();
      if (now.isBefore(state.retryAt)) {
        failures.add(channel.code() + ":COOLDOWN");
        continue;
      }
      try {
        T result = operation.request().apply(channel);
        state.failures = 0;
        state.error = "";
        state.diagnostic = null;
        state.retryAt = Instant.EPOCH;
        state.success = clock.instant();
        if (capability == MarketCapability.TRADING_CALENDAR && operation.updateCalendar()) {
          calendarSource = channel.calendarSource();
          @SuppressWarnings("unchecked")
          Map<LocalDate, Boolean> days = (Map<LocalDate, Boolean>) result;
          lastCalendar = Map.copyOf(days);
        }
        return result;
      } catch (UnsupportedOperationException e) {
        // 能力不支持不是通道故障，不影响该通道的股票与日历请求。
        failures.add(channel.code() + ":UNSUPPORTED");
      } catch (RuntimeException e) {
        ExecutionContext.checkpoint();
        state.failures++;
        state.failure = clock.instant();
        state.diagnostic = FailureDiagnostics.from(e);
        state.error = state.diagnostic.code();
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn(
                "provider_failed channel={} capability={} diagnostic={} failureType={}",
                channel.code(),
                capability,
                state.error,
                e.getClass().getSimpleName());
        state.retryAt = clock.instant().plus(cooldown);
        failures.add(channel.code() + ":" + state.error);
      }
    }
    throw new IllegalStateException("所有真实行情通道不可用（不会回退模拟）：" + String.join(",", failures));
  }

  /** {@inheritDoc} */
  @Override
  public com.alphascope.market.vo.ProviderProbeSample probe(
      com.alphascope.market.dto.ProviderProbeRequest request) {
    var selected =
        channels.stream()
            .filter(channel -> channel.code().equals(request.getChannel()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("通道未配置"));
    return execute(
        new FetchOperation<>(
            channel -> ProviderProbeSamples.fetch(channel, request),
            request.getCapability(),
            List.of(selected),
            false));
  }

  public MarketWindow fetchWindow(String symbol, LocalDate from, LocalDate to, boolean index) {
    return fetch(
        p -> {
          if (index && !p.supportsIndexBars()) {
            throw new UnsupportedOperationException();
          }
          var window = p.fetchWindow(symbol, from, to, index);
          if (window.absences().entrySet().stream()
              .anyMatch(
                  e ->
                      !"SUSPENDED".equals(e.getValue())
                          || e.getKey().isBefore(from)
                          || e.getKey().isAfter(to))) {
            throw new IllegalStateException("INVALID_ABSENCE_EVIDENCE");
          }
          if (window.bars().stream().anyMatch(b -> window.absences().containsKey(b.tradeDate()))) {
            throw new IllegalStateException("CONFLICTING_DAY_STATUS");
          }
          if (!window.bars().isEmpty()) {
            ProviderValidation.bars(window.bars(), symbol, from, to);
          }
          Set<LocalDate> observed = new HashSet<>(window.absences().keySet());
          window.bars().forEach(b -> observed.add(b.tradeDate()));
          if (observed.isEmpty()) {
            throw new IllegalStateException("EMPTY_RESPONSE");
          }
          for (var day : lastCalendar.entrySet()) {
            if (day.getValue()
                && !day.getKey().isBefore(from)
                && !day.getKey().isAfter(to)
                && !observed.contains(day.getKey())) {
              throw new IllegalStateException("INCOMPLETE_HISTORY");
            }
          }
          return window;
        },
        index ? MarketCapability.INDEX_DAILY : MarketCapability.STOCK_DAILY);
  }

  public List<DailyBarDto> fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
    return fetch(
        p ->
            fresh(
                ProviderValidation.bars(p.fetchDailyBars(symbol, from, to), symbol, from, to),
                from,
                to),
        MarketCapability.STOCK_DAILY);
  }

  public List<DailyBarDto> fetchIndexBars(String symbol, LocalDate from, LocalDate to) {
    return fetch(
        p -> {
          if (!p.supportsIndexBars()) {
            throw new UnsupportedOperationException("INDEX_UNSUPPORTED");
          }
          return fresh(
              ProviderValidation.bars(p.fetchIndexBars(symbol, from, to), symbol, from, to),
              from,
              to);
        },
        MarketCapability.INDEX_DAILY);
  }

  public Map<LocalDate, Boolean> fetchCalendar(LocalDate from, LocalDate to) {
    return fetch(
        p -> ProviderValidation.calendar(p.fetchCalendar(from, to), from, to),
        MarketCapability.TRADING_CALENDAR);
  }

  private List<DailyBarDto> fresh(List<DailyBarDto> bars, LocalDate from, LocalDate to) {
    LocalDate expected =
        lastCalendar.entrySet().stream()
            .filter(e -> e.getValue() && !e.getKey().isBefore(from) && !e.getKey().isAfter(to))
            .map(Map.Entry::getKey)
            .max(LocalDate::compareTo)
            .orElse(null);
    if (expected != null && bars.get(bars.size() - 1).tradeDate().isBefore(expected)) {
      throw new IllegalStateException("STALE_DATA");
    }
    return bars;
  }

  private List<CapabilityView> capabilityStates(MarketDataProvider channel) {
    List<CapabilityView> result = new ArrayList<>();
    for (MarketCapability capability :
        List.of(
            MarketCapability.TRADING_CALENDAR,
            MarketCapability.STOCK_DAILY,
            MarketCapability.INDEX_DAILY)) {
      boolean supported = channel.capabilities().contains(capability);
      State state = states.get(channel.code() + ":" + capability);
      boolean restricted =
          channel.code().equals("BAOSTOCK") && BaoStockAccessGuard.standard().blocked();
      String value =
          !supported
              ? "UNSUPPORTED"
              : restricted
                  ? "ACCESS_RESTRICTED"
                  : state == null
                      ? "NOT_CHECKED"
                      : clock.instant().isBefore(state.retryAt)
                          ? "COOLDOWN"
                          : state.failures > 0
                              ? "RETRY_READY"
                              : state.success == null ? "NOT_CHECKED" : "AVAILABLE";
      result.add(
          new CapabilityView(
              capability,
              supported,
              value,
              state == null ? null : state.success,
              !supported
                  ? null
                  : restricted ? protectedDiagnostic().code() : state == null ? null : state.error,
              state == null || state.retryAt.equals(Instant.EPOCH) ? null : state.retryAt,
              state == null ? null : state.failure,
              !supported
                  ? null
                  : restricted ? protectedDiagnostic() : state == null ? null : state.diagnostic));
    }
    return result;
  }

  /** 读取已经保存的拒绝证据，不发送请求；旧记录缺原文保持未记录。 */
  private FailureDiagnosis protectedDiagnostic() {
    var saved = BaoStockAccessGuard.standard().state();
    return saved.code() == null
        ? FailureDiagnostics.code("ACCESS_RESTRICTED")
        : BaoStockErrors.diagnose(saved.code(), saved.errorMessage());
  }

  public List<ProviderChannelView> status() {
    List<ProviderChannelView> rows = new ArrayList<>();
    for (var channel : channels) {
      var state = states.get(channel.code());
      boolean restricted =
          channel.code().equals("BAOSTOCK") && BaoStockAccessGuard.standard().blocked();
      String availability =
          restricted
              ? "ACCESS_RESTRICTED"
              : clock.instant().isBefore(state.retryAt)
                  ? "COOLDOWN"
                  : state.failures > 0
                      ? "RETRY_READY"
                      : state.success == null ? "NOT_CHECKED" : "AVAILABLE";
      rows.add(
          new ProviderChannelView(
              channel.code(),
              channel.supportsIndexBars(),
              availability,
              state.failures,
              state.success,
              restricted ? protectedDiagnostic().code() : state.error,
              restricted || state.retryAt.equals(Instant.EPOCH) ? null : state.retryAt,
              false,
              capabilityStates(channel)));
    }
    return rows;
  }
}

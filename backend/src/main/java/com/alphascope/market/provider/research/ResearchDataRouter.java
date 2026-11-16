package com.alphascope.market.provider.research;

import com.alphascope.common.diagnostic.FailureDiagnosis;
import com.alphascope.common.diagnostic.FailureDiagnostics;
import com.alphascope.common.diagnostic.baostock.BaoStockErrors;
import com.alphascope.common.task.ExecutionContext;
import com.alphascope.common.task.TaskStoppedException;
import com.alphascope.market.dto.ResearchFactorRequest;
import com.alphascope.market.dto.ResearchFactorWindow;
import com.alphascope.market.dto.ResearchMarketSnapshot;
import com.alphascope.market.provider.MarketCapability;
import com.alphascope.market.provider.ProviderDataUnavailableException;
import com.alphascope.market.provider.baostock.BaoStockAccessGuard;
import com.alphascope.market.vo.CapabilityView;
import com.alphascope.operations.service.SourceLicenseService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.MDC;

/**
 * 按研究能力独立选择来源和冷却；原价一旦确定来源，其因子不能单独换源。
 *
 * @author chenxuefeng
 */
public class ResearchDataRouter implements AutoCloseable {
  private final List<ResearchDataProvider> providers;
  private final Duration cooldown;
  private final Clock clock;
  private final Map<String, State> states = new ConcurrentHashMap<>();

  private SourceLicenseService licenses;

  public void setLicenses(SourceLicenseService licenses) {
    this.licenses = licenses;
  }

  public ResearchDataRouter(List<ResearchDataProvider> providers, Duration cooldown, Clock clock) {
    this.providers = List.copyOf(providers);
    this.cooldown = cooldown;
    this.clock = clock;
    if (providers.stream().map(ResearchDataProvider::code).distinct().count() != providers.size()) {
      throw new IllegalArgumentException("研究通道不能重复");
    }
  }

  /**
   * 获取整份同源市场截面，失败时按配置切换整个能力请求。
   *
   * @param date 历史业务日期
   * @param requiredSource 已确定的窗口来源；null表示按优先级选择
   * @return 原始来源明确的整份截面
   */
  public ResearchMarketSnapshot snapshot(LocalDate date, String requiredSource) {
    return route(
        MarketCapability.MARKET_SNAPSHOT, requiredSource, provider -> provider.snapshot(date));
  }

  /**
   * 取得与原价同源的因子，禁止跨源拼接。
   *
   * @param request 实际来源和覆盖日期要求
   * @return 同源因子及原文
   */
  public ResearchFactorWindow factors(ResearchFactorRequest request) {
    return route(
        MarketCapability.ADJUSTMENT_FACTORS,
        request.source(),
        provider -> {
          var value = provider.factors(request);
          if (value == null
              || !provider.code().equals(value.source())
              || !request.source().equals(value.source())) {
            throw new IllegalStateException("FACTOR_SOURCE_MISMATCH");
          }
          return value;
        });
  }

  /**
   * 读取实际来源的能力声明，不探测网络。
   *
   * @param source 已确定的价格来源
   * @param capability 所需能力
   * @return 来源已配置且明确支持时为true
   */
  public boolean supports(String source, MarketCapability capability) {
    return providers.stream()
        .anyMatch(p -> p.code().equals(source) && p.capabilities().contains(capability));
  }

  /**
   * 读取远端历史查询声明；false不妨碍复用本地不可变归档。
   *
   * @param source 实际来源
   * @return 是否提供远端历史截面
   */
  public boolean historicalSnapshots(String source) {
    return providers.stream().anyMatch(p -> p.code().equals(source) && p.historicalSnapshots());
  }

  /**
   * 读取可复用的已配置来源代码，不发起网络请求。
   *
   * @return 按配置顺序排列的来源列表
   */
  public List<String> sources() {
    return providers.stream().map(ResearchDataProvider::code).toList();
  }

  /**
   * 读取指定通道的两项研究能力状态。
   *
   * @param code 实际通道代码
   * @return 每项能力的支持声明、最近结果和冷却状态
   */
  public List<CapabilityView> status(String code) {
    var provider = providers.stream().filter(value -> value.code().equals(code)).findFirst();
    List<CapabilityView> result = new ArrayList<>();
    for (MarketCapability capability :
        List.of(MarketCapability.MARKET_SNAPSHOT, MarketCapability.ADJUSTMENT_FACTORS)) {
      boolean supported =
          provider.isPresent() && provider.get().capabilities().contains(capability);
      State state = states.get(key(code, capability));
      Instant accessUntil = provider.isEmpty() ? null : provider.get().blockedUntil();
      boolean accessCooling = accessUntil != null && clock.instant().isBefore(accessUntil);
      boolean restricted =
          supported && code.equals("BAOSTOCK") && BaoStockAccessGuard.standard().blocked();
      FailureDiagnosis protection = null;
      if (restricted) {
        var saved = BaoStockAccessGuard.standard().state();
        protection =
            saved.code() == null
                ? FailureDiagnostics.code("ACCESS_RESTRICTED")
                : BaoStockErrors.diagnose(saved.code(), saved.errorMessage());
      }
      String value =
          !supported
              ? "UNSUPPORTED"
              : restricted
                  ? "ACCESS_RESTRICTED"
                  : accessCooling
                      ? "ACCESS_COOLDOWN"
                      : state == null
                          ? "NOT_CHECKED"
                          : state.retryAt != null && clock.instant().isBefore(state.retryAt)
                              ? "COOLDOWN"
                              : state.error == null ? "AVAILABLE" : "UNAVAILABLE";
      result.add(
          new CapabilityView(
              capability,
              supported,
              value,
              state == null ? null : state.success,
              !supported
                  ? null
                  : restricted
                      ? protection.code()
                      : accessCooling
                          ? "PUBLIC_MARKET_ACCESS_COOLDOWN"
                          : state == null ? null : state.error,
              accessCooling ? accessUntil : state == null ? null : state.retryAt,
              state == null ? null : state.failure,
              !supported
                  ? null
                  : restricted ? protection : state == null ? null : state.diagnostic));
    }
    return result;
  }

  private synchronized <T> T route(
      MarketCapability capability,
      String requiredSource,
      Function<ResearchDataProvider, T> action) {
    List<String> failures = new ArrayList<>();
    for (ResearchDataProvider provider : providers) {
      ExecutionContext.checkpoint();
      if (requiredSource != null && !requiredSource.equals(provider.code())) {
        continue;
      }
      if (!provider.capabilities().contains(capability)) {
        continue;
      }
      if (provider.code().equals("BAOSTOCK") && BaoStockAccessGuard.standard().blocked()) {
        var saved = BaoStockAccessGuard.standard().state();
        failures.add(
            "BAOSTOCK:"
                + (saved.code() == null
                    ? "ACCESS_RESTRICTED"
                    : "BAOSTOCK_API_ERROR_" + saved.code()));
        continue;
      }
      State state = states.computeIfAbsent(key(provider.code(), capability), key -> new State());
      if (provider.blockedUntil() != null && clock.instant().isBefore(provider.blockedUntil())) {
        failures.add(provider.code() + ":ACCESS_COOLDOWN");
        continue;
      }
      if (state.retryAt != null && clock.instant().isBefore(state.retryAt)) {
        failures.add(provider.code() + ":COOLDOWN");
        continue;
      }
      String previous = MDC.get("provider");
      MDC.put("provider", provider.code());
      try {
        if (licenses != null)
          for (String source : provider.requiredSources()) licenses.require(source, "COLLECT");
        T result = action.apply(provider);
        ExecutionContext.checkpoint();
        state.success = clock.instant();
        state.error = null;
        state.diagnostic = null;
        state.retryAt = null;
        return result;
      } catch (TaskStoppedException e) {
        throw e;
      } catch (ProviderDataUnavailableException e) {
        state.failure = clock.instant();
        state.diagnostic = FailureDiagnostics.from(e);
        state.error = state.diagnostic.code();
        failures.add(provider.code() + ":" + state.error);
      } catch (RuntimeException e) {
        ExecutionContext.checkpoint();
        state.failure = clock.instant();
        state.diagnostic = FailureDiagnostics.from(e);
        state.error = state.diagnostic.code();
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn(
                "research_provider_failed channel={} capability={} diagnostic={} failureType={}",
                provider.code(),
                capability,
                state.error,
                e.getClass().getSimpleName());
        state.retryAt = clock.instant().plus(cooldown);
        failures.add(provider.code() + ":" + state.error);
      } finally {
        if (previous == null) {
          MDC.remove("provider");
        } else {
          MDC.put("provider", previous);
        }
      }
    }
    throw new IllegalStateException(
        "真实数据能力不可用（"
            + capability
            + "；不回退模拟）："
            + (failures.isEmpty() ? "NO_CONFIGURED_CAPABLE_SOURCE" : String.join(",", failures)));
  }

  private String key(String code, MarketCapability capability) {
    return code + ":" + capability;
  }

  /** 释放所有研究能力会话。 */
  @Override
  public void close() {
    providers.forEach(ResearchDataProvider::close);
  }

  /**
   * 独立能力的进程内观测状态，不代替持久访问限制。
   *
   * @author chenxuefeng
   */
  private static class State {
    /** 最近成功时间。 */
    volatile Instant success;

    /** 最近实际请求失败的时间；不会因查询或冷却而更新。 */
    volatile Instant failure;

    /** 当前失败代码。 */
    volatile String error;

    /** 当次受控诊断，保留已确认供应商数字码及安全原文。 */
    volatile FailureDiagnosis diagnostic;

    /** 失败后的冷却结束时间。 */
    volatile Instant retryAt;
  }
}

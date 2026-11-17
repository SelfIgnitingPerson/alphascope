package com.alphascope.portfolio.controller;

import com.alphascope.common.response.Result;
import com.alphascope.market.service.InstrumentDiscoveryService;
import com.alphascope.market.vo.InstrumentDiscoveryView;
import com.alphascope.operations.policy.OperationalData;
import com.alphascope.portfolio.dto.WatchDiscoveryInput;
import com.alphascope.portfolio.service.WatchOnboardingService;
import com.alphascope.portfolio.vo.WatchDiscoveryResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户证券搜索与首次研究入口，未登记证券不再需要直接操作SQL。
 *
 * @author chenxuefeng
 */
@OperationalData
@RestController
public class WatchOnboardingController {
  private final InstrumentDiscoveryService discovery;
  private final WatchOnboardingService onboarding;

  public WatchOnboardingController(
      InstrumentDiscoveryService discovery, WatchOnboardingService onboarding) {
    this.discovery = discovery;
    this.onboarding = onboarding;
  }

  /**
   * 搜索可核验的A股候选。
   *
   * @param q 代码或名称
   * @return 本地及远端结果与状态
   */
  @GetMapping("/api/v1/instruments/discover")
  public Result<InstrumentDiscoveryView> search(@RequestParam String q) {
    return Result.success(discovery.search(q));
  }

  /**
   * 添加自选并按用户选择初始化。
   *
   * @param input 证券与初始化选择
   * @return 添加及排队结果
   */
  @PostMapping("/api/v1/watchlist/discover")
  public Result<WatchDiscoveryResult> add(@Valid @RequestBody WatchDiscoveryInput input) {
    return Result.success(onboarding.add(input));
  }
}

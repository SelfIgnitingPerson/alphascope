package com.alphascope.market.controller;

import com.alphascope.common.response.Result;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.vo.BaoStockPolicyView;
import com.alphascope.market.vo.ProviderStatusView;
import com.alphascope.operations.policy.OperationalData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询当前通道状态和已有数据来源；GET不触发付费请求或真实回补。
 *
 * @author chenxuefeng
 */
@OperationalData
@RestController
@RequestMapping("/api/v1/market")
public class ProviderController {
  private final MarketService market;

  public ProviderController(MarketService market) {
    this.market = market;
  }

  /** 只读取状态，不在GET中触发收费请求或真实回填。 */
  @GetMapping("/providers")
  public Result<ProviderStatusView> providers() {
    return Result.success(market.providerStatus());
  }

  /**
   * 读取免费节点的本机预算和持久访问保护，不测试连接、不解除限制。
   *
   * @return 本机预算、保护状态及核查提示；损坏状态明确显示异常，不填零
   */
  @GetMapping("/providers/baostock-policy")
  public Result<BaoStockPolicyView> baoStockPolicy() {
    return Result.success(market.baoStockPolicy());
  }
}

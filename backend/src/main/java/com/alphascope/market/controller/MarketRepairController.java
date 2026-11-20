package com.alphascope.market.controller;

import com.alphascope.common.response.Result;
import com.alphascope.market.dto.MarketRepairInput;
import com.alphascope.market.service.FullMarketService;
import com.alphascope.market.vo.MarketRepairView;
import com.alphascope.operations.policy.OperationalData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员显式核验本机档案，不能用当前市场数据替代过去。
 *
 * @author chenxuefeng
 */
@RestController
@OperationalData
public class MarketRepairController {
  private final FullMarketService service;

  public MarketRepairController(FullMarketService service) {
    this.service = service;
  }

  /**
   * 分批核验已存真实档案，不发起网络采集。
   *
   * @param input 最多10个待核验日期
   * @return 每日修复及未完成原因
   */
  @PostMapping("/api/v1/admin/market/repair-stored")
  public Result<MarketRepairView> repair(@Valid @RequestBody MarketRepairInput input) {
    return Result.success(service.repairStored(input));
  }
}

package com.alphascope.market.controller;

import com.alphascope.common.response.Result;
import com.alphascope.market.dto.InstrumentInput;
import com.alphascope.market.dto.LifecycleInput;
import com.alphascope.market.service.InstrumentAdminService;
import com.alphascope.market.service.InstrumentLifecycleService;
import com.alphascope.operations.policy.OperationalData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登记证券或指数主数据，校验代码后缀与交易所一致。
 *
 * @author chenxuefeng
 */
@OperationalData
@RestController
public class InstrumentAdminController {
  private final InstrumentAdminService service;

  public InstrumentAdminController(InstrumentAdminService service) {
    this.service = service;
  }

  @org.springframework.beans.factory.annotation.Autowired
  private InstrumentLifecycleService lifecycle;

  /**
   * 依据公开公告补录生命周期。
   *
   * @param symbol 已登记证券
   * @param input 已核验日期及依据
   * @return 保存成功
   */
  @org.springframework.web.bind.annotation.PutMapping(
      "/api/v1/admin/instruments/{symbol}/lifecycle")
  public com.alphascope.common.response.Result<Void> lifecycle(
      @org.springframework.web.bind.annotation.PathVariable String symbol,
      @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
          LifecycleInput input) {
    lifecycle.declare(symbol, input);
    return com.alphascope.common.response.Result.success();
  }

  /**
   * 登记新的证券或指数主数据。
   *
   * @param input 统一代码、名称、交易所、类型和可空上市日期
   * @return 保存成功的统一空响应
   */
  @PostMapping("/api/v1/admin/instruments")
  public Result<Void> create(@Valid @RequestBody InstrumentInput input) {
    service.create(input);
    return Result.success();
  }
}

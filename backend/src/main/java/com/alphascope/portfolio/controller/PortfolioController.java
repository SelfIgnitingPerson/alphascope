package com.alphascope.portfolio.controller;

import com.alphascope.common.response.Result;
import com.alphascope.operations.policy.OperationalData;
import com.alphascope.portfolio.dto.AccountInput;
import com.alphascope.portfolio.dto.PositionInput;
import com.alphascope.portfolio.service.PortfolioService;
import com.alphascope.portfolio.vo.PositionsView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登记人工持仓与资产总额；不接券商交易或自动下单。
 *
 * @author chenxuefeng
 */
@OperationalData
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {
  private final PortfolioService service;

  public PortfolioController(PortfolioService service) {
    this.service = service;
  }

  /**
   * 读取当前人工持仓及资产总额，不访问券商账户。
   *
   * @return 持仓列表、账户总额及单位
   */
  @GetMapping("/positions")
  public Result<PositionsView> positions(
      @org.springframework.web.bind.annotation.RequestParam(required = false)
          java.time.LocalDate asOf) {
    return Result.success(asOf == null ? service.positionsView() : service.positionsView(asOf));
  }

  /**
   * 保存人工持仓的数量、成本和业务日期。
   *
   * @param symbol URL路径中的统一证券代码
   * @param input 人工持仓字段，数量为股、成本为人民币元
   * @return 保存成功的统一空响应
   */
  @PutMapping("/positions/{symbol}")
  public Result<Void> position(
      @PathVariable String symbol, @Valid @RequestBody PositionInput input) {
    service.position(symbol, input);
    return Result.success();
  }

  /**
   * 保存人工账户总资产及对应业务日期。
   *
   * @param input 人民币资产总额及业务日期，未知总额可为空
   * @return 保存成功的统一空响应
   */
  @PutMapping("/account")
  public Result<Void> account(@Valid @RequestBody AccountInput input) {
    service.account(input);
    return Result.success();
  }
}

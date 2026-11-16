package com.alphascope.fundamental.controller;

import com.alphascope.common.response.Result;
import com.alphascope.fundamental.service.FundamentalService;
import com.alphascope.fundamental.vo.FinancialView;
import com.alphascope.fundamental.vo.FinancialsView;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 按业务截止时点读取已披露财报，避免用当前修订倒填旧报告。
 *
 * @author chenxuefeng
 */
@RestController
public class FundamentalController {
  private final FundamentalService service;

  public FundamentalController(FundamentalService service) {
    this.service = service;
  }

  /**
   * 读取截止业务日期已可用的累计财报，不倒填后来取得的修订。
   *
   * @param symbol URL路径中的统一证券代码
   * @param asOf 可选截止日期，省略时采用上海当前日期
   * @return 统一响应中的累计财报列表及金额单位
   */
  @GetMapping("/api/v1/instruments/{symbol}/financials")
  public Result<FinancialsView> reports(
      @PathVariable String symbol, @RequestParam(required = false) LocalDate asOf) {
    return Result.success(
        new FinancialsView(
            service
                .reports(symbol, asOf == null ? LocalDate.now(ZoneId.of("Asia/Shanghai")) : asOf)
                .stream()
                .map(FinancialView::from)
                .toList(),
            "CNY",
            "DECIMAL"));
  }
}

package com.alphascope.intelligence.controller;

import com.alphascope.common.response.Result;
import com.alphascope.fundamental.dto.EvidenceQuery;
import com.alphascope.intelligence.service.BusinessEvidenceService;
import com.alphascope.intelligence.service.ValuationAuditService;
import com.alphascope.intelligence.vo.BusinessEvidenceView;
import com.alphascope.intelligence.vo.ValuationAuditView;
import com.alphascope.operations.policy.ResearchData;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 经营与估值证据入口，继续执行实际来源许可校验。
 *
 * @author chenxuefeng
 */
@ResearchData
@RestController
public class BusinessEvidenceController {
  @Autowired private ValuationAuditService valuation;

  /**
   * 独立核对实际股本、滚动盈利及同行价格，不接受客户端PE值。
   *
   * @param symbol 标的
   * @param asOf 截止日
   * @return 逐项证据检查
   */
  @GetMapping("/api/v1/instruments/{symbol}/valuation-audit")
  public Result<ValuationAuditView> valuation(
      @PathVariable String symbol, @RequestParam LocalDate asOf) {
    return Result.success(valuation.read(new EvidenceQuery(symbol, asOf)));
  }

  private final BusinessEvidenceService service;

  public BusinessEvidenceController(BusinessEvidenceService service) {
    this.service = service;
  }

  /**
   * 读取有来源的经营与估值核对。
   *
   * @param symbol 证券代码
   * @param asOf 截止日期
   * @return 证据与缺项
   */
  @GetMapping("/api/v1/instruments/{symbol}/business-evidence")
  public Result<BusinessEvidenceView> read(
      @PathVariable String symbol, @RequestParam LocalDate asOf) {
    return Result.success(service.read(new EvidenceQuery(symbol, asOf)));
  }
}

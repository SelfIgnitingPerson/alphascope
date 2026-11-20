package com.alphascope.fundamental.controller;

import com.alphascope.common.response.Result;
import com.alphascope.fundamental.dto.CorporateActionInput;
import com.alphascope.fundamental.dto.EvidenceQuery;
import com.alphascope.fundamental.dto.ValuationThesisInput;
import com.alphascope.fundamental.service.InvestmentEvidenceService;
import com.alphascope.operations.policy.ResearchData;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 私人估值假设和管理员公开行动登记入口，身份与来源权限沿用现有过滤器。
 *
 * @author chenxuefeng
 */
@ResearchData
@RestController
@RequestMapping("/api/v1")
public class InvestmentEvidenceController {
  private final InvestmentEvidenceService service;

  public InvestmentEvidenceController(InvestmentEvidenceService service) {
    this.service = service;
  }

  /** 读取个人长期情景；@param symbol 证券代码 @param asOf 截止日 @return 情景或空 */
  @GetMapping("/stocks/{symbol}/valuation-thesis")
  public Result<ValuationThesisInput> thesis(
      @PathVariable String symbol, @RequestParam LocalDate asOf) {
    return Result.success(service.thesis(new EvidenceQuery(symbol, asOf)));
  }

  /**
   * 查询研究日截止前已经留档的本人估值情景。
   *
   * @param symbol 证券代码
   * @param asOf 研究日期
   * @return 严格按登记时间筛选的情景或空
   */
  @GetMapping("/stocks/{symbol}/valuation-thesis/at-close")
  public Result<ValuationThesisInput> thesisAtClose(
      @PathVariable String symbol, @RequestParam LocalDate asOf) {
    return Result.success(service.thesisAtClose(new EvidenceQuery(symbol, asOf)));
  }

  /** 保存私人估值假设；@param input 明确来源和失效条件 @return 成功响应 */
  @PostMapping("/valuation-theses")
  public Result<Void> thesis(@Valid @RequestBody ValuationThesisInput input) {
    service.saveThesis(input);
    return Result.success(null);
  }

  /** 读取公告行动；@param symbol 证券代码 @param asOf 截止日 @return 已登记行动 */
  @ResearchData
  @GetMapping("/stocks/{symbol}/corporate-actions")
  public Result<List<CorporateActionInput>> actions(
      @PathVariable String symbol, @RequestParam LocalDate asOf) {
    return Result.success(service.actions(new EvidenceQuery(symbol, asOf)));
  }

  /** 管理员登记公开行动；@param input 公告和价格源因子 @return 成功响应 */
  @PostMapping("/admin/corporate-actions")
  public Result<Void> action(@Valid @RequestBody CorporateActionInput input) {
    service.saveAction(input);
    return Result.success(null);
  }
}

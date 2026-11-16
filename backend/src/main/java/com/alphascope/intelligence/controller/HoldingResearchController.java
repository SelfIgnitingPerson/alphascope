package com.alphascope.intelligence.controller;

import com.alphascope.common.response.Result;
import com.alphascope.intelligence.dto.HoldingResearchQuery;
import com.alphascope.intelligence.service.HoldingResearchService;
import com.alphascope.intelligence.vo.HoldingResearchView;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 已保存证据的个股行动研究，统一返回Result，不提供交易或写入接口。
 *
 * @author chenxuefeng
 */
@RestController
public class HoldingResearchController {
  private final HoldingResearchService service;

  public HoldingResearchController(HoldingResearchService service) {
    this.service = service;
  }

  /**
   * 读取关注股票的短、中、长期持有与买卖条件。
   *
   * @param symbol 统一证券代码
   * @param query 必填截止日期
   * @return 同一日期、同一方法下的条件式研究
   */
  @GetMapping("/api/v1/instruments/{symbol}/holding-research")
  public Result<HoldingResearchView> read(
      @PathVariable String symbol,
      @Valid @ModelAttribute @ParameterObject HoldingResearchQuery query) {
    return Result.success(service.read(symbol, query));
  }
}

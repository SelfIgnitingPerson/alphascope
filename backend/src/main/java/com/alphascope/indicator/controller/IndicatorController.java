package com.alphascope.indicator.controller;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.response.Result;
import com.alphascope.indicator.service.IndicatorService;
import com.alphascope.indicator.vo.IndicatorView;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询标的已计算的技术指标；不在GET请求中重复运行研究流水线。
 *
 * @author chenxuefeng
 */
@RestController
public class IndicatorController {
  private final IndicatorService service;

  public IndicatorController(IndicatorService service) {
    this.service = service;
  }

  /**
   * 查询证券的已保存指标，日期通过查询对象绑定并校验。
   *
   * @param symbol URL 路径中的统一证券代码
   * @param request 必填的 start、end 日期闭区间
   * @return 统一响应中的历史指标列表
   */
  @GetMapping("/api/v1/instruments/{symbol}/indicators")
  public Result<List<IndicatorView>> list(
      @PathVariable String symbol,
      @Valid @ModelAttribute @ParameterObject DateRangeRequest request) {
    return Result.success(service.list(symbol, request).stream().map(IndicatorView::from).toList());
  }
}

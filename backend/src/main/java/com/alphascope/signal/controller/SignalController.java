package com.alphascope.signal.controller;

import com.alphascope.common.dto.OptionalDateRangeRequest;
import com.alphascope.common.response.Result;
import com.alphascope.operations.policy.ResearchData;
import com.alphascope.signal.service.SignalService;
import com.alphascope.signal.vo.SignalView;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 只读查询已保存的规则评估及证据，不将结果作为交易指令。
 *
 * @author chenxuefeng
 */
@ResearchData
@RestController
public class SignalController {
  private final SignalService service;

  public SignalController(SignalService service) {
    this.service = service;
  }

  /**
   * 查询已保存规则评估，由业务服务统一确定缺省日期区间。
   *
   * @param symbol URL 路径中的统一证券代码
   * @param request 可选 start、end 日期；省略时查询最近一个月
   * @return 统一响应中的规则评估及证据列表
   */
  @GetMapping("/api/v1/instruments/{symbol}/signals")
  public Result<List<SignalView>> list(
      @PathVariable String symbol,
      @Valid @ModelAttribute @ParameterObject OptionalDateRangeRequest request) {
    return Result.success(service.list(symbol, request).stream().map(SignalView::from).toList());
  }
}

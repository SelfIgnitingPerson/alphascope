package com.alphascope.intelligence.controller;

import com.alphascope.common.response.Result;
import com.alphascope.intelligence.dto.ResearchFocusQuery;
import com.alphascope.intelligence.service.ResearchFocusService;
import com.alphascope.intelligence.vo.ResearchFocusView;
import com.alphascope.operations.policy.ResearchData;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自选研究重点入口；身份由会话确定，不接受用户主键。
 *
 * @author chenxuefeng
 */
@ResearchData
@RestController
public class ResearchFocusController {
  private final ResearchFocusService service;

  public ResearchFocusController(ResearchFocusService service) {
    this.service = service;
  }

  /**
   * 只读查看本页自选在所选日期最需要处理的研究事项。
   *
   * @param query 日期与分页
   * @return 有覆盖范围说明的优先事项
   */
  @GetMapping("/api/v1/research-focus")
  public Result<ResearchFocusView> read(
      @Valid @ModelAttribute @ParameterObject ResearchFocusQuery query) {
    return Result.success(service.read(query));
  }
}

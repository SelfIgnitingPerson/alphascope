package com.alphascope.industry.controller;

import com.alphascope.common.dto.OptionalDateRangeRequest;
import com.alphascope.common.response.Result;
import com.alphascope.industry.service.IndustryService;
import com.alphascope.industry.vo.ThemeStrengthView;
import com.alphascope.industry.vo.ThemeView;
import com.alphascope.operations.policy.ResearchData;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询主题列表、历史有效成员、主题强弱及产业证据。
 *
 * @author chenxuefeng
 */
@ResearchData
@RestController
@RequestMapping("/api/v1/themes")
public class IndustryController {
  private final IndustryService service;

  public IndustryController(IndustryService service) {
    this.service = service;
  }

  /**
   * 读取当前主题字典，供研究范围选择使用。
   *
   * @return 统一响应中的主题列表；未配置时为空
   */
  @GetMapping
  public Result<List<ThemeView>> list() {
    return Result.success(service.themes().stream().map(ThemeView::from).toList());
  }

  /**
   * 读取主题强弱及覆盖证据，由业务服务统一确定缺省日期区间。
   *
   * @param code URL 路径中的主题代码
   * @param request 可选 start、end 日期；省略时查询最近三个月
   * @return 统一响应中的主题强弱与样本范围
   */
  @GetMapping("/{code}/strength")
  public Result<ThemeStrengthView> strength(
      @PathVariable String code,
      @Valid @ModelAttribute @ParameterObject OptionalDateRangeRequest request) {
    return Result.success(service.strength(code, request));
  }
}

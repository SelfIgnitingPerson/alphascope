package com.alphascope.planning.controller;

import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.response.PageResponse;
import com.alphascope.common.response.Result;
import com.alphascope.operations.policy.ResearchData;
import com.alphascope.planning.dto.PlanCreateInput;
import com.alphascope.planning.dto.PlanQuery;
import com.alphascope.planning.dto.PlanReviewInput;
import com.alphascope.planning.service.ResearchPlanService;
import com.alphascope.planning.vo.PlanReviewView;
import com.alphascope.planning.vo.PlanSummary;
import com.alphascope.planning.vo.PlanView;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人计划和不可变复核入口，身份来自会话，不接受租户参数。
 *
 * @author chenxuefeng
 */
@ResearchData
@RestController
@RequestMapping("/api/v1/plans")
public class ResearchPlanController {
  private final ResearchPlanService service;

  @org.springframework.beans.factory.annotation.Autowired
  private com.alphascope.planning.service.PlanFollowupService followups;

  public ResearchPlanController(ResearchPlanService service) {
    this.service = service;
  }

  /**
   * 保存当前研究计划。
   *
   * @param input 研究条件
   * @return 已保存计划
   */
  @PostMapping
  public Result<PlanView> create(@Valid @RequestBody PlanCreateInput input) {
    return Result.success(service.create(input));
  }

  /**
   * 列出个人计划。
   *
   * @param query 筛选与分页
   * @return 摘要分页
   */
  @GetMapping
  public Result<PageResponse<PlanSummary>> list(
      @Valid @ModelAttribute @ParameterObject PlanQuery query) {
    return Result.success(service.page(query));
  }

  /**
   * 读取当前用户计划详情，其他用户主键视为不存在。
   *
   * @param id 计划主键
   * @return 完整冻结详情
   */
  @GetMapping("/{id}")
  public Result<PlanView> get(@PathVariable long id) {
    return Result.success(service.get(id));
  }

  /**
   * 显式追加复核。
   *
   * @param id 个人计划主键
   * @param input 复核日期
   * @return 追加结果
   */
  @PostMapping("/{id}/reviews")
  public Result<PlanReviewView> review(
      @PathVariable long id, @Valid @RequestBody PlanReviewInput input) {
    return Result.success(service.review(id, input));
  }

  /**
   * 读取既有复核。
   *
   * @param id 个人计划主键
   * @param page 分页参数
   * @return 复核分页
   */
  @GetMapping("/{id}/reviews")
  public Result<PageResponse<PlanReviewView>> reviews(
      @PathVariable long id, @Valid @ModelAttribute @ParameterObject PageRequest page) {
    return Result.success(service.reviews(id, page));
  }

  /**
   * 确认已阅读所选日期前的最近复核，原证据保持不变。
   *
   * @param id 当前用户计划
   * @param input 明确的研究日期
   * @return 统一成功响应
   */
  @PostMapping("/{id}/acknowledge")
  public Result<Void> acknowledge(
      @PathVariable long id, @Valid @RequestBody PlanReviewInput input) {
    service.acknowledge(id, input);
    return Result.success(null);
  }

  /**
   * 读取当前账号全部跟踪计划的优先待办，刷新不追加证据。
   *
   * @param asOf 截止业务日
   * @return 全局待办汇总
   */
  @GetMapping("/todos")
  public Result<com.alphascope.planning.vo.PlanTodoView> todos(
      @org.springframework.web.bind.annotation.RequestParam java.time.LocalDate asOf) {
    return Result.success(followups.todos(asOf));
  }

  /**
   * 批量复核跟踪中计划，单项失败不会遮住其他计划。
   *
   * @param input 明确的收盘研究日
   * @return 完成数量与待处理原因
   */
  @PostMapping("/review-due")
  public Result<com.alphascope.planning.vo.PlanFollowupView> followup(
      @Valid @RequestBody PlanReviewInput input) {
    return Result.success(followups.review(input.date()));
  }

  /**
   * 归档并保留证据。
   *
   * @param id 个人计划主键
   * @return 统一成功响应
   */
  @PostMapping("/{id}/archive")
  public Result<Void> archive(@PathVariable long id) {
    service.archive(id);
    return Result.success(null);
  }
}

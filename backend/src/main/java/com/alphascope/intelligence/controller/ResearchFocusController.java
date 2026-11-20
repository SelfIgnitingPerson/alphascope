package com.alphascope.intelligence.controller;

import com.alphascope.common.response.Result;
import com.alphascope.intelligence.dto.ForwardQuery;
import com.alphascope.intelligence.dto.ResearchFocusQuery;
import com.alphascope.intelligence.service.ForwardValidationService;
import com.alphascope.intelligence.service.ResearchFocusService;
import com.alphascope.intelligence.service.ResearchObservationService;
import com.alphascope.intelligence.vo.ForwardEvidenceView;
import com.alphascope.intelligence.vo.ForwardValidationView;
import com.alphascope.intelligence.vo.ObservationCaptureView;
import com.alphascope.intelligence.vo.ResearchFocusView;
import com.alphascope.operations.policy.OperationalData;
import com.alphascope.operations.policy.ResearchData;
import com.alphascope.planning.dto.PlanReviewInput;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自选研究重点入口；身份由会话确定，不接受用户主键。
 *
 * @author chenxuefeng
 */
@ResearchData
@RestController
public class ResearchFocusController {
  @Autowired private ForwardValidationService forward;

  /**
   * 读取服务端全量范围累计的实际前瞻结果，页面刷新不评估或写入。
   *
   * @param query 截止日与本人计划范围
   * @return 有版本与排除项的统计
   */
  @GetMapping("/api/v1/research-focus/forward")
  public Result<ForwardValidationView> forward(
      @Valid @ModelAttribute @ParameterObject ForwardQuery query) {
    return Result.success(forward.summary(query));
  }

  /**
   * 显式核验已成熟的真实前瞻窗口，不向外部交易系统提交操作。
   *
   * @param input 截止日
   * @return 执行数量及缺项
   */
  @PostMapping("/api/v1/research-focus/forward/evaluate")
  @OperationalData
  public Result<ObservationCaptureView> evaluate(@Valid @RequestBody PlanReviewInput input) {
    return Result.success(forward.evaluate(input.date()));
  }

  /**
   * 读取本人已冻结窗口的完整可复算证据。
   *
   * @param id 窗口主键
   * @return 完整输入和原结果
   */
  @GetMapping("/api/v1/research-focus/forward/{id}")
  public Result<ForwardEvidenceView> evidence(@PathVariable long id) {
    return Result.success(forward.evidence(id));
  }

  private final ResearchFocusService service;

  @Autowired private ResearchObservationService journal;

  /**
   * 显式保存当日判断，历史研究只形成回看记录。
   *
   * @param input 研究业务日
   * @return 留档执行结果，不返回模拟交易成绩
   */
  @PostMapping("/api/v1/research-focus/capture")
  @OperationalData
  public Result<ObservationCaptureView> capture(@Valid @RequestBody PlanReviewInput input) {
    return Result.success(journal.capture(input.date()));
  }

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

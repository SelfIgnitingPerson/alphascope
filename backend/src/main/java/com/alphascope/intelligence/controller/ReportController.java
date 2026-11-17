package com.alphascope.intelligence.controller;

import com.alphascope.common.response.PageResponse;
import com.alphascope.common.response.Result;
import com.alphascope.intelligence.dto.ReportSearchRequest;
import com.alphascope.intelligence.service.ReportService;
import com.alphascope.intelligence.vo.ReportComparison;
import com.alphascope.intelligence.vo.ReportDetail;
import com.alphascope.intelligence.vo.ReportSummary;
import com.alphascope.operations.policy.OperationalData;
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
 * 查询冻结报告、修订归档与差异；读取旧报告不触发重新生成。
 *
 * @author chenxuefeng
 */
@ResearchData
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
  private final ReportService service;

  public ReportController(ReportService service) {
    this.service = service;
  }

  /**
   * 按请求对象查询报告归档，默认日期和分页由业务服务统一处理。
   *
   * @param request 报告类型、日期区间、分页及最新修订筛选条件
   * @return 统一响应中的报告分页结果
   */
  @OperationalData
  @GetMapping
  public Result<PageResponse<ReportSummary>> list(
      @Valid @ModelAttribute @ParameterObject ReportSearchRequest request) {
    return Result.success(service.list(request));
  }

  /**
   * 读取不可变报告修订及其冻结分析。
   *
   * @param id 报告记录主键
   * @return 报告正文、结构化分析和原始证据
   */
  @GetMapping("/{id}")
  public Result<ReportDetail> get(@PathVariable long id) {
    return Result.success(service.get(id));
  }

  /**
   * 读取同类型、同业务日期和同数据模式的报告修订。
   *
   * @param id 用于定位修订组的报告主键
   * @return 可比较的修订列表
   */
  @GetMapping("/{id}/versions")
  public Result<List<ReportSummary>> versions(@PathVariable long id) {
    return Result.success(service.versions(id));
  }

  /**
   * 比较两份可比报告的正文与输入，不重跑研究。
   *
   * @param id 当前报告主键
   * @param otherId 待比较报告主键
   * @return 正文及冻结输入是否发生变化的比较结果
   */
  @GetMapping("/{id}/compare/{otherId}")
  public Result<ReportComparison> compare(@PathVariable long id, @PathVariable long otherId) {
    return Result.success(service.compare(id, otherId));
  }
}

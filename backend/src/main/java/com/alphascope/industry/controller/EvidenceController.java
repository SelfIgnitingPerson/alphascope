package com.alphascope.industry.controller;

import com.alphascope.common.response.Result;
import com.alphascope.common.vo.ResourceIdView;
import com.alphascope.industry.dto.MetricInput;
import com.alphascope.industry.dto.SourceInput;
import com.alphascope.industry.service.EvidenceService;
import com.alphascope.industry.vo.IndustryMetricView;
import com.alphascope.operations.policy.OperationalData;
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
 * 登记可核验公开来源与产业指标，所有写入由证据服务处理。
 *
 * @author chenxuefeng
 */
@OperationalData
@RestController
@RequestMapping("/api/v1")
public class EvidenceController {
  private final EvidenceService service;

  public EvidenceController(EvidenceService service) {
    this.service = service;
  }

  /**
   * 保存公开来源材料和内容指纹。
   *
   * @param input 来源类型、原文链接、标题、披露时刻及正文
   * @return 新建来源记录的主键
   */
  @PostMapping("/admin/sources")
  public Result<ResourceIdView> source(@Valid @RequestBody SourceInput input) {
    return Result.success(new ResourceIdView(service.source(input)));
  }

  /**
   * 登记具有公开来源和计量单位的产业指标。
   *
   * @param input 主题、指标、单位、业务日期及已登记来源
   * @return 保存成功的统一空响应
   */
  @PostMapping("/admin/industry-metrics")
  public Result<Void> metric(@Valid @RequestBody MetricInput input) {
    service.metric(input);
    return Result.success();
  }

  /**
   * 读取业务时点可见的产业指标和公开文档信息。
   *
   * @param code URL路径中的主题代码
   * @param asOf 查询截止业务日期
   * @return 可见指标列表；无记录时为空
   */
  @GetMapping("/themes/{code}/metrics")
  public Result<List<IndustryMetricView>> metrics(
      @PathVariable String code, @RequestParam LocalDate asOf) {
    return Result.success(
        service.metrics(code, asOf).stream().map(IndustryMetricView::from).toList());
  }
}

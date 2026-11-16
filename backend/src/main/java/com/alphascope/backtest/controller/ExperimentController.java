package com.alphascope.backtest.controller;

import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.service.ExperimentService;
import com.alphascope.backtest.vo.ExperimentReplayView;
import com.alphascope.backtest.vo.ExperimentView;
import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.response.PageResponse;
import com.alphascope.common.response.Result;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 不可覆盖实验的显式保存、读取和冻结输入复算接口。
 *
 * @author chenxuefeng
 */
@RestController
public class ExperimentController {
  private final ExperimentService service;

  public ExperimentController(ExperimentService service) {
    this.service = service;
  }

  /**
   * 保存一份独立实验。
   *
   * @param request 完整研究条件
   * @return 新实验及冻结结果
   */
  @PostMapping("/api/v1/admin/backtest-experiments")
  public Result<ExperimentView> save(@Valid @RequestBody SignalStatisticsRequest request) {
    return Result.success(service.save(request));
  }

  /**
   * 读取实验元数据分页。
   *
   * @param request 分页条件
   * @return 元数据分页
   */
  @GetMapping("/api/v1/backtest/experiments")
  public Result<PageResponse<ExperimentView>> page(
      @Valid @ModelAttribute @ParameterObject PageRequest request) {
    return Result.success(service.page(request));
  }

  /**
   * 读取冻结结果。
   *
   * @param id 实验主键
   * @return 实验详情
   */
  @GetMapping("/api/v1/backtest/experiments/{id}")
  public Result<ExperimentView> get(@PathVariable long id) {
    return Result.success(service.get(id));
  }

  /**
   * 使用冻结输入复算，不写入或更新实验。
   *
   * @param id 实验主键
   * @return 完整性及结果对比
   */
  @PostMapping("/api/v1/admin/backtest-experiments/{id}/reproduce")
  public Result<ExperimentReplayView> reproduce(@PathVariable long id) {
    return Result.success(service.reproduce(id));
  }
}

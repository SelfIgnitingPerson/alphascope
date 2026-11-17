package com.alphascope.backtest.controller;

import com.alphascope.backtest.dto.ResearchValidationRequest;
import com.alphascope.backtest.service.ValidationTaskService;
import com.alphascope.backtest.vo.ValidationTaskView;
import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.response.PageResponse;
import com.alphascope.common.response.Result;
import com.alphascope.operations.policy.ResearchData;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台研究验证入口：接收即返回任务，状态和摘要轻量查询，大证据仅显式下载。
 *
 * @author chenxuefeng
 */
@ResearchData
@RestController
public class ValidationTaskController {
  private final ValidationTaskService tasks;

  public ValidationTaskController(ValidationTaskService tasks) {
    this.tasks = tasks;
  }

  /**
   * 显式提交后台验证，禁止通过刷新GET启动计算。
   *
   * @param request 日期、验证类型和固定自选范围
   * @return 已持久接收的任务，HTTP202
   */
  @PostMapping("/api/v1/admin/backtest-validation-tasks")
  public ResponseEntity<Result<ValidationTaskView>> submit(
      @Valid @RequestBody ResearchValidationRequest request) {
    return ResponseEntity.accepted().body(Result.success(tasks.submit(request)));
  }

  /**
   * 分页读取轻量历史。
   *
   * @param request 页码与大小
   * @return 不含原始输入的任务分页
   */
  @GetMapping("/api/v1/backtest/validation-tasks")
  public Result<PageResponse<ValidationTaskView>> page(
      @Valid @ModelAttribute @ParameterObject PageRequest request) {
    return Result.success(tasks.page(request));
  }

  /**
   * 读取实际进度和已经发布的结果摘要。
   *
   * @param id 任务主键
   * @return 轻量详情
   */
  @GetMapping("/api/v1/backtest/validation-tasks/{id}")
  public Result<ValidationTaskView> get(@PathVariable long id) {
    return Result.success(tasks.get(id));
  }

  /**
   * 保存取消意图，已完成任务保持实际终态。
   *
   * @param id 任务主键
   * @return 实际取消或完成状态
   */
  @PostMapping("/api/v1/admin/backtest-validation-tasks/{id}/cancel")
  public Result<ValidationTaskView> cancel(@PathVariable long id) {
    return Result.success(tasks.cancel(id));
  }

  /**
   * 下载已完成证据，校验失败拒绝返回；文件响应不包Result。
   *
   * @param id 任务主键
   * @return 完整冻结JSON附件
   */
  @ResearchData(permissions = {"DISPLAY", "EXPORT"})
  @GetMapping("/api/v1/backtest/validation-tasks/{id}/evidence")
  public ResponseEntity<byte[]> evidence(@PathVariable long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"alphascope-validation-" + id + ".json\"")
        .body(tasks.evidence(id).getBytes(StandardCharsets.UTF_8));
  }
}

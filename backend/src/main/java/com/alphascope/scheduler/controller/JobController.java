package com.alphascope.scheduler.controller;

import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.response.PageResponse;
import com.alphascope.common.response.Result;
import com.alphascope.operations.policy.OperationalData;
import com.alphascope.scheduler.dto.JobSubmissionCommand;
import com.alphascope.scheduler.service.JobService;
import com.alphascope.scheduler.vo.JobView;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提交持久研究任务、读取执行阶段并显式重试失败尝试；提交后异步执行。
 *
 * @author chenxuefeng
 */
@OperationalData
@RestController
@RequestMapping("/api/v1/admin/jobs")
public class JobController {
  private final JobService service;

  public JobController(JobService service) {
    this.service = service;
  }

  /**
   * 按分页请求对象读取持久任务记录。
   *
   * @param request page 默认为 1、size 默认为 20，分页边界由字段约束校验
   * @return 统一响应中的任务执行分页结果
   */
  @GetMapping
  public Result<PageResponse<JobView>> list(
      @Valid @ModelAttribute @ParameterObject com.alphascope.scheduler.dto.JobQuery request) {
    return Result.success(service.page(request));
  }

  /**
   * 读取一次持久任务尝试及其执行阶段。
   *
   * @param id 任务执行记录主键
   * @return 状态、心跳、错误及阶段明细
   */
  @GetMapping("/{id}")
  public Result<JobView> get(@PathVariable long id) {
    return Result.success(service.get(id));
  }

  /**
   * 提交日报任务，响应只确认排队，后台继续执行。
   *
   * @param date 研究业务日期，不得晚于上海当前日期
   * @param force 是否显式请求新修订，默认false
   * @return HTTP 202及已接收或复用的任务详情
   */
  @PostMapping("/daily")
  public ResponseEntity<Result<JobView>> daily(
      @RequestParam LocalDate date, @RequestParam(defaultValue = "false") boolean force) {
    return accepted(
        service.submit(
            JobSubmissionCommand.builder()
                .reportType("DAILY")
                .businessDate(date)
                .force(force)
                .retryOf(null)
                .build()));
  }

  /**
   * 显式提交三所同日清单、行情及历史证据核验，不生成日报。
   *
   * @param date 已完成交易日
   * @return HTTP 202及已接收任务
   */
  @PostMapping("/market-full")
  public ResponseEntity<Result<JobView>> fullMarket(@RequestParam LocalDate date) {
    return accepted(
        service.submit(
            JobSubmissionCommand.builder()
                .reportType("MARKET_FULL")
                .businessDate(date)
                .force(true)
                .build()));
  }

  /**
   * 提交沪深历史资料补齐。
   *
   * @param date 已完成交易日
   * @return 接收的任务
   */
  @PostMapping("/market-history")
  public ResponseEntity<Result<JobView>> marketHistory(@RequestParam LocalDate date) {
    return accepted(
        service.submit(
            JobSubmissionCommand.builder()
                .reportType("MARKET_HISTORY")
                .businessDate(date)
                .force(true)
                .build()));
  }

  /**
   * 管理员显式补齐当前北交所在市目录；盘中也可采集清单，不产生收盘行情或报告。
   *
   * @param date 当前目录观测日期；历史无归档时不倒填
   * @return HTTP 202及持久采集任务
   */
  @PostMapping("/market-universe")
  public ResponseEntity<Result<JobView>> marketUniverse(@RequestParam LocalDate date) {
    return accepted(
        service.submit(
            JobSubmissionCommand.builder()
                .reportType("MARKET_UNIVERSE")
                .businessDate(date)
                .force(true)
                .build()));
  }

  /**
   * 提交周报任务，响应只确认排队，后台继续执行。
   *
   * @param date 研究业务日期，不得晚于上海当前日期
   * @param force 是否显式请求新修订，默认false
   * @return HTTP 202及已接收或复用的任务详情
   */
  @PostMapping("/weekly")
  public ResponseEntity<Result<JobView>> weekly(
      @RequestParam LocalDate date, @RequestParam(defaultValue = "false") boolean force) {
    return accepted(
        service.submit(
            JobSubmissionCommand.builder()
                .reportType("WEEKLY")
                .businessDate(date)
                .force(force)
                .retryOf(null)
                .build()));
  }

  /**
   * 在原任务编号下重新排队，归档原执行状态和阶段。
   *
   * @param id 被重试的原任务主键
   * @return HTTP 202及原任务的最新状态
   */
  @PostMapping("/{id}/retry")
  public ResponseEntity<Result<JobView>> retry(@PathVariable long id) {
    return accepted(service.retry(id));
  }

  /**
   * 持久化取消意图；运行任务会先释放资源再更新最终状态。
   *
   * @param id 待取消任务主键
   * @return 当前取消请求或最终状态，不承诺阻塞调用立即退出
   */
  @PostMapping("/{id}/cancel")
  public Result<JobView> cancel(@PathVariable long id) {
    return Result.success(service.cancel(id));
  }

  /**
   * 分页查看原任务历次执行，不把失败尝试铺成新任务。
   *
   * @param id 任务编号
   * @param request 分页
   * @return 不可变执行历史
   */
  @GetMapping("/{id}/attempts")
  public Result<PageResponse<JobView>> history(
      @PathVariable long id, @Valid @ModelAttribute @ParameterObject PageRequest request) {
    return Result.success(service.history(id, request));
  }

  private ResponseEntity<Result<JobView>> accepted(long id) {
    return ResponseEntity.accepted().body(get(id));
  }
}

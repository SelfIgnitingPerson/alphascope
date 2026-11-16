package com.alphascope.backtest.service;

import com.alphascope.backtest.dto.ResearchValidationRequest;
import com.alphascope.backtest.vo.ValidationTaskView;
import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.response.PageResponse;

/**
 * 研究验证队列、取消和证据下载，与日报任务分别保存和调度。
 *
 * @author chenxuefeng
 */
public interface ValidationTaskService {
  /**
   * 校验并冻结请求，重复活动条件返回原任务。
   *
   * @param request 明确日期、验证类型和自选
   * @return 已持久接收的轻量任务
   */
  ValidationTaskView submit(ResearchValidationRequest request);

  /**
   * 分页读取任务元数据。
   *
   * @param request 分页条件
   * @return 不含大证据的分页
   */
  PageResponse<ValidationTaskView> page(PageRequest request);

  /**
   * 读取状态与轻量结果。
   *
   * @param id 任务主键
   * @return 任务及已有摘要
   */
  ValidationTaskView get(long id);

  /**
   * 保存取消意图并协作中断本机执行。
   *
   * @param id 任务主键
   * @return 实际状态，不覆盖已完成结果
   */
  ValidationTaskView cancel(long id);

  /**
   * 显式下载成功证据并核对文本指纹。
   *
   * @param id 任务主键
   * @return 完整冻结JSON文本
   */
  String evidence(long id);

  /** 从持久队首原子领取并执行一项验证。 */
  void processNext();

  /** 独立核对截止、停止意图、归属和失联心跳。 */
  void monitor();
}

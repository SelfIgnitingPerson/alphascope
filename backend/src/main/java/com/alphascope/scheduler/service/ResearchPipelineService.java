package com.alphascope.scheduler.service;

import com.alphascope.scheduler.dto.PipelineCommand;
import com.alphascope.scheduler.dto.PipelineOutcome;

/**
 * 单一研究流水线编排，不承担队列提交、互斥和失联恢复。
 *
 * @author chenxuefeng
 */
public interface ResearchPipelineService {
  /**
   * 使用已经领取的持久任务执行收盘研究各阶段。
   *
   * @param command 任务、日期、重试及进程归属
   * @return 最终状态、实际质量和成功输入指纹
   */
  PipelineOutcome run(PipelineCommand command);
}

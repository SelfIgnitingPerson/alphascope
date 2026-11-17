package com.alphascope.scheduler.entity;

import lombok.Data;

/**
 * 数据库记录实体，保留同任务历次执行的完整快照及指纹，不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
public class JobAttemptHistory {
  /** 原任务编号，不创建新的主列表任务。 */
  private Long jobId;

  /** 原执行序号。 */
  private Integer attempt;

  /** 原状态、错误、来源与阶段明细。 */
  private String executionJson;

  /** 完整执行快照的SHA256。 */
  private String contentHash;
}

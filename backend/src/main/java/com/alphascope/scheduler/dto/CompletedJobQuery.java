package com.alphascope.scheduler.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/**
 * 按任务、日期、数据源与输入指纹精确复用已完成任务。
 *
 * @author chenxuefeng
 */
@Data
@Builder
public class CompletedJobQuery {
  /** 研究任务类型 DAILY 或 WEEKLY。 */
  private String jobName;

  /** 本次请求研究的业务日期。 */
  private LocalDate businessDate;

  /** 本次运行配置的数据源范围。 */
  private String dataScope;

  /** 完整业务输入指纹，相同指纹才允许复用成功结果。 */
  private String inputHash;
}

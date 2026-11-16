package com.alphascope.scheduler.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class JobStage {
  /** 记录主键，自增生成 */
  private Long id;

  /** 任务主键，关联job_execution.id */
  private Long jobId;

  /** 流水线阶段代码，如CALENDAR、MARKET_SYNC、REPORT */
  private String stageName;

  /** 阶段执行状态：RUNNING、SUCCESS、FAILED或INTERRUPTED等 */
  private String status;

  /** 本阶段开始时间，上海时区 */
  private LocalDateTime startedAt;

  /** 本阶段结束时间，上海时区；未结束时为空 */
  private LocalDateTime endedAt;

  /** 本阶段失败或中断的具体原因 */
  private String error;

  /** 与阶段执行状态独立的数据质量；运行中或旧记录可为空。 */
  private String dataStatus;

  /** 阶段核验、实际进度、硬截止和恢复来源JSON；旧记录可为空。 */
  private String detailsJson;
}

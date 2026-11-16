package com.alphascope.scheduler.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class JobExecution {
  /** 记录主键，自增生成 */
  private Long id;

  /** 任务类型：DAILY日报，WEEKLY周报 */
  private String jobName;

  /** 本次研究或采集的业务日期，按上海时区记录 */
  private LocalDate businessDate;

  /** 同任务类型和业务日期的尝试序号，从1递增 */
  private Integer attempt;

  /** 任务执行状态，包含排队、运行、成功、跳过、失败、中断、取消请求/完成及超时请求/完成 */
  private String status;

  /** 本次尝试创建或开始执行的时间，上海时区 */
  private LocalDateTime startedAt;

  /** 本次尝试结束时间，上海时区；未结束时为空 */
  private LocalDateTime endedAt;

  /** 本次执行失败、中断或跳过的原因说明 */
  private String error;

  /** 记录创建时间，上海时区 */
  private LocalDateTime createdAt;

  /** 记录最后更新时间，上海时区 */
  private LocalDateTime updatedAt;

  /** 运行数据范围标识，如DEMO或REAL:BAOSTOCK,SINA；不等于单条数据实际来源 */
  private String dataScope;

  /** 计算或生成时完整输入的SHA-256指纹 */
  private String inputHash;

  /** 当前消费任务的工作进程标识，用于互斥和失联恢复 */
  private String ownerId;

  /** 工作进程最近心跳时间，上海时区 */
  private LocalDateTime heartbeatAt;

  /** 被重试的原任务ID；首次提交时为空 */
  private Long retryOf;

  /** 任务数据质量：AVAILABLE完整、PARTIAL部分、UNAVAILABLE缺失、NOT_APPLICABLE不适用、UNKNOWN未核验；旧记录为空。 */
  private String dataStatus;
}

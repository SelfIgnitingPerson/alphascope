package com.alphascope.intelligence.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class AnalysisReport {
  /** 记录主键，自增生成 */
  private Long id;

  /** 报告类型：DAILY收盘日报，WEEKLY每周周报 */
  private String reportType;

  /** 本次研究或采集的业务日期，按上海时区记录 */
  private LocalDate businessDate;

  /** 同类型、同业务日的报告修订序号，从1递增 */
  private Integer revision;

  /** 报告生成状态，当前成功入库为SUCCESS */
  private String status;

  /** 生成模型名称；RULE_FALLBACK表示使用规则摘要 */
  private String model;

  /** 生成报告采用的提示词或规则摘要结构标识 */
  private String promptVersion;

  /** 计算或生成时完整输入的SHA-256指纹 */
  private String inputHash;

  /** 模型调用消耗的Token数量；未记录时为NULL */
  private Integer tokenUsage;

  /** 结构化报告正文JSON，区分事实、风险、解释和待验证事项 */
  private String contentJson;

  /** 保存的Markdown摘要正文 */
  private String contentMd;

  /** 报告采用行情的业务截止时刻，通常为交易日15:00，上海时区 */
  private LocalDateTime dataAsOf;

  /** 演示数据标志：0真实数据，1演示数据；按模式隔离 */
  @JsonProperty("is_synthetic")
  private Boolean synthetic;

  /** 记录创建时间，上海时区 */
  private LocalDateTime createdAt;

  /** 记录最后更新时间，上海时区 */
  private LocalDateTime updatedAt;

  /** 报告采用的输入证据中最新实际采集时刻；旧记录可为NULL */
  private LocalDateTime collectedAt;

  /** 计算算法标识，用于追溯当时采用的计算口径 */
  private String algorithmVersion;

  /** 运行数据范围标识，如DEMO或REAL:BAOSTOCK,SINA；不等于单条数据实际来源 */
  private String dataScope;

  /** 生成该报告的任务ID；旧记录或直接生成时可为NULL */
  private Long jobId;
}

package com.alphascope.intelligence.vo;

import com.alphascope.intelligence.entity.AnalysisReport;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报告归档条目，不携带大体积冻结输入。
 *
 * @author chenxuefeng
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ReportSummary(
    /** 当前资源记录的数据库主键。 */
    long id,

    /** 报告类型：DAILY收盘日报，WEEKLY每周周报。 */
    String reportType,

    /** 本次研究采用的业务日期，使用上海时区。 */
    LocalDate businessDate,

    /** 同类型、同业务日期的报告修订序号，从1递增。 */
    int revision,

    /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
    String status,

    /** 实际使用的生成模型；RULE_FALLBACK表示规则摘要。 */
    String model,

    /** 报告采用行情的业务截止时刻，通常为交易日15:00，上海时区。 */
    LocalDateTime dataAsOf,

    /** 本报告采用的证据中最新实际采集时刻；复用缓存不改写。 */
    LocalDateTime collectedAt,

    /** 是否为演示数据；真实数据和演示数据必须隔离。 */
    boolean isSynthetic,

    /** 本次计算采用的算法标识，用于核对历史口径。 */
    String algorithmVersion,

    /** 运行配置的数据源范围，例如DEMO或REAL:BAOSTOCK,SINA，不等于单条证据来源。 */
    String dataScope,

    /** 计算所采用完整输入的SHA-256指纹。 */
    String inputHash) {
  /**
   * 显式转换不包含大体积冻结正文的报告记录；字段调整由编译器检查，空值不经字符串或 JSON 中转。
   *
   * @param row 不包含大体积冻结正文的报告记录，不可为 null
   * @return 保持既有接口字段及单位的公开响应
   */
  public static ReportSummary from(AnalysisReport row) {
    return new ReportSummary(
        row.getId() == null ? 0 : row.getId(),
        row.getReportType(),
        row.getBusinessDate(),
        row.getRevision() == null ? 0 : row.getRevision(),
        row.getStatus(),
        row.getModel(),
        row.getDataAsOf(),
        row.getCollectedAt(),
        Boolean.TRUE.equals(row.getSynthetic()),
        row.getAlgorithmVersion(),
        row.getDataScope(),
        row.getInputHash());
  }
}

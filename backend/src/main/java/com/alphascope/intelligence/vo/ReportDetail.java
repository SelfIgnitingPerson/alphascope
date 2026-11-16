package com.alphascope.intelligence.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报告详情响应；正文与证据均来自该修订已经保存的内容。
 *
 * @author chenxuefeng
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ReportDetail(
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
    String inputHash,

    /** 生成时保存的结构化文字报告，不从当前数据库补写历史正文。 */
    JsonNode contentJson,

    /** 本修订实际采用的来源文档或数据批次引用列表。 */
    List<Source> sources,

    /** 本修订冻结的完整输入JSON，是读取历史报告的唯一证据边界。 */
    JsonNode inputSnapshot,

    /** 由该修订冻结输入形成的结构化分析，允许为空以兼容缺少证据的旧报告。 */
    ReportAnalysis analysis) {
  /**
   * 本报告实际采用的文档或数据记录引用。
   *
   * @author chenxuefeng
   */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Source(
      /** 引用的原始来源文档ID；仅有数据批次引用时可为空。 */
      Long sourceDocumentId,

      /** 实际引用的数据记录或采集批次标识。 */
      String dataReference) {}
}

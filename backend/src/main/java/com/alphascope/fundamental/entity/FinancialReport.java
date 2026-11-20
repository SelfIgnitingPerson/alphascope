package com.alphascope.fundamental.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
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
public class FinancialReport {
  /** 记录主键，自增生成 */
  private Long id;

  /** 标的主键，关联instrument.id */
  private Long instrumentId;

  /** 累计财报对应的报告期末日期 */
  private LocalDate periodEnd;

  /** 累计口径：Q1_CUMULATIVE一季、H1_CUMULATIVE半年、Q3_CUMULATIVE三季、ANNUAL全年 */
  private String reportType;

  /** 原始材料公开发布时间，上海时区 */
  private LocalDateTime publishedAt;

  /** 同标的、报告期和报告类型的内容版本序号，只新增不覆盖 */
  private Integer version;

  /** 累计营业收入，人民币元；NULL表示缺失 */
  private BigDecimal revenue;

  /** 累计归属于母公司股东的净利润，人民币元；NULL表示缺失 */
  private BigDecimal netProfit;

  /** 累计经营活动产生的现金流量净额，人民币元；NULL表示缺失 */
  private BigDecimal operatingCashflow;

  /** 累计毛利率，小数存储，如0.30表示30%；NULL表示缺失 */
  private BigDecimal grossMargin;

  /** 累计营业收入同比增速，小数存储；NULL表示缺失 */
  private BigDecimal revenueYoy;

  /** 累计归母净利润同比增速，小数存储；NULL表示缺失 */
  private BigDecimal profitYoy;

  /** 实际数据供应商或人工来源标识 */
  private String source;

  /** 记录创建时间，上海时区 */
  private LocalDateTime createdAt;

  /** 记录最后更新时间，上海时区 */
  private LocalDateTime updatedAt;

  /** 本版本最早可用于研究的时刻；旧手工记录NULL时沿用published_at */
  private LocalDateTime availableAt;

  /** 公告时间精度：TIMESTAMP精确时刻，DAY仅日期；日期精度保守次日起可用 */
  private String publicationPrecision;

  /** 供应商标示的修订时间；不是已核验的原始公告修订时间 */
  private LocalDateTime vendorUpdatedAt;

  /** 原始数据实际采集时间，上海时区；复用缓存不改写 */
  private LocalDateTime fetchedAt;

  /** 内容SHA-256指纹，用于去重和定位不可变证据 */
  private String contentHash;

  /** 原始公开来源URL，用于核验和追溯 */
  private String sourceUrl;

  /** 供应商原始响应JSON，用于核验来源及保留当次证据 */
  private String rawJson;

  /** 新采集的逐字原始响应，旧材料缺省，不倒填。 */
  private String rawPayload;
}

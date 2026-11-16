package com.alphascope.fundamental.vo;

import com.alphascope.fundamental.entity.FinancialReport;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对外查询响应；与数据库实体分离，保持单位和业务时点语义。
 *
 * @author chenxuefeng
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FinancialView(
    /** 记录主键，自增生成 */
    Long id,

    /** 标的主键，关联instrument.id */
    Long instrumentId,

    /** 累计财报对应的报告期末日期 */
    LocalDate periodEnd,

    /** 累计口径：Q1_CUMULATIVE一季、H1_CUMULATIVE半年、Q3_CUMULATIVE三季、ANNUAL全年 */
    String reportType,

    /** 原始材料公开发布时间，上海时区 */
    LocalDateTime publishedAt,

    /** 同标的、报告期和报告类型的内容版本序号，只新增不覆盖 */
    Integer version,

    /** 累计营业收入，人民币元；NULL表示缺失 */
    BigDecimal revenue,

    /** 累计归属于母公司股东的净利润，人民币元；NULL表示缺失 */
    BigDecimal netProfit,

    /** 累计经营活动产生的现金流量净额，人民币元；NULL表示缺失 */
    BigDecimal operatingCashflow,

    /** 累计毛利率，小数存储，如0.30表示30%；NULL表示缺失 */
    BigDecimal grossMargin,

    /** 累计营业收入同比增速，小数存储；NULL表示缺失 */
    BigDecimal revenueYoy,

    /** 累计归母净利润同比增速，小数存储；NULL表示缺失 */
    BigDecimal profitYoy,

    /** 实际数据供应商或人工来源标识 */
    String source,

    /** 记录创建时间，上海时区 */
    LocalDateTime createdAt,

    /** 记录最后更新时间，上海时区 */
    LocalDateTime updatedAt,

    /** 本版本最早可用于研究的时刻；旧手工记录NULL时沿用published_at */
    LocalDateTime availableAt,

    /** 公告时间精度：TIMESTAMP精确时刻，DAY仅日期；日期精度保守次日起可用 */
    String publicationPrecision,

    /** 供应商标示的修订时间；不是已核验的原始公告修订时间 */
    LocalDateTime vendorUpdatedAt,

    /** 原始数据实际采集时间，上海时区；复用缓存不改写 */
    LocalDateTime fetchedAt,

    /** 内容SHA-256指纹，用于去重和定位不可变证据 */
    String contentHash,

    /** 原始公开来源URL，用于核验和追溯 */
    String sourceUrl,

    /** 供应商原始响应JSON，用于核验来源及保留当次证据 */
    String rawJson) {
  /**
   * 显式转换业务时点可见的累计财报版本；字段调整由编译器检查，空值不经字符串或 JSON 中转。
   *
   * @param row 业务时点可见的累计财报版本，不可为 null
   * @return 保持既有接口字段及单位的公开响应
   */
  public static FinancialView from(FinancialReport row) {
    return new FinancialView(
        row.getId(),
        row.getInstrumentId(),
        row.getPeriodEnd(),
        row.getReportType(),
        row.getPublishedAt(),
        row.getVersion(),
        row.getRevenue(),
        row.getNetProfit(),
        row.getOperatingCashflow(),
        row.getGrossMargin(),
        row.getRevenueYoy(),
        row.getProfitYoy(),
        row.getSource(),
        row.getCreatedAt(),
        row.getUpdatedAt(),
        row.getAvailableAt(),
        row.getPublicationPrecision(),
        row.getVendorUpdatedAt(),
        row.getFetchedAt(),
        row.getContentHash(),
        row.getSourceUrl(),
        row.getRawJson());
  }
}

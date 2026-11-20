package com.alphascope.fundamental.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 用户自行核对的长期估值情景，独立保存依据，不冒充机构目标价或系统认证价值。
 *
 * @author chenxuefeng
 * @param financialSource 实际EPS依据的数据来源，与来源许可登记对应
 * @param symbol 证券代码
 * @param asOf 研究依据截止日
 * @param validUntil 假设最迟复核日，过期停止使用
 * @param epsLow 保守年化归母每股收益，人民币元，须注明股本口径
 * @param epsHigh 乐观年化归母每股收益，人民币元
 * @param peLow 保守市盈率倍数
 * @param peHigh 乐观市盈率倍数
 * @param safetyMargin 用户明确选择的安全边际比例
 * @param businessConfirmed 是否已由用户核对经营逻辑
 * @param rationale 盈利、估值倍数、股本口径及业务证据说明
 * @param invalidation 经营假设失效的可观察条件
 * @param sourceUrl 公开财报或业务证据链接
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(value = "inputSources", allowGetters = true)
public record ValuationThesisInput(
    @NotBlank @Pattern(regexp = "[0-9]{6}\\.(SH|SZ|BJ)") String symbol,
    @NotNull LocalDate asOf,
    @NotNull LocalDate validUntil,
    @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") BigDecimal epsLow,
    @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") BigDecimal epsHigh,
    @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("200") BigDecimal peLow,
    @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("200") BigDecimal peHigh,
    @NotNull @DecimalMin("0") @DecimalMax("0.8") BigDecimal safetyMargin,
    boolean businessConfirmed,
    @NotBlank @Size(min = 10, max = 4000) String rationale,
    @NotBlank @Size(min = 5, max = 2000) String invalidation,
    @NotBlank @Size(max = 1000) @Pattern(regexp = "https?://[^\\s]+") String sourceUrl,
    @NotBlank @Pattern(regexp = "EASTMONEY_FINANCIAL|BAOSTOCK|SINA|TUSHARE") String financialSource,
    /** 股本证据，缺失不做独立估值通过声明。 */
    @jakarta.validation.Valid ValuationShareBasis shareBasis,
    /** 最多8个明确同行，服务端核对实际财报、价格和口径，不接受客户端PE值。 */
    @jakarta.validation.Valid @jakarta.validation.constraints.Size(max = 8)
        java.util.List<ComparablePeer> peers,
    /** 财报可观察的失效条件，原归档没有这个字段。 */
    @jakarta.validation.Valid
        @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        ValuationExitPolicy exitPolicy) {
  /** 兼容已保存的情景及依赖原参数的调用。 */
  public ValuationThesisInput(
      String symbol,
      LocalDate asOf,
      LocalDate validUntil,
      BigDecimal epsLow,
      BigDecimal epsHigh,
      BigDecimal peLow,
      BigDecimal peHigh,
      BigDecimal safetyMargin,
      boolean businessConfirmed,
      String rationale,
      String invalidation,
      String sourceUrl,
      String financialSource,
      ValuationShareBasis shareBasis,
      java.util.List<ComparablePeer> peers) {
    this(
        symbol,
        asOf,
        validUntil,
        epsLow,
        epsHigh,
        peLow,
        peHigh,
        safetyMargin,
        businessConfirmed,
        rationale,
        invalidation,
        sourceUrl,
        financialSource,
        shareBasis,
        peers,
        null);
  }

  /** 保留旧情景输入，新增独立依据缺省为未知。 */
  public ValuationThesisInput(
      String symbol,
      LocalDate asOf,
      LocalDate validUntil,
      BigDecimal epsLow,
      BigDecimal epsHigh,
      BigDecimal peLow,
      BigDecimal peHigh,
      BigDecimal safetyMargin,
      boolean businessConfirmed,
      String rationale,
      String invalidation,
      String sourceUrl,
      String financialSource) {
    this(
        symbol,
        asOf,
        validUntil,
        epsLow,
        epsHigh,
        peLow,
        peHigh,
        safetyMargin,
        businessConfirmed,
        rationale,
        invalidation,
        sourceUrl,
        financialSource,
        null,
        java.util.List.of());
  }

  /** 来源从明确登记的EPS依据提取，不能由客户端另填inputSources覆盖。 */
  @com.fasterxml.jackson.annotation.JsonProperty("inputSources")
  public java.util.Set<String> inputSources() {
    return java.util.Set.of(financialSource);
  }
}

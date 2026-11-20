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
 * 人工登记公开公司行动，仅支持明确日期的现金分红与送转，不从复权因子猜测账务。
 *
 * @author chenxuefeng
 * @param symbol 标准证券代码
 * @param exDate 除权除息日
 * @param paymentDate 现金实际到账日
 * @param shareListingDate 新增股份可交易日，暂仅支持与除权日相同
 * @param priceSource 与回放行情一致的价格供应商
 * @param factorBefore 同一价格源的前一交易日因子
 * @param factorAfter 同一价格源的除权日因子
 * @param shareMultiplier 送转后每股对应股数，无送转填1
 * @param cashPerShare 每原股税前现金分红，元
 * @param exReferencePrice 公告或交易所除权参考价，用于当日开盘限制核对
 * @param sourceUrl 可复核的公开公告链接
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(value = "inputSources", allowGetters = true)
public record CorporateActionInput(
    @NotBlank @Pattern(regexp = "[0-9]{6}\\.(SH|SZ|BJ)") String symbol,
    @NotNull LocalDate exDate,
    @NotNull LocalDate paymentDate,
    @NotNull LocalDate shareListingDate,
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String priceSource,
    @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal factorBefore,
    @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal factorAfter,
    @NotNull @DecimalMin("1") @DecimalMax("10") BigDecimal shareMultiplier,
    @NotNull @DecimalMin("0") @DecimalMax("1000") BigDecimal cashPerShare,
    @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal exReferencePrice,
    @NotBlank @Size(max = 1000) @Pattern(regexp = "https?://[^\\s]+") String sourceUrl) {
  /** 本条行动实际匹配的价格供应商，不采用客户端额外声称的来源。 */
  @com.fasterxml.jackson.annotation.JsonProperty("inputSources")
  public java.util.Set<String> inputSources() {
    return java.util.Set.of(priceSource);
  }
}

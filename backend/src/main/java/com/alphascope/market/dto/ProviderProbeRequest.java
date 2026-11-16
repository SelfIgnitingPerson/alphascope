package com.alphascope.market.dto;

import com.alphascope.market.provider.MarketCapability;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个已配置通道的基础能力测试条件；样本代码固定，不接受任意URL或批量范围。
 *
 * @author chenxuefeng
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderProbeRequest {
  /** 当前已配置的通道代码，由服务端核验；不接受用户指定主机。 */
  @NotBlank
  @Pattern(regexp = "[A-Z_]{1,32}")
  private String channel;

  /** 仅允许交易日历、股票日线或指数日线；不进行全市场批量采集。 */
  @NotNull private MarketCapability capability;

  /** 精确测试日期；无行情不能解释成网络断开，未来日期不允许提交。 */
  @NotNull private LocalDate date;
}

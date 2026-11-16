package com.alphascope.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 固定结构的数据传输对象，禁止使用无类型Map代替。
 *
 * @author chenxuefeng
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WatchlistDO {
  /** 关注标的代码。 */
  private String symbol;

  /** 标的中文名称。 */
  private String name;

  /** 关注优先级，越大越优先。 */
  private Integer priority;

  /** 是否启用该自选。 */
  private Boolean enabled;
}

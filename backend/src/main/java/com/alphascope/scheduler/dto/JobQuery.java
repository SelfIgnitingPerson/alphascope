package com.alphascope.scheduler.dto;

import com.alphascope.common.dto.PageRequest;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务类型与分页查询，身份由服务端注入。
 *
 * @author chenxuefeng
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobQuery extends PageRequest {
  /** 留空表示全部任务类型。 */
  @Pattern(regexp = "DAILY|WEEKLY|MARKET_HISTORY|MARKET_UNIVERSE|MARKET_FULL")
  private String type;

  /**
   * 受校验页码对应的零起点。
   *
   * @return 查询偏移
   */
  public int getOffset() {
    return (getPage() - 1) * getSize();
  }
}

package com.alphascope.intelligence.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 当前研究视图的业务日期查询，不触发采集、写入或重建旧报告。
 *
 * @author chenxuefeng
 */
@Data
public class HoldingResearchQuery {
  /** 必填截止日期，未来日期由业务层拒绝。 */
  @NotNull
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate asOf;
}

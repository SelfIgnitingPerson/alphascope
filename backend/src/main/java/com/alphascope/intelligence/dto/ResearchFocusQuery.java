package com.alphascope.intelligence.dto;

import com.alphascope.common.dto.PageRequest;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 按当前用户自选优先级分页核对研究变化，不接受客户端指定用户。
 *
 * @author chenxuefeng
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResearchFocusQuery extends PageRequest {
  /** 所选研究日，未来日期在服务层拒绝。 */
  @NotNull
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate asOf;
}

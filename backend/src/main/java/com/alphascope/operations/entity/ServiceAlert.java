package com.alphascope.operations.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据库记录实体，保存当前用户的运行告警生命周期，不直接暴露接口。
 *
 * @author chenxuefeng
 */
@Data
public class ServiceAlert {
  /** 内部告警主键。 */
  private Long id;

  /** 确定性的受控事项摘要。 */
  private String issueKey;

  /** 对用户可见的具体问题。 */
  private String message;

  /** 首次发现时间。 */
  private LocalDateTime firstSeenAt;

  /** 最近观察时间。 */
  private LocalDateTime lastSeenAt;

  /** 恢复时间，未恢复为空。 */
  private LocalDateTime resolvedAt;
}

package com.alphascope.market.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
public class MarketUniverseSnapshot {
  /** 不可变记录主键。 */
  private Long id;

  /** 清单实际观测的上海日期，不能由客户端回填。 */
  private LocalDate businessDate;

  /** 清单范围，当前BSE_A代表北交所在市A股。 */
  private String universeScope;

  /** 实际目录来源，例如SINA。 */
  private String source;

  /** 成员文本与证据文本组合的SHA256。 */
  private String contentHash;

  /** 已核验成员的精确JSON文本，不受数据库JSON规范化影响。 */
  private String membersJson;

  /** 来源分页与前后总数确认的精确JSON文本。 */
  private String evidenceJson;

  /** 实际核验结束时间，上海时区。 */
  private LocalDateTime observedAt;
}

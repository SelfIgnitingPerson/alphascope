package com.alphascope.operations.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
public class SourceLicense {
  /** 登记修订主键，只新增不覆盖。 */
  private Long id;

  /** 来源标识。 */
  private String source;

  /** 已登记使用范围JSON。 */
  private String permissionsJson;

  /** 授权依据URL，不是密钥。 */
  private String evidenceUrl;

  /** 合同或条款定位。 */
  private String reference;

  /** 许可开始日期。 */
  private LocalDate validFrom;

  /** 许可截止日期。 */
  private LocalDate validUntil;

  /** 实际登记管理员，不接受客户端指定。 */
  private Long reviewerId;

  /** 登记内容SHA256。 */
  private String contentHash;

  /** 真实登记时刻。 */
  private LocalDateTime createdAt;
}

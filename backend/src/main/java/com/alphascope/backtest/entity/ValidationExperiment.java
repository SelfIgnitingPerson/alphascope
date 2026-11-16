package com.alphascope.backtest.entity;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
@NoArgsConstructor
public class ValidationExperiment {
  /** 不可变实验主键。 */
  private Long id;

  /** 实验证券代码。 */
  private String symbol;

  /** 保存时数据范围。 */
  private String dataScope;

  /** 实际可用时点模式。 */
  private String availabilityMode;

  /** 冻结方法版本。 */
  private String methodVersion;

  /** 冻结请求。 */
  private String requestJson;

  /** 冻结完整输入。 */
  private String snapshotJson;

  /** 冻结完整结果。 */
  private String resultJson;

  /** 输入语义SHA256。 */
  private String inputHash;

  /** 结果语义SHA256。 */
  private String resultHash;

  /** 数据库实际保存时刻。 */
  private LocalDateTime createdAt;
}

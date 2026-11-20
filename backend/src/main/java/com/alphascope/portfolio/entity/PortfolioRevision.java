package com.alphascope.portfolio.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
@Data
public class PortfolioRevision {
  /** 登记版本主键。 */
  private Long id;

  /** POSITION或ACCOUNT。 */
  private String recordKind;

  /** 证券代码或ACCOUNT。 */
  private String symbol;

  /** 人工声明的业务日。 */
  private LocalDate businessDate;

  /** 不可变登记正文。 */
  private String evidenceJson;

  /** 正文指纹。 */
  private String evidenceHash;

  /** 实际形成时间，禁止倒填。 */
  private LocalDateTime createdAt;
}

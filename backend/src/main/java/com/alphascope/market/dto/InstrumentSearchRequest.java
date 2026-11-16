package com.alphascope.market.dto;

import com.alphascope.common.dto.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 证券代码或中文名称搜索及分页条件。
 *
 * @author chenxuefeng
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentSearchRequest extends PageRequest {
  /** 代码或名称关键词；省略或空值时匹配全部证券。 */
  @Builder.Default private String q = "";

  /**
   * 读取搜索关键词，不对用户提供的非空文本额外裁剪。
   *
   * @return 已提供关键词；未提供时为空字符串
   */
  public String getQ() {
    return q == null ? "" : q;
  }
}

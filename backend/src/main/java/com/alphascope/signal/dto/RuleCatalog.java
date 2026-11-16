package com.alphascope.signal.dto;

import java.util.List;

/**
 * 当前规则版本及完整预期清单，分母不从已经保存的结果反推。
 *
 * @author chenxuefeng
 */
public record RuleCatalog(
    /** 当前规则版本。 */
    String version,
    /** 每项规则及当前研究模式的适用性。 */
    List<Definition> definitions) {
  /**
   * 单项规则的稳定代码和适用条件。
   *
   * @author chenxuefeng
   */
  public record Definition(
      /** 稳定规则代码。 */
      String code,
      /** 当前模式是否需要实际评估；false也必须保存明确的不适用记录。 */
      boolean applicable) {}
}

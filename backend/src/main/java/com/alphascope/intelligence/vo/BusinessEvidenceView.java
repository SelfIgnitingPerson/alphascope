package com.alphascope.intelligence.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 经营与估值的独立证据检查，不由用户乐观假设生成评级。
 *
 * @author chenxuefeng
 * @param date 研究截止日
 * @param period 财报期末
 * @param source 实际财报来源
 * @param sourceUrl 公开原始材料
 * @param cashConversion 正利润时经营现金流除净利润
 * @param marginChange 同源同累计期毛利率差，小数
 * @param disclosedAnnualEps 最近已披露年度基本EPS，未核验股本一致性时仅作对照
 * @param findings 观察到的事实及解释边界
 * @param unknowns 不能确定的驱动、资产负债与估值证据
 * @param valuationStatus 独立估值证据状态，不表示合理价值认证
 * @param inputSources 本次实际来源
 */
public record BusinessEvidenceView(
    LocalDate date,
    LocalDate period,
    String source,
    String sourceUrl,
    Double cashConversion,
    Double marginChange,
    Double disclosedAnnualEps,
    List<String> findings,
    List<String> unknowns,
    String valuationStatus,
    Set<String> inputSources,
    /** 实际用于比率与EPS的保存版本；供应商链接可能返回新版本，按指纹定位。 */
    List<Reference> references,
    /** 三表经营质量及明确原文路径。 */
    OperatingQuality operatingQuality) {
  /** 保留旧HTTP测试和冻结记录构造，缺省不补造经营材料。 */
  public BusinessEvidenceView(
      LocalDate date,
      LocalDate period,
      String source,
      String sourceUrl,
      Double cashConversion,
      Double marginChange,
      Double disclosedAnnualEps,
      List<String> findings,
      List<String> unknowns,
      String valuationStatus,
      Set<String> inputSources,
      List<Reference> references) {
    this(
        date,
        period,
        source,
        sourceUrl,
        cashConversion,
        marginChange,
        disclosedAnnualEps,
        findings,
        unknowns,
        valuationStatus,
        inputSources,
        references,
        null);
  }

  /**
   * 保存版本的时间与来源锚点，不冒充交易所原始公告。
   *
   * @author chenxuefeng
   * @param purpose 此材料在比较中的用途
   * @param period 累计期末
   * @param version 保存版本
   * @param publishedAt 供应商记录的公开时点
   * @param availableAt 本版本可用时点
   * @param sourceUrl 来源入口，可能返回修订后的内容
   * @param contentHash 已保存原始内容指纹
   */
  public record Reference(
      String purpose,
      LocalDate period,
      Integer version,
      LocalDateTime publishedAt,
      LocalDateTime availableAt,
      String sourceUrl,
      String contentHash) {}
}

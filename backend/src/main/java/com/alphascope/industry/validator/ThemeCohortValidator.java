package com.alphascope.industry.validator;

import com.alphascope.industry.dto.CohortDefinition;
import com.alphascope.industry.dto.CohortMember;

/**
 * 披露研究样本的来源、可用时点、完整成员及重复证券校验。
 *
 * @author chenxuefeng
 */
public final class ThemeCohortValidator {
  private ThemeCohortValidator() {}

  /**
   * 校验披露样本的来源、原文指纹、实际可用时点及成员定义，不修改输入。
   *
   * @param d 已解析的完整披露样本定义
   * @throws IllegalArgumentException 定义违反来源、时点或成员约束
   */
  public static void validate(CohortDefinition d) {
    if (!"DISCLOSED_RESEARCH_SAMPLE".equals(d.scope())
        || !d.sourceUrl().startsWith("https://static.cninfo.com.cn/finalpage/")
        || !d.pdfSha256().matches("[a-f0-9]{64}")
        || d.availableAt().isBefore(d.publishedAt().toLocalDate().plusDays(1).atStartOfDay())
        || d.verifiedAt() == null
        || d.verifiedAt().isBefore(d.publishedAt())
        || d.members().size() < 2
        || d.members().stream().map(CohortMember::symbol).distinct().count() != d.members().size()
        || d.members().stream()
            .anyMatch(m -> !m.symbol().matches("[0-9]{6}\\.(SH|SZ|BJ)") || m.name().isBlank())) {
      throw new IllegalArgumentException("无效或时点不明确的主题披露样本");
    }
  }
}

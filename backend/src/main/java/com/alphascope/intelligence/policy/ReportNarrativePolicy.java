package com.alphascope.intelligence.policy;

import com.alphascope.intelligence.dto.ReportResult;
import java.util.List;
import java.util.Objects;

/**
 * 只允许模型挑选同类别已核验完整原句，数字、证券、期间和单位不能重新组合。
 *
 * @author chenxuefeng
 */
public final class ReportNarrativePolicy {
  private ReportNarrativePolicy() {}

  /**
   * 核对模型摘要是否仅重排已核验陈述；不依赖字符串数字白名单证明语义。
   *
   * @param result 模型输出
   * @param facts 服务端根据冻结证据生成的规则陈述
   * @return 分类与完整原句均一致时为true
   */
  public static boolean matches(ReportResult result, ReportResult facts) {
    return result != null
        && facts != null
        && Objects.equals(result.summary(), facts.summary())
        && subset(result.positiveFactors(), facts.positiveFactors())
        && Objects.equals(result.negativeFactors(), facts.negativeFactors())
        && subset(result.themeOutlook(), facts.themeOutlook())
        && Objects.equals(result.watchlistRisks(), facts.watchlistRisks())
        && subset(result.nextChecks(), facts.nextChecks())
        && Objects.equals(result.uncertainties(), facts.uncertainties());
  }

  private static boolean subset(List<String> chosen, List<String> allowed) {
    return chosen != null && allowed != null && allowed.containsAll(chosen);
  }
}

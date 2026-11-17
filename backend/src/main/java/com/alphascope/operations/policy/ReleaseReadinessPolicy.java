package com.alphascope.operations.policy;

import com.alphascope.operations.vo.SourceLicenseView;

/**
 * 发布待办的中文解释，与当前运行故障独立。
 *
 * @author chenxuefeng
 */
public final class ReleaseReadinessPolicy {
  private ReleaseReadinessPolicy() {}

  /**
   * 将真实来源的登记状态转换为发布前待办，不改变实际许可准入。
   *
   * @param source 真实许可状态
   * @return 可由管理员理解的待办
   */
  public static String licenseIssue(SourceLicenseView source) {
    String name =
        switch (source.source()) {
          case "EXCHANGE_CALENDAR" -> "交易所公告日历";
          case "BAOSTOCK" -> "BaoStock";
          case "SINA" -> "新浪财经";
          case "EASTMONEY" -> "东方财富";
          case "EASTMONEY_DIRECTORY" -> "东方财富证券目录";
          case "EASTMONEY_FINANCIAL" -> "东方财富财报";
          case "EASTMONEY_FORECAST" -> "东方财富机构预测";
          default -> "其他已配置来源";
        };
    String issue =
        switch (source.status()) {
          case "MISSING" -> "尚未登记实际使用依据";
          case "EXPIRED" -> "登记已过期，需重新核对有效依据";
          case "NOT_YET_VALID" -> "登记尚未生效";
          default -> "登记状态需核对";
        };
    return name + "：" + issue;
  }
}

package com.alphascope.intelligence.vo;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 独立来源的股本、TTM盈利与同行价格检查；结论是资料核对，不是合理价值认证。
 *
 * @author chenxuefeng
 * @param version 固定核验方法版本
 * @param date 截止日
 * @param status NEEDS_INPUT、INCOMPLETE、INCONSISTENT或CHECKED_WITH_LIMITS
 * @param observedTtmEps 同口径滚动归母利润除经核对当前股数
 * @param peerPeLowerQuartile 有效同行TTM市盈率下四分位
 * @param conservativeCap 受已观察盈利与同行约束的折安全边际上限，不自动等于买点
 * @param checks 逐项核验及失败原因
 * @param peers 每个同行的实际数值、排除和来源
 * @param limitations 方法和人工同行选择边界
 * @param inputSources 本次实际材料来源
 */
public record ValuationAuditView(
    String version,
    LocalDate date,
    String status,
    Double observedTtmEps,
    Double peerPeLowerQuartile,
    Double conservativeCap,
    List<Check> checks,
    List<Peer> peers,
    List<String> limitations,
    Set<String> inputSources) {
  /**
   * 三态证据核对，null表示尚不能核验。
   *
   * @author chenxuefeng
   * @param label 项目
   * @param observed 实际事实或缺项
   * @param passed true通过数值核对、false不一致、null缺资料
   */
  public record Check(String label, String observed, Boolean passed) {}

  /**
   * 同行的真实财报和同日行情，不读取用户填写的PE。
   *
   * @author chenxuefeng
   * @param symbol 标的
   * @param pe TTM归母口径市盈率，无法核验时为空
   * @param status CHECKED或EXCLUDED
   * @param reason 核验依据或排除原因
   * @param financialUrl 财报实际来源入口
   * @param comparabilityUrl 用户选择同行的公开依据
   */
  public record Peer(
      String symbol,
      Double pe,
      String status,
      String reason,
      String financialUrl,
      String comparabilityUrl) {}
}

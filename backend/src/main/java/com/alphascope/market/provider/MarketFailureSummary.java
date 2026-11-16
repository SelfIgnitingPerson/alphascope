package com.alphascope.market.provider;

import com.alphascope.common.diagnostic.FailureDiagnosis;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * 将已保存的行情日期证据转成用户说明；不把缺日推断为网络故障或供应商尚未发布。
 *
 * @author chenxuefeng
 */
public final class MarketFailureSummary {
  private MarketFailureSummary() {}

  /**
   * 描述本地缺日和实际补采结果，不对已有日期进行替换。
   *
   * @param evidence 从当前缓存、交易日历和受控异常分类取得的证据
   * @return 不包含技术错误码的主提示
   */
  public static String describe(Evidence evidence) {
    String stock =
        evidence.name() == null || evidence.name().isBlank()
            ? evidence.symbol()
            : evidence.name() + "（" + evidence.symbol() + "）";
    String missing =
        evidence.missingDates().stream()
            .sorted()
            .limit(3)
            .map(LocalDate::toString)
            .collect(java.util.stream.Collectors.joining("、"));
    String problem =
        evidence.missingDates().isEmpty()
            ? "本地已有所选日期的行情，但本次刷新或完整性核验没有通过。"
            : "缺少"
                + missing
                + (evidence.missingDates().size() > 3
                    ? "等" + evidence.missingDates().size() + "个交易日"
                    : "的")
                + "收盘行情。";
    String saved =
        evidence.latestDate() == null ? "本地暂无此窗口的行情。" : "本地已保存到" + evidence.latestDate() + "。";
    String cause =
        evidence.diagnostic() == null
            ? "补采未成功，现有记录不足以确定具体原因。"
            : "补采结果：" + evidence.diagnostic().reason();
    String action =
        evidence.missingDates().isEmpty()
            ? "已有数据保留；请核对数据来源或刷新失败原因后再试。"
            : "本次报告未生成，已有历史数据保留；请补齐上述日期后再试，或明确选择较早的研究日期。";
    return stock + problem + saved + cause + " " + action;
  }

  /**
   * 仅携带已核实日期与来源分类的展示证据。
   *
   * @author chenxuefeng
   */
  @Builder
  public record Evidence(
      /** 标准证券代码。 */
      String symbol,
      /** 已登记证券名称。 */
      String name,
      /** 当前请求窗口中本地最新日期，不等于已生成报告日期。 */
      LocalDate latestDate,
      /** 日历要求而当前本地行情缺失的日期，不含已证实停牌。 */
      List<LocalDate> missingDates,
      /** 本次补采的受控原因，不含供应商正文。 */
      FailureDiagnosis diagnostic) {}
}

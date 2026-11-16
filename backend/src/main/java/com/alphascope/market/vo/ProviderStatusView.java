package com.alphascope.market.vo;

import com.alphascope.market.dto.MarketSourceDO;
import java.util.List;

/**
 * 行情通道配置及已保存来源；GET 不触发采集。
 *
 * @author chenxuefeng
 */
public record ProviderStatusView(
    /** 当前通道组合模式。 */
    String mode,

    /** 运行配置的数据源范围。 */
    String dataScope,

    /** 是否为演示配置。 */
    boolean isSynthetic,

    /** 本进程最近取得交易日历的通道。 */
    String calendarSource,

    /** 按优先级排列的通道状态。 */
    List<ProviderChannelView> channels,

    /** 各标的已保存的实际行情来源与时间。 */
    List<MarketSourceDO> latestData,
    /** 未进入当前路由的通道及配置原因，不将其标成访问受限。 */
    List<InactiveChannel> inactiveChannels) {

  /**
   * 未启用通道的配置说明；不包含凭据，也不推断账号实际权限。
   *
   * @author chenxuefeng
   */
  public record InactiveChannel(
      /** 通道代码。 */
      String channel,
      /** NOT_CONFIGURED为缺少必要配置，DISABLED为未选择。 */
      String state,
      /** 未参与路由的实际配置原因，不推断上游拒绝。 */
      String reason,
      /** 配置及验证步骤。 */
      String action) {}
}

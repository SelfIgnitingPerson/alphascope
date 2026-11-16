package com.alphascope.market.provider.research;

import com.alphascope.market.dto.ResearchFactorRequest;
import com.alphascope.market.dto.ResearchFactorWindow;
import com.alphascope.market.dto.ResearchMarketSnapshot;
import com.alphascope.market.provider.MarketCapability;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * 全市场截面和复权的供应商能力契约；不支持的能力不能假装成功或回退演示。
 *
 * @author chenxuefeng
 */
public interface ResearchDataProvider extends AutoCloseable {
  /**
   * 返回该能力适配器的实际来源代码。
   *
   * @return BAOSTOCK或TUSHARE等具体代码
   */
  String code();

  /**
   * 读取实际依赖的数据来源；跨目录/行情源时分别检查许可，不能只检查报价源。
   *
   * @return 收集该能力必须具备权限的所有来源
   */
  default Set<String> requiredSources() {
    return Set.of(code());
  }

  /**
   * 说明是否能远程查询历史截面；false时只能读取已归档历史，不能用当前目录倒填。
   *
   * @return 是否支持远端历史查询
   */
  default boolean historicalSnapshots() {
    return true;
  }

  /**
   * 读取上游明确限流后的访问冷却终点，不发起探测。
   *
   * @return 冷却终点或null；过期标记不阻止后续受控访问
   */
  default Instant blockedUntil() {
    return null;
  }

  /**
   * 返回实现支持的研究能力，不代表网络或权限已验证。
   *
   * @return 不含日线/日历能力的研究能力集合
   */
  Set<MarketCapability> capabilities();

  /**
   * 获取有来源说明的证券清单和截面，独立目录必须保留来源及观测时刻；实际覆盖率由计算层核对。
   *
   * @param date 目标交易日期
   * @return 原始来源、标准化协议及上游原文
   */
  ResearchMarketSnapshot snapshot(LocalDate date);

  /**
   * 获取指定来源和覆盖范围的真实复权输入。
   *
   * @param request 证券、日期范围和与原价一致的来源
   * @return 已核验日期范围的因子和原文
   */
  ResearchFactorWindow factors(ResearchFactorRequest request);

  /** 释放供应商会话，默认无持有资源。 */
  @Override
  default void close() {}
}

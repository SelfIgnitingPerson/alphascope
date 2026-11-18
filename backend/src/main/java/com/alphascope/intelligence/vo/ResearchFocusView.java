package com.alphascope.intelligence.vo;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * 自选研究的当日优先事项；只比较已保存版本，不构成交易或历史可得性证明。
 *
 * @author chenxuefeng
 * @param asOf 请求的研究日
 * @param items 本页核对结果，按风险与变化排序
 * @param total 启用自选总数；不能把本页当作全部自选
 * @param page 当前页码
 * @param size 按自选优先级划分的每页数量
 */
public record ResearchFocusView(LocalDate asOf, List<Item> items, int total, int page, int size) {
  /**
   * 单只证券的一项首要研究事项，其他周期可从个股页继续核对。
   *
   * @author chenxuefeng
   * @param symbol 统一代码
   * @param name 证券名称
   * @param category RISK、MATCH、DATA_GAP、CHANGED或WAIT
   * @param title 可直接理解的事项标题
   * @param explanation 具体周期、方案及对照限制
   * @param horizon 首要事项的周期，资料缺失时为空
   * @param quoteDate 实际行情日，不用请求日替换旧行情
   * @param comparisonDate 已尝试核对的上一证券交易日；日历缺失时为空
   * @param newlyMatched 是否已用同方法、同源且完整上期对照确认条件新满足
   * @param changeLabel 与上期的可比变化，不把持续信号称为新增
   * @param priorities 首项为当前核对重点，其他项保留不同周期的风险与观察依据
   */
  @Builder
  public record Item(
      String symbol,
      String name,
      String category,
      String title,
      String explanation,
      String horizon,
      LocalDate quoteDate,
      LocalDate comparisonDate,
      boolean newlyMatched,
      String changeLabel,
      List<ResearchPriority> priorities) {}
}

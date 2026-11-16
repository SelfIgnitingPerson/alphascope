package com.alphascope.indicator.service;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.indicator.entity.IndicatorSnapshot;
import java.time.LocalDate;
import java.util.List;

/**
 * 批量加载同源历史行情与基准，采用统一复权锚点计算各交易日指标。
 *
 * @author chenxuefeng
 */
public interface IndicatorService {

  /**
   * 读取已保存的技术指标，按当前真实/演示模式及交易日区间隔离，不触发计算。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param request 必填的开始、结束日期闭区间
   * @return 交易日升序指标列表；没有记录时为空列表
   */
  List<IndicatorSnapshot> list(String symbol, DateRangeRequest request);

  /**
   * 按分析日因子统一前复权后计算并保存当日指标；缺少有效当日行情时拒绝计算。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param date 研究或记录对应的业务日期，上海时区
   */
  void calculate(String symbol, LocalDate date);

  /**
   * 批量加载同源证券及基准历史，在内存按交易日分窗计算区间指标，避免逐日重复查询。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param start 请求区间起始日期，包含当日
   * @param end 请求区间结束日期，包含当日
   */
  void calculateRange(String symbol, LocalDate start, LocalDate end);
}

package com.alphascope.market.service;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.market.vo.MarketOverviewView;
import com.alphascope.market.vo.MarketScoreView;
import com.alphascope.market.vo.ScopedScoreView;
import java.time.LocalDate;
import java.util.List;

/**
 * 独立展示沪深观察事实；全A范围或五维规则未满足时不臆造综合分数。
 *
 * @author chenxuefeng
 */
public interface ScoreService {

  /**
   * 保存当前业务日全市场五维评级及缺项证据；沪深独立观察不被放大为完整全 A 综合分。
   *
   * @param date 研究或记录对应的业务日期，上海时区
   */
  void calculate(LocalDate date);

  /**
   * 读取指定闭区间及当前数据模式的已保存评级，不触发计算。
   *
   * @param request 必填的开始、结束日期闭区间
   * @return 按交易日升序排列的评分及证据列表
   */
  List<MarketScoreView> history(DateRangeRequest request);

  /**
   * 只读组合业务日评级、独立沪深截面和最近分项观察，分别保留范围及可用状态。
   *
   * @param date 研究或记录对应的业务日期，上海时区
   * @return 明确区分完整评级与部分观察事实的市场概览
   */
  MarketOverviewView overview(LocalDate date);

  /**
   * 根据已保存同日证据只读计算有明确公式的五维分项，不触发上游请求。
   *
   * @param date 业务日期
   * @return 代理范围、缺项和完整范围总分准入结果
   */
  ScopedScoreView research(LocalDate date);
}

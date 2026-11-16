package com.alphascope.industry.service;

import com.alphascope.common.dto.OptionalDateRangeRequest;
import com.alphascope.industry.dto.DivergenceEvidence;
import com.alphascope.industry.dto.ThemeContext;
import com.alphascope.industry.entity.Theme;
import com.alphascope.industry.vo.ThemeStrengthView;
import java.time.LocalDate;
import java.util.List;

/**
 * 按有效成分计算等权主题收益、均线广度及多成员连续三日证据。
 *
 * @author chenxuefeng
 */
public interface IndustryService {

  /**
   * 读取研究主题字典，不触发采集或计算。
   *
   * @return 已配置主题列表；没有主题时为空列表
   */
  List<Theme> themes();

  /**
   * 读取主题历史评分、当前有效成员、样本来源和覆盖边界。
   *
   * @param code 主题稳定业务代码，例如 PCB
   * @param request 可选日期区间；省略时为截止日期之前三个月
   * @return 明确区分披露样本与人工归属的主题强弱结果
   */
  ThemeStrengthView strength(String code, OptionalDateRangeRequest request);

  /**
   * 按实际交易日历计算指定区间内的主题等权收益、广度和覆盖率。
   *
   * @param start 请求区间起始日期，包含当日
   * @param end 请求区间结束日期，包含当日
   */
  void calculateRange(LocalDate start, LocalDate end);

  /**
   * 整理并返回报告业务日可见的披露样本定义、成员、历史行情及评分，供生成时冻结。
   *
   * @param date 研究或记录对应的业务日期，上海时区
   * @return 完整主题证据列表；演示模式或没有可见披露样本时为空列表
   */
  List<ThemeContext> snapshot(LocalDate date);

  /**
   * 按业务日真实有效成员计算并保存主题收益、均线广度及覆盖证据，覆盖不足时保留空评分。
   *
   * @param date 研究或记录对应的业务日期，上海时区
   */
  void calculate(LocalDate date);

  /**
   * 核对同一主题至少两名有效成员及最近三个实际交易日的连续证据，不跨主题拼接。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param date 研究或记录对应的业务日期，上海时区
   * @return 可用分化规则数值及逐主题诊断；证据不足时数值字典为空并说明缺项
   */
  DivergenceEvidence divergenceEvidence(String symbol, LocalDate date);
}

package com.alphascope.backtest.service;

import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.vo.ExperimentReplayView;
import com.alphascope.backtest.vo.ExperimentView;
import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.response.PageResponse;

/**
 * 保存和复核不可覆盖的历史研究实验，不访问行情上游。
 *
 * @author chenxuefeng
 */
public interface ExperimentService {
  /**
   * 显式保存完整输入、输出及方法指纹，查询接口不会自动产生实验。
   *
   * @param request 日期、证券和方法条件
   * @return 新实验详情
   */
  ExperimentView save(SignalStatisticsRequest request);

  /**
   * 分页查询冻结实验元数据。
   *
   * @param request 分页条件
   * @return 统一分页
   */
  PageResponse<ExperimentView> page(PageRequest request);

  /**
   * 读取原实验结果并校验冻结完整性。
   *
   * @param id 实验主键
   * @return 原实验结果
   */
  ExperimentView get(long id);

  /**
   * 仅用冻结输入重算，规则或数据库当前值变化不影响实验。
   *
   * @param id 实验主键
   * @return 输入与结果匹配状态
   */
  ExperimentReplayView reproduce(long id);
}

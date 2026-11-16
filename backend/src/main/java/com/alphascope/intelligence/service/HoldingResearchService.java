package com.alphascope.intelligence.service;

import com.alphascope.intelligence.dto.HoldingResearchQuery;
import com.alphascope.intelligence.vo.HoldingResearchView;

/**
 * 个股多周期研究入口，只读取已有事实，不采集行情或写入持仓。
 *
 * @author chenxuefeng
 */
public interface HoldingResearchService {
  /**
   * 按精确日期生成可解释的短、中、长期行动计划。
   *
   * @param symbol 已登记证券代码
   * @param query 截止日期对象
   * @return 带价位、条件、证据和实际缺项的研究视图
   */
  HoldingResearchView read(String symbol, HoldingResearchQuery query);
}

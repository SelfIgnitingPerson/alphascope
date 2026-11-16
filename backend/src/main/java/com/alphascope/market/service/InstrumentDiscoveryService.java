package com.alphascope.market.service;

import com.alphascope.market.vo.InstrumentCandidate;
import com.alphascope.market.vo.InstrumentDiscoveryView;

/**
 * 面向新用户的证券发现、校验和登记入口。
 *
 * @author chenxuefeng
 */
public interface InstrumentDiscoveryService {
  /**
   * 合并本地和公开目录结果。
   *
   * @param keyword 至少2字符的代码或名称
   * @return 候选及真实来源状态
   */
  InstrumentDiscoveryView search(String keyword);

  /**
   * 从服务器来源复核并登记准确股票。
   *
   * @param symbol 标准代码
   * @return 已登记且有效的候选
   */
  InstrumentCandidate register(String symbol);
}

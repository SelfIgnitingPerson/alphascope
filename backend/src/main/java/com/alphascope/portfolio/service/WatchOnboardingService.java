package com.alphascope.portfolio.service;

import com.alphascope.portfolio.dto.WatchDiscoveryInput;
import com.alphascope.portfolio.vo.WatchDiscoveryResult;

/**
 * 新用户自选与初始化编排，网络目录核验不在写事务内执行。
 *
 * @author chenxuefeng
 */
public interface WatchOnboardingService {
  /**
   * 校验证券、加入自选并按选择提交首次研究。
   *
   * @param input 代码、日期及初始化选择
   * @return 添加结果与独立初始化状态
   */
  WatchDiscoveryResult add(WatchDiscoveryInput input);
}

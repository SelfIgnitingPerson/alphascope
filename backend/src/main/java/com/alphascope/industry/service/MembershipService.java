package com.alphascope.industry.service;

import com.alphascope.industry.dto.MembershipInput;

/**
 * 校验标的、主题与起止日期后登记人工归属，不从当前名单推造历史。
 *
 * @author chenxuefeng
 */
public interface MembershipService {

  /**
   * 校验证券、主题及真实有效日期后登记人工主题归属，不倒填历史成分。
   *
   * @param input 标的代码、主题代码、真实生效日期及可空结束日期
   * @throws IllegalArgumentException 结束日期早于开始日期或主题不存在
   */
  void add(MembershipInput input);
}

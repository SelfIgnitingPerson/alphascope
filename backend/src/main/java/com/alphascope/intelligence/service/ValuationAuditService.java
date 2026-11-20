package com.alphascope.intelligence.service;

import com.alphascope.fundamental.dto.EvidenceQuery;
import com.alphascope.intelligence.vo.ValuationAuditView;

/**
 * 独立读取真实材料进行估值核对，不读取客户端PE作为同行事实。
 *
 * @author chenxuefeng
 */
public interface ValuationAuditService {
  /**
   * 检查实际股本、TTM、同行和保守上限，全部缺项可定位。
   *
   * @param query 证券和截止日
   * @return 逐项核验
   */
  ValuationAuditView read(EvidenceQuery query);
}

package com.alphascope.intelligence.service;

import com.alphascope.fundamental.dto.EvidenceQuery;
import com.alphascope.intelligence.vo.BusinessEvidenceView;

/**
 * 当前用户的经营和估值独立核对。
 *
 * @author chenxuefeng
 */
public interface BusinessEvidenceService {
  /**
   * 读取截止日前的公开证据，不发起采集。
   *
   * @param query 证券和截止日期
   * @return 证据、反证及未核验项目
   */
  BusinessEvidenceView read(EvidenceQuery query);
}

package com.alphascope.intelligence.service;

import com.alphascope.intelligence.entity.ResearchObservation;
import com.alphascope.intelligence.vo.ObservationCaptureView;
import java.time.LocalDate;

/**
 * 实际判断留档与当时可见性服务。
 *
 * @author chenxuefeng
 */
public interface ResearchObservationService {
  /**
   * 显式保存当前账号关注与持仓研究，历史回看明确标记。
   *
   * @param date 业务日
   * @return 留档结果
   */
  ObservationCaptureView capture(LocalDate date);

  /**
   * 读取上一研究日真实形成的判断，不能用当前重算替代。
   *
   * @param query 证券、上期日期及当前研究日零时
   * @return 不可变原判断或null
   */
  ResearchObservation previous(com.alphascope.intelligence.dto.ObservationQuery query);
}

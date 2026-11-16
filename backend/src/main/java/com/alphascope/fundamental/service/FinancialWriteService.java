package com.alphascope.fundamental.service;

import com.alphascope.fundamental.dto.FinancialInput;

/**
 * 校验财报标的、报告期、版本和公开来源，在事务中新增累计财报。
 *
 * @author chenxuefeng
 */
public interface FinancialWriteService {

  /**
   * 校验标的与公开时点，在同一事务内锁定证券并新增手工累计财报。
   *
   * <p>同版本只能新增一次，不能覆盖历史；金额单位为人民币元，比例使用小数，空财务值表示缺证据。
   *
   * @param input 标的、累计报告期、版本、金额/比例及可核验公开来源
   * @throws IllegalArgumentException 报告期末晚于披露日期
   * @throws org.springframework.dao.DuplicateKeyException 同标的、报告期、口径及版本已经存在
   */
  void insert(FinancialInput input);
}

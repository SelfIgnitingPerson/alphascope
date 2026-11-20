package com.alphascope.fundamental.service;

import com.alphascope.fundamental.dto.CorporateActionInput;
import com.alphascope.fundamental.dto.EvidenceQuery;
import com.alphascope.fundamental.dto.ValuationThesisInput;
import java.util.List;

/**
 * 有来源的公司行动及用户估值情景，不调用交易接口或推断缺失股本。
 *
 * @author chenxuefeng
 */
public interface InvestmentEvidenceService {
  /**
   * 登记共享公开行动。
   *
   * @param input 已核对的公告与因子
   */
  void saveAction(CorporateActionInput input);

  /**
   * 冻结当前用户的估值假设。
   *
   * @param input 用户明确输入的情景
   */
  void saveThesis(ValuationThesisInput input);

  /**
   * 读取真实已登记行动。
   *
   * @param query 股票和截止日
   * @return 不可变版本列表
   */
  List<CorporateActionInput> actions(EvidenceQuery query);

  /**
   * 读取当前用户估值情景。
   *
   * @param query 股票和截止日
   * @return 情景或null
   */
  ValuationThesisInput thesis(EvidenceQuery query);

  /**
   * 读取当日研究截止前已经实际登记的有效估值情景。
   *
   * @param query 股票和截止日
   * @return 历史情景或null
   */
  ValuationThesisInput thesisAtClose(EvidenceQuery query);
}

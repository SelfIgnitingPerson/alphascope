package com.alphascope.fundamental.mapper;

import com.alphascope.fundamental.dto.EvidenceQuery;
import com.alphascope.fundamental.entity.ResearchEvidenceRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 共享公司行动和私人估值假设分表存储，不允许客户端传入租户或动态表名。
 *
 * @author chenxuefeng
 */
@Mapper
public interface InvestmentEvidenceMapper {
  /**
   * 保存公开行动版本。
   *
   * @param row 可追溯证据
   */
  void saveAction(ResearchEvidenceRecord row);

  /**
   * 保存私人估值情景。
   *
   * @param row 用户确认的依据
   */
  void saveThesis(ResearchEvidenceRecord row);

  /**
   * 读取截止日前各除权日的最近版本。
   *
   * @param query 证券和截止日
   * @return 公开行动记录
   */
  List<ResearchEvidenceRecord> actions(EvidenceQuery query);

  /**
   * 读取当前用户截止日前最近估值情景。
   *
   * @param query 证券和截止日
   * @return 情景或null
   */
  ResearchEvidenceRecord thesis(EvidenceQuery query);

  /**
   * 选择当日截止时已经登记的估值版本，避免把事后登记写成历史依据。
   *
   * @param query 证券和研究日
   * @return 当时实际留档的估值情景或null
   */
  ResearchEvidenceRecord thesisAtClose(EvidenceQuery query);
}

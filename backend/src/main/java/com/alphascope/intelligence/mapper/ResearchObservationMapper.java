package com.alphascope.intelligence.mapper;

import com.alphascope.intelligence.dto.ObservationQuery;
import com.alphascope.intelligence.entity.ResearchObservation;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 私人每日判断留档，只追加、不覆盖。
 *
 * @author chenxuefeng
 */
@Mapper
public interface ResearchObservationMapper {
  /**
   * 幂等追加实际观察。
   *
   * @param row 服务端冻结正文
   * @return 新增行数
   */
  int save(ResearchObservation row);

  /**
   * 读取准确上期且当时已实际形成的最后修订。
   *
   * @param query 业务日与实际形成截止时点
   * @return 原判断或null
   */
  ResearchObservation previous(ObservationQuery query);

  /**
   * 先按证券与计划保存时间筛选，再按编号倒序读取一批观察。
   *
   * @param query 截止范围
   * @return 倒序记录，包含事后修订供服务端识别
   */
  List<ResearchObservation> forward(ObservationQuery query);

  /**
   * 当前用户记录总量，保护长期积累容量。
   *
   * @return 已保存行数
   */
  long count();
}

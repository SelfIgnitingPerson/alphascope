package com.alphascope.planning.mapper;

import com.alphascope.planning.dto.PlanQuery;
import com.alphascope.planning.dto.PlanReviewQuery;
import com.alphascope.planning.entity.PlanReview;
import com.alphascope.planning.entity.ResearchPlan;
import com.alphascope.planning.vo.PlanSummary;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户计划与追加复核，所有SQL经租户隔离；无证据覆盖或删除方法。
 *
 * @author chenxuefeng
 */
@Mapper
public interface ResearchPlanMapper {
  /**
   * 保存一次不可变计划并回填主键。
   *
   * @param row 服务端捕获的完整研究
   */
  void insert(ResearchPlan row);

  /**
   * 按所属用户筛选分页读取摘要。
   *
   * @param query 校验后的筛选与分页
   * @return 摘要列表
   */
  List<PlanSummary> page(PlanQuery query);

  /**
   * 统计所属用户筛选结果。
   *
   * @param query 筛选条件
   * @return 匹配记录数
   */
  long count(PlanQuery query);

  /**
   * 读取当前用户计划，其他用户的主键视为不存在。
   *
   * @param id 计划主键
   * @return 计划或null
   */
  ResearchPlan get(long id);

  /**
   * 归档当前用户计划，不删除冻结内容。
   *
   * @param id 计划主键
   * @return 影响行数
   */
  int archive(long id);

  /**
   * 追加不可变复核记录并回填主键。
   *
   * @param row 服务端计算的复核证据
   */
  void review(PlanReview row);

  /**
   * 分页读取所属用户及计划的全部复核。
   *
   * @param query 已确认归属的计划与分页
   * @return 倒序复核记录
   */
  List<PlanReview> reviews(PlanReviewQuery query);

  /**
   * 统计所属用户计划的复核。
   *
   * @param id 计划主键
   * @return 复核总数
   */
  long reviewCount(long id);

  /**
   * 锁住当前身份账号，写事务结束后释放；不能由客户端指定用户。
   *
   * @param id 已验证的服务端用户主键
   * @return 被锁定主键
   */
  Long lockUser(long id);

  /**
   * 统计当前用户保存的计划与复核数量，保护长期存储预算。
   *
   * @return 总记录数
   */
  long evidenceCount();
}

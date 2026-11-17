package com.alphascope.planning.service;

import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.response.PageResponse;
import com.alphascope.planning.dto.PlanCreateInput;
import com.alphascope.planning.dto.PlanQuery;
import com.alphascope.planning.dto.PlanReviewInput;
import com.alphascope.planning.vo.PlanReviewView;
import com.alphascope.planning.vo.PlanSummary;
import com.alphascope.planning.vo.PlanView;

/**
 * 从实际研究保存个人计划，并显式追加复核。
 *
 * @author chenxuefeng
 */
public interface ResearchPlanService {
  /**
   * 捕获服务端研究，拒绝演示及无有效当日行情的计划。
   *
   * @param input 证券、周期和行动标识
   * @return 保存后的计划
   */
  PlanView create(PlanCreateInput input);

  /**
   * 读取个人计划，不触发采集或复核写入。
   *
   * @param query 筛选与分页
   * @return 摘要分页
   */
  PageResponse<PlanSummary> page(PlanQuery query);

  /**
   * 按当前身份读取完整冻结详情。
   *
   * @param id 计划主键
   * @return 原始计划
   */
  PlanView get(long id);

  /**
   * 追加当前日期复核，不改变原计划价位。
   *
   * @param id 当前用户计划主键
   * @param input 明确日期
   * @return 本次复核
   */
  PlanReviewView review(long id, PlanReviewInput input);

  /**
   * 读取已保存复核，不重算历史结果。
   *
   * @param id 当前用户计划主键
   * @param page 分页参数
   * @return 不可变结果分页
   */
  PageResponse<PlanReviewView> reviews(long id, PageRequest page);

  /**
   * 归档当前用户计划，保留原始证据。
   *
   * @param id 计划主键
   */
  void archive(long id);
}

package com.alphascope.planning.service;

import com.alphascope.planning.vo.PlanFollowupView;
import com.alphascope.planning.vo.PlanSummary;
import java.time.LocalDate;
import java.util.List;

/**
 * 从已保存资料批量跟踪当前账号计划，不修改冻结价位、不发送交易指令。
 *
 * @author chenxuefeng
 */
public interface PlanFollowupService {
  /**
   * 每批最多复核100份到期计划，已具备当日有效复核的不重复写入。
   *
   * @param date 收盘研究日
   * @return 本批结果与剩余待办
   */
  PlanFollowupView review(LocalDate date);

  /**
   * 后台收盘任务覆盖全部跟踪计划，独立事务逐项保存，不受HTTP单批100份限制。
   *
   * @param date 已完成的收盘研究日
   * @return 全量跟踪结果及仍缺资料的事项
   */
  PlanFollowupView reviewAll(LocalDate date);

  /**
   * 汇总当前账号全部跟踪计划的待办，排序后仅返回前五项。
   *
   * @param date 截止研究日
   * @return 全局数量及优先事项
   */
  com.alphascope.planning.vo.PlanTodoView todos(LocalDate date);

  /**
   * 返回跟踪中计划的原风险线触及事实，用于合并个股与组合风险后统一排序。
   *
   * @param date 截止研究日
   * @return 截止该日已复核触及原风险线的计划，阅读标记保留供展示
   */
  List<PlanSummary> originalRiskAlerts(LocalDate date);
}

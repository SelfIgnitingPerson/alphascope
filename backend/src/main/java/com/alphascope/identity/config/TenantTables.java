package com.alphascope.identity.config;

import java.util.Set;

/**
 * 公共行情与私人研究的唯一数据库边界清单；未知表默认不得被当成公共表绕过隔离。
 *
 * @author chenxuefeng
 */
public final class TenantTables {
  /** 每位用户独立存储的研究与任务表。 */
  public static final Set<String> PRIVATE =
      Set.of(
          "watchlist",
          "research_plan",
          "research_plan_review",
          "portfolio_account",
          "portfolio_position",
          "indicator_snapshot",
          "signal_event",
          "signal_revision",
          "theme_score",
          "market_score",
          "analysis_report",
          "report_input_snapshot",
          "report_source",
          "notification_log",
          "job_execution",
          "job_stage",
          "job_attempt_history",
          "validation_experiment",
          "research_validation_task",
          "service_alert");

  /** 可由不同用户共享的公开基础数据；人工写入口仅管理员可调用。 */
  public static final Set<String> SHARED =
      Set.of(
          "instrument",
          "theme_leader_revision",
          "adjustment_factor",
          "daily_bar",
          "daily_bar_audit",
          "instrument_day_status",
          "market_data_revision",
          "market_factor_snapshot",
          "trading_calendar",
          "market_breadth_snapshot",
          "market_universe_snapshot",
          "market_observation_snapshot",
          "earnings_forecast_snapshot",
          "financial_report",
          "research_collection",
          "theme",
          "theme_research_cohort",
          "theme_research_member",
          "source_document",
          "industry_metric",
          "instrument_theme");

  /** 身份及运维控制表通过专用Service校验，不接受用户指定范围。 */
  public static final Set<String> CONTROL =
      Set.of(
          "app_user",
          "auth_session",
          "auth_event",
          "source_license",
          "storage_budget",
          "flyway_schema_history");

  private TenantTables() {}
}

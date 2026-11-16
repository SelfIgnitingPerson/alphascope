package com.alphascope.operations.vo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 运维总览：备份、第二存储、权威日期与标的时效分别展示，GET不触发采集。
 *
 * @author chenxuefeng
 */
public record OperationsStatusView(
    /** 当前核查时刻。 */
    Instant checkedAt,
    /** HEALTHY、ATTENTION或UNAVAILABLE。 */
    String status,
    /** 已确认应有的最近收盘日期；日历不足为null。 */
    LocalDate expectedTradeDate,
    /** AVAILABLE或UNKNOWN_CALENDAR。 */
    String calendarStatus,
    /** 定时备份是否启用。 */
    boolean scheduledBackupEnabled,
    /** 上海时区备份计划。 */
    String backupCron,
    /** 最后实际恢复验证的本机备份。 */
    BackupHealth backup,
    /** 最近备份尝试及失败原因。 */
    BackupRunStatus latestAttempt,
    /** 第二存储复制情况。 */
    SecondaryBackupStatus secondary,
    /** 自选及基准的行情时效，不由服务健康UP替代。 */
    List<Freshness> instruments,
    /** 需要处理的明确事项。 */
    List<String> issues) {
  /**
   * 最后已验证备份的完整性与计划时效。
   *
   * @author chenxuefeng
   */
  public record BackupHealth(
      /** AVAILABLE、STALE、MISSING或INVALID。 */
      String status,
      /** 文件名。 */
      String fileName,
      /** 实际完成时刻。 */
      Instant verifiedAt,
      /** 按计划应执行的下一次时间。 */
      Instant nextDue,
      /** 原始压缩文件SHA256。 */
      String sha256) {}

  /**
   * 单个必需标的的业务日期新鲜度。
   *
   * @author chenxuefeng
   */
  public record Freshness(
      /** 证券代码。 */
      String symbol,
      /** FRESH、STALE、MISSING、UNKNOWN_CALENDAR或NOT_APPLICABLE。 */
      String status,
      /** 实际行情日期。 */
      LocalDate tradeDate,
      /** 实际采集时刻。 */
      LocalDateTime fetchedAt,
      /** 实际来源。 */
      String source) {}
}

package com.alphascope.scheduler.vo;

import com.alphascope.common.util.Jsons;
import com.alphascope.scheduler.dto.StageDetails;
import com.alphascope.scheduler.entity.JobExecution;
import com.alphascope.scheduler.entity.JobStage;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 持久研究任务的一次执行尝试，保留心跳、父重试及各阶段结果。
 *
 * @author chenxuefeng
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record JobView(
    /** 当前资源记录的数据库主键。 */
    long id,

    /** 研究任务类型：DAILY日报，WEEKLY周报。 */
    String jobName,

    /** 运行配置的数据源范围，例如DEMO或REAL:BAOSTOCK,SINA，不等于单条证据来源。 */
    String dataScope,

    /** 本次研究采用的业务日期，使用上海时区。 */
    LocalDate businessDate,

    /** 同任务类型和业务日期的尝试序号，从1递增。 */
    int attempt,

    /** 任务执行状态；包含PENDING/RUNNING、SUCCESS/SKIPPED/FAILED/INTERRUPTED及取消、超时的请求态和终态。 */
    String status,

    /** 失败、中断或跳过的具体原因；正常执行时可为空。 */
    String error,

    /** 本次任务或阶段开始时刻，上海时区。 */
    LocalDateTime startedAt,

    /** 任务或阶段结束时刻；未结束时可为空。 */
    LocalDateTime endedAt,

    /** 工作进程最近心跳时间，用于识别失联任务。 */
    LocalDateTime heartbeatAt,

    /** 被重试的原任务ID；首次提交时为空。 */
    Long retryOf,

    /** 计算所采用完整输入的SHA-256指纹。 */
    String inputHash,

    /** 当前任务已经保存的各阶段状态及时间记录。 */
    List<Stage> stages,
    /** 独立数据质量；旧任务没有记录时为空，不自动解释为完整。 */
    String dataStatus) {
  /** 解释本次已保存的错误代码；旧任务不补造当时没有记录的具体网络原因。 */
  @com.fasterxml.jackson.annotation.JsonProperty("diagnostic")
  public com.alphascope.common.diagnostic.FailureDiagnosis diagnostic() {
    if ("FAILED".equals(status))
      return com.alphascope.common.diagnostic.FailureDiagnostics.recorded(error);
    if (status != null
        && java.util.Set.of("CANCELLED", "TIMED_OUT", "INTERRUPTED").contains(status))
      return com.alphascope.common.diagnostic.FailureDiagnostics.code(status);
    return null;
  }

  /**
   * 将任务列表记录转换为公开响应；列表不额外加载阶段详情。
   *
   * @param row 持久任务执行记录，不可为 null
   * @return stages 为 null 的列表条目；完整阶段通过任务详情查询
   */
  public static JobView from(JobExecution row) {
    return new JobView(
        row.getId() == null ? 0 : row.getId(),
        row.getJobName(),
        row.getDataScope(),
        row.getBusinessDate(),
        row.getAttempt() == null ? 0 : row.getAttempt(),
        row.getStatus(),
        row.getError(),
        row.getStartedAt(),
        row.getEndedAt(),
        row.getHeartbeatAt(),
        row.getRetryOf(),
        row.getInputHash(),
        null,
        row.getDataStatus());
  }

  /**
   * 单次研究任务中一个阶段的执行状态。
   *
   * @author chenxuefeng
   */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Stage(
      /** 流水线阶段代码，例如CALENDAR、MARKET_SYNC、REPORT。 */
      String stageName,

      /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
      String status,

      /** 失败、中断或跳过的具体原因；正常执行时可为空。 */
      String error,

      /** 本次任务或阶段开始时刻，上海时区。 */
      LocalDateTime startedAt,

      /** 任务或阶段结束时刻；未结束时可为空。 */
      LocalDateTime endedAt,
      /** 独立数据质量；运行中及旧记录可为空。 */
      String dataStatus,
      /** 核验、实际进度和截止信息；旧记录可为空。 */
      StageDetails details) {
    /**
     * 显式转换已保存的阶段记录，原始JSON只在该读取边界解码。
     *
     * @param row 持久阶段记录
     * @return 执行状态与数据质量分别表达的阶段
     */
    public static Stage from(JobStage row) {
      return new Stage(
          row.getStageName(),
          row.getStatus(),
          row.getError(),
          row.getStartedAt(),
          row.getEndedAt(),
          row.getDataStatus(),
          row.getDetailsJson() == null
              ? null
              : Jsons.read(row.getDetailsJson(), StageDetails.class));
    }
  }
}

/** 持久任务与执行阶段类型；保持现有 REST 字段名称。 */

export interface Job {
  /** 已保存错误代码的当前释义，不代表重新探测。 */
  diagnostic?: import('./common').FailureDiagnosis | null;
  /** 与执行结果独立的数据质量；旧记录为空，不默认完整。 */
  data_status?: string | null;
  /** 本次任务或阶段开始时刻，上海时区。 */
  started_at?: string | null;
  /** 任务或阶段结束时刻；未结束时可为空。 */
  ended_at?: string | null;
  /** 当前资源记录的数据库主键。 */
  id: number;
  /** 研究任务类型：DAILY日报，WEEKLY周报。 */
  job_name: string;
  /** 运行配置的数据源范围，例如DEMO或REAL:BAOSTOCK,SINA，不等于单条证据来源。 */
  data_scope: string;
  /** 本次研究采用的业务日期，使用上海时区。 */
  business_date: string;
  /** 同任务类型和业务日期的尝试序号，从1递增。 */
  attempt: number;
  /** 任务执行状态；包含PENDING/RUNNING、SUCCESS/SKIPPED/FAILED/INTERRUPTED及取消、超时的请求态和终态。 */
  status: string;
  /** 失败、中断或跳过的具体原因；正常执行时可为空。 */
  error?: string | null;
  /** 工作进程最近心跳时间，用于识别失联任务。 */
  heartbeat_at?: string;
  /** 被重试的原任务ID；首次提交时为空。 */
  retry_of?: number;
}

export interface JobStage {
  /** 本阶段独立数据质量；运行中或旧记录可为空。 */
  data_status?: string | null;
  /** 实际进度、硬截止、恢复模式和质量核验。 */
  details?: {
    diagnostic?: import('./common').FailureDiagnosis | null;
    quality?: {
      status: string;
      available: number;
      expected: number;
      note: string;
      issues: { scope: string; source?: string; status: string; reason: string }[];
    } | null;
    progress?: { completed: number; total: number; message: string; updatedAt: string } | null;
    deadline?: string;
    retryOf?: number | null;
    recoveryMode?: string;
  } | null;
  /** 流水线阶段代码，例如CALENDAR、MARKET_SYNC、REPORT。 */
  stage_name: string;
  /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
  status: string;
  /** 失败、中断或跳过的具体原因；正常执行时可为空。 */
  error?: string | null;
  /** 本次任务或阶段开始时刻，上海时区。 */
  started_at?: string;
  /** 任务或阶段结束时刻；未结束时可为空。 */
  ended_at?: string;
}

export interface JobDetail extends Job {
  /** 当前任务已经保存的各阶段状态及时间记录。 */
  stages: JobStage[];
}

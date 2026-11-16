package com.alphascope.scheduler.mapper;

import com.alphascope.scheduler.dto.CompletedJobQuery;
import com.alphascope.scheduler.dto.JobCompletion;
import com.alphascope.scheduler.dto.JobStopRequest;
import com.alphascope.scheduler.dto.ScheduledJobQuery;
import com.alphascope.scheduler.entity.JobExecution;
import com.alphascope.scheduler.entity.JobStage;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 任务队列、MySQL互斥锁、阶段状态与心跳持久化，重试保留父任务记录。
 *
 * @author chenxuefeng
 */
@Mapper
public interface JobMapper {

  /**
   * 在当前数据库连接上尝试获取任务命名互斥锁，不等待其他连接释放。
   *
   * <p>获取与释放必须使用同一数据库连接；事务提交不会自动释放 MySQL 命名锁。
   *
   * @param key 同一业务任务共享的 MySQL 命名锁名称
   * @return 1 表示成功获取、0 表示被其他连接占用，null 表示数据库无法完成锁操作
   */
  Integer lock(String key);

  /**
   * 在持有锁的同一数据库连接上释放任务命名互斥锁。
   *
   * @param key 同一业务任务共享的 MySQL 命名锁名称
   * @return 1 表示释放成功、0 表示锁不属于当前连接，null 表示锁不存在或数据库无法完成操作
   */
  Integer unlock(String key);

  /**
   * 读取同任务类型、同业务日期的下一尝试序号，无既有任务时从 1 开始。
   *
   * @param name 任务类型，DAILY 表示日报，WEEKLY 表示周报
   * @param date 对应的业务日期，按上海时区解释
   * @return 已有最大尝试序号加 1
   */
  int attempt(@Param("name") String name, @Param("date") LocalDate date);

  /**
   * 读取当前用户、任务类型、日期和来源范围的最后一次尝试。
   *
   * @param query 自动调度所需的固定范围
   * @return 最近尝试；不存在为null
   */
  JobExecution latestForSchedule(ScheduledJobQuery query);

  /**
   * 统计相同范围全部尝试，手动尝试也占自动重试预算，防止重启清空预算。
   *
   * @param query 当前用户内的任务范围
   * @return 已保存的尝试数量
   */
  long scheduledAttempts(ScheduledJobQuery query);

  /**
   * 按任务类型、日期、数据源范围及输入指纹精确查找最近已成功或已跳过的任务。
   *
   * @param query 任务类型、业务日期、配置数据源范围及实际输入指纹
   * @return 可复用任务的主键；没有相同输入结果时返回 null
   */
  Long completed(CompletedJobQuery query);

  /**
   * 新增 PENDING 持久队列任务，记录尝试号、输入指纹及重试来源并回填主键。
   *
   * @param row 任务类型、业务日期、数据源范围、输入指纹、尝试号及可空父任务 id；成功后回填 id
   */
  void start(JobExecution row);

  /**
   * 保存某次任务的最终状态、结束时刻及失败或跳过原因。
   *
   * @param id 任务执行主键，关联 job_execution.id
   * @param status 最终执行状态，例如 SUCCESS、SKIPPED、FAILED 或 INTERRUPTED
   * @param error 已脱敏的错误或跳过原因；正常完成时可为 null
   */
  void finish(@Param("id") long id, @Param("status") String status, @Param("error") String error);

  /**
   * 为任务阶段新增 RUNNING 记录并保存阶段开始时刻。
   *
   * @param id 任务执行主键，关联 job_execution.id
   * @param stage 流水线阶段代码，例如 CALENDAR 或 MARKET_SYNC
   */
  void stage(@Param("id") long id, @Param("stage") String stage);

  /**
   * 更新指定任务阶段的最终状态、结束时刻及错误说明。
   *
   * @param stage 任务主键、阶段代码、最终状态和可空的已脱敏错误原因
   */
  void stageEnd(JobStage stage);

  /**
   * 按主键读取单次研究任务的持久状态，不触发执行或重试。
   *
   * @param id 任务执行主键，关联 job_execution.id
   * @return 任务记录；不存在时返回 null
   */
  JobExecution get(long id);

  /**
   * 读取某次任务已经保存的阶段状态，按阶段记录主键升序返回。
   *
   * @param id 任务执行主键，关联 job_execution.id
   * @return 阶段执行记录；尚未开始阶段时返回空列表
   */
  List<JobStage> stages(long id);

  /**
   * 查找同任务类型、业务日期和数据源范围下最近等待或运行中的任务。
   *
   * @param name 任务类型，DAILY 表示日报，WEEKLY 表示周报
   * @param date 对应的业务日期，按上海时区解释
   * @param scope 本次运行配置的数据源范围，例如 DEMO 或 REAL:BAOSTOCK,SINA
   * @return 排队、运行或等待取消/超时收尾任务的主键；没有活动任务时返回 null
   */
  Long active(
      @Param("name") String name, @Param("date") LocalDate date, @Param("scope") String scope);

  /**
   * 读取队列中主键最小的 PENDING 任务，供工作进程尝试领取。
   *
   * @return 待执行任务主键；队列为空时返回 null
   */
  Long pending();

  /**
   * 以 PENDING 为前置条件原子领取任务，写入工作进程、心跳并转为 RUNNING。
   *
   * @param id 任务执行主键，关联 job_execution.id
   * @param owner 领取或持有该任务的工作进程唯一标识
   * @return 受影响行数；1 表示领取成功，0 表示任务不存在或已被其他进程领取
   */
  int claim(@Param("id") long id, @Param("owner") String owner);

  /**
   * 仅为指定工作进程实际持有且仍在执行或终止收尾 的任务刷新心跳。
   *
   * @param id 任务执行主键，关联 job_execution.id
   * @param owner 领取或持有该任务的工作进程唯一标识
   * @return 受影响行数；1 表示更新成功，0 表示任务不存在、归属不符或已结束
   */
  int heartbeat(@Param("id") long id, @Param("owner") String owner);

  /**
   * 更新该次任务已经核实的完整输入指纹，供相同输入结果复用。
   *
   * @param id 任务执行主键，关联 job_execution.id
   * @param hash 按实际业务输入计算的 SHA-256 指纹
   */
  void inputHash(@Param("id") long id, @Param("hash") String hash);

  /**
   * 读取执行或终止收尾中且最近心跳早于当前数据库时间两分钟的候选失联任务。
   *
   * <p>仅心跳过期不能判定进程已退出；调用方还须确认工作锁可获取后再恢复任务。
   *
   * @return 待核查的候选任务列表；无候选时返回空列表
   */
  List<JobExecution> stale();

  /**
   * 将指定任务仍在 RUNNING 的阶段按已确认的中断、取消或超时标记结束，保留原因及结束时刻。
   *
   * @param stage 任务主键、最终执行状态及未完成原因
   */
  void stopRunningStages(JobStage stage);

  /**
   * 统计全部持久任务执行记录，包括历史失败和重试尝试。
   *
   * @return 任务执行记录总数
   */
  long count();

  /**
   * 按主键倒序分页读取任务执行记录。
   *
   * @param size 本次分页返回条数，由调用方校验合法范围
   * @param offset 从 0 开始的分页偏移，由调用方校验为非负数
   * @return 当前页任务记录；偏移超过总数时返回空列表
   */
  List<JobExecution> page(@Param("size") int size, @Param("offset") int offset);

  /**
   * 原子保存最终执行状态和数据质量，仅允许仍拥有任务的进程更新。
   *
   * @param completion 任务、进程归属、执行结果、数据质量和公开原因
   * @return 受影响行数；0表示任务归属或运行状态已改变
   */
  int complete(JobCompletion completion);

  /**
   * 持久化人工取消或阶段超时意图；等待任务直接取消，运行任务交给工作线程收尾。
   *
   * @param request 任务、停止状态、可空归属和公开原因
   * @return 成功记录终止意图的行数；已结束任务返回0
   */
  int requestStop(JobStopRequest request);

  /**
   * 更新仍在运行的阶段进度和截止信息，不改变阶段执行状态。
   *
   * @param stage 任务、阶段及实际进度详情JSON
   */
  void stageProgress(JobStage stage);
}

package com.alphascope.scheduler.service;

import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.exception.NotFoundException;
import com.alphascope.common.response.PageResponse;
import com.alphascope.scheduler.dto.JobSubmissionCommand;
import com.alphascope.scheduler.vo.JobView;
import java.time.LocalDate;

/**
 * 数据库持久队列为唯一任务入口；互斥使用独立连接，心跳与业务事务分离。
 *
 * @author chenxuefeng
 */
public interface JobService {

  /**
   * 分页读取全部持久任务执行记录，保留失败和重试尝试。
   *
   * @param request 页码和分页大小，默认第 1 页、每页 20 条
   * @return 当前页任务、匹配总数、页码和每页大小；列表条目不额外查询阶段
   */
  PageResponse<JobView> page(PageRequest request);

  /**
   * 读取单次任务的状态、心跳、错误及阶段记录。
   *
   * @param id 任务执行主键
   * @return 持久任务详情
   * @throws NotFoundException 任务主键不存在
   */
  JobView get(long id);

  /**
   * 在数据库互斥范围内提交持久任务，按数据范围及完整输入指纹复用可用结果。
   *
   * <p>提交只完成排队；force 或显式重试允许新尝试，已经存在的活动任务仍会复用。
   *
   * @param command 报告类型、业务日期、显式新修订和服务端原尝试引用
   * @return 活动任务、相同输入已完成任务或本次新增任务的主键
   */
  long submit(JobSubmissionCommand command);

  /**
   * 同步提交并处理研究任务，供测试或显式开发工具调用；HTTP 和调度器使用异步提交。
   *
   * @param type 报告或任务类型，DAILY 表示日报，WEEKLY 表示周报
   * @param date 研究或记录对应的业务日期，上海时区
   * @return 被提交或复用的任务主键
   */
  long run(String type, LocalDate date);

  /**
   * 在原任务编号下重新执行，旧状态和阶段保留为执行历史。
   *
   * @param id 任务执行主键
   * @return 原任务或当前活动任务的主键
   * @throws IllegalArgumentException 原任务不是失败、中断、取消或超时状态
   */
  long retry(long id);

  /** 刷新当前实例正在执行任务的心跳，不改写其他进程持有的任务。 */
  void heartbeat();

  /** 核对心跳超时任务及工作锁，只有锁已释放时才标记中断并保留可重试状态。 */
  void recover();

  /** 恢复已确认失联任务后，读取并尝试处理队列中最早的待执行任务。 */
  void processNext();

  /**
   * 领取并串行执行指定待处理任务，使用独立数据库连接持有互斥锁。
   *
   * <p>仅处理 PENDING 且数据源范围匹配的任务；执行状态、阶段及错误持续持久化，失败不回退演示行情。
   *
   * @param id 任务执行主键
   */
  void process(long id);

  /**
   * 取消等待任务或为运行任务持久化取消请求，返回当前状态。
   *
   * @param id 任务主键
   * @return 最新任务详情；已结束任务保持既有结果，不重新执行
   * @throws NotFoundException 任务主键不存在
   */
  JobView cancel(long id);

  /** 领取当前身份的共享采集任务，与私人日报消费者分离；不并行连接同一供应商。 */
  void processSharedNext();

  /**
   * 分页读取已归档执行，旧版重试链也可追溯。
   *
   * @param id 原任务编号
   * @param request 已校验分页
   * @return 原执行快照
   */
  PageResponse<JobView> history(long id, PageRequest request);
}

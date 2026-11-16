package com.alphascope.backtest.mapper;

import com.alphascope.backtest.dto.ValidationTaskMutation;
import com.alphascope.backtest.entity.ResearchValidationTask;
import com.alphascope.common.dto.PageRequest;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 独立研究验证队列；所有SQL集中XML，停止、归属和最终发布在数据库原子核对。
 *
 * @author chenxuefeng
 */
@Mapper
public interface ValidationTaskMapper {
  /**
   * 保存冻结请求并取得任务主键。
   *
   * @param task 冻结条件和去重键
   */
  void insert(ResearchValidationTask task);

  /**
   * 读取轻量任务详情，不加载完整证据。
   *
   * @param id 任务主键
   * @return 元数据、冻结请求和轻量摘要，不存在为null
   */
  ResearchValidationTask get(long id);

  /**
   * 分页读取任务元数据，不加载请求、摘要或证据。
   *
   * @param request 页码与大小
   * @return 按最近提交排序的任务
   */
  List<ResearchValidationTask> page(PageRequest request);

  /**
   * 统计全部历史任务。
   *
   * @return 任务数
   */
  long count();

  /**
   * 统计活动任务，用于本机接收容量控制。
   *
   * @return 排队与执行中的任务数
   */
  long activeCount();

  /**
   * 查找重复的活动请求。
   *
   * @param key 冻结条件与数据范围指纹
   * @return 已有活动任务，不存在为null
   */
  ResearchValidationTask active(String key);

  /**
   * 找到队首待执行任务。
   *
   * @return 主键，无任务为null
   */
  Long pending();

  /**
   * 仅允许一个进程原子领取PENDING任务。
   *
   * @param change 归属、开始和截止时间
   * @return 成功领取的行数
   */
  int claim(ValidationTaskMutation change);

  /**
   * 保存心跳和实际进度，不覆盖停止意图。
   *
   * @param change 当前归属及实际进度
   * @return 仍归本进程执行的行数
   */
  int pulse(ValidationTaskMutation change);

  /**
   * 保存取消意图；未开始任务直接取消。
   *
   * @param change 主键及请求时间
   * @return 实际改变的行数
   */
  int cancel(ValidationTaskMutation change);

  /**
   * 按归属和截止原子发布结果，已取消或过期时不保存成功证据。
   *
   * @param change 候选终态和已经复核的证据
   * @return 实际完成的行数
   */
  int finish(ValidationTaskMutation change);

  /**
   * 恢复心跳失联任务，保留原取消与超时意图。
   *
   * @param change 实际时间与失联阈值
   * @return 恢复任务数量
   */
  int recover(ValidationTaskMutation change);

  /**
   * 仅显式下载时加载已完成的完整证据。
   *
   * @param id 任务主键
   * @return 已成功发布的精确JSON，没有成功结果为null
   */
  String evidence(long id);
}

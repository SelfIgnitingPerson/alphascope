package com.alphascope.intelligence.service;

import com.alphascope.common.exception.NotFoundException;
import com.alphascope.common.response.PageResponse;
import com.alphascope.intelligence.dto.ReportSearchRequest;
import com.alphascope.intelligence.vo.ReportComparison;
import com.alphascope.intelligence.vo.ReportDetail;
import com.alphascope.intelligence.vo.ReportSummary;
import java.time.LocalDate;
import java.util.List;

/**
 * 报告生成与读取编排：冻结业务时点输入，在事务外生成摘要，最后短事务保存新修订。
 *
 * @author chenxuefeng
 */
public interface ReportService {

  /**
   * 按报告类型、业务日期、数据模式及修订筛选条件分页查询归档。
   *
   * @param request 类型、可选日期、分页和最新修订筛选；省略日期时查询最近三个月
   * @return 包含当前页报告、匹配总数、页码及每页条数的分页结果
   */
  PageResponse<ReportSummary> list(ReportSearchRequest request);

  /**
   * 读取指定修订已保存的正文、冻结输入和分析，不用当前行情重建历史。
   *
   * @param id 报告修订主键
   * @return 该修订的完整报告详情
   * @throws NotFoundException 报告主键不存在
   */
  ReportDetail get(long id);

  /**
   * 读取指定报告同类型、同业务日期及同数据模式的全部修订。
   *
   * @param id 报告修订主键
   * @return 按修订号倒序排列的报告归档列表
   */
  List<ReportSummary> versions(long id);

  /**
   * 比较同类报告修订的冻结正文与输入指纹。
   *
   * @param left 被比较的第一份报告修订主键
   * @param right 被比较的第二份报告修订主键
   * @return 内容字段差异及输入是否变化的结果
   * @throws IllegalArgumentException 两个报告的业务日期、类型或真实/演示模式不同
   */
  ReportComparison compare(long left, long right);

  /**
   * 收集并冻结业务时点证据，生成报告新修订，不覆盖已有报告。
   *
   * <p>模型请求在保存事务外执行；模型不可用时使用规则摘要。带 jobId 的重载关联持久任务，不带该参数的重载不关联任务。
   *
   * @param type 报告或任务类型，DAILY 表示日报，WEEKLY 表示周报
   * @param date 研究或记录对应的业务日期，上海时区
   * @return 本次新保存的报告主键
   */
  long generate(String type, LocalDate date);

  /**
   * 收集并冻结业务时点证据，生成报告新修订，不覆盖已有报告。
   *
   * <p>模型请求在保存事务外执行；模型不可用时使用规则摘要。带 jobId 的重载关联持久任务，不带该参数的重载不关联任务。
   *
   * @param type 报告或任务类型，DAILY 表示日报，WEEKLY 表示周报
   * @param date 研究或记录对应的业务日期，上海时区
   * @param jobId 关联的持久任务执行主键；不关联任务时为 null
   * @return 本次新保存的报告主键
   */
  long generate(String type, LocalDate date, Long jobId);
}

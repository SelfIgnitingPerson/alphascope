package com.alphascope.intelligence.mapper;

import com.alphascope.intelligence.dto.ReportQuery;
import com.alphascope.intelligence.entity.AnalysisReport;
import com.alphascope.intelligence.entity.ReportSource;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 保存新增报告修订及其冻结输入、来源引用；读取按模式隔离归档结果。
 *
 * @author chenxuefeng
 */
@Mapper
public interface ReportMapper {

  /**
   * 按报告类型、日期、模式及最新修订筛选条件分页读取报告归档。
   *
   * @param query 报告类型、业务日期区间、数据模式、最新修订过滤及分页条件
   * @return 按业务日期及修订倒序排列的当前页报告；无匹配结果时返回空列表
   */
  List<AnalysisReport> list(ReportQuery query);

  /**
   * 按主键读取已保存的报告正文与元信息，不重算研究分析。
   *
   * @param id 报告修订主键，关联 analysis_report.id
   * @return 报告记录；不存在时返回 null
   */
  AnalysisReport get(long id);

  /**
   * 读取指定报告修订已经保存的来源文档或数据引用。
   *
   * @param id 报告修订主键，关联 analysis_report.id
   * @return 本修订的来源引用列表；未保存引用时返回空列表
   */
  List<ReportSource> sources(long id);

  /**
   * 读取同类型、同业务日期的下一报告修订号，未保存报告时从 1 开始。
   *
   * <p>此查询本身不加互斥锁；调用方须通过任务互斥及事务协调版本分配和报告新增。
   *
   * @param type 报告类型，DAILY 表示日报，WEEKLY 表示周报
   * @param date 对应的业务日期，按上海时区解释
   * @return 已有最大修订号加 1
   */
  int revision(@Param("type") String type, @Param("date") LocalDate date);

  /**
   * 新增报告修订并回填生成的主键，不覆盖历史报告。
   *
   * <p>调用方在同一短事务内保存本报告、冻结输入及来源引用。
   *
   * @param row 报告类型、日期、修订、正文、算法版本、输入指纹、数据范围及原始时点；成功后回填 id
   */
  void save(AnalysisReport row);

  /**
   * 为指定报告修订新增一条已采用的数据引用。
   *
   * @param id 报告修订主键，关联 analysis_report.id
   * @param reference 真实引用的文档、数据记录或采集批次标识
   */
  void source(@Param("id") long id, @Param("reference") String reference);

  /**
   * 保存指定报告修订的完整冻结输入 JSON，供历史读取及复核使用。
   *
   * @param id 报告修订主键，关联 analysis_report.id
   * @param input 该修订生成时实际采用的完整输入 JSON，不从当前库补造旧证据
   */
  void input(@Param("id") long id, @Param("input") String input);

  /**
   * 按与报告列表相同的筛选条件统计匹配总数，不应用分页大小或偏移。
   *
   * @param query 报告类型、业务日期区间、数据模式、最新修订过滤及分页条件
   * @return 符合类型、日期、模式及最新修订筛选条件的报告数量
   */
  long count(ReportQuery query);

  /**
   * 读取同类型、同业务日期和同数据模式的全部报告修订。
   *
   * @param type 报告类型，DAILY 表示日报，WEEKLY 表示周报
   * @param date 对应的业务日期，按上海时区解释
   * @param synthetic true 查询演示数据，false 查询真实数据，两种模式不得混用
   * @return 按修订号倒序排列的报告列表；无记录时返回空列表
   */
  List<AnalysisReport> versions(
      @Param("type") String type,
      @Param("date") LocalDate date,
      @Param("synthetic") boolean synthetic);

  /**
   * 读取指定报告修订已经冻结的完整输入 JSON，不读取当前行情重建。
   *
   * @param id 报告修订主键，关联 analysis_report.id
   * @return 冻结输入 JSON；没有保存快照时返回 null
   */
  String snapshot(long id);
}

package com.alphascope.industry.mapper;

import com.alphascope.industry.dto.CohortWriteCommand;
import com.alphascope.industry.entity.SourceDocument;
import com.alphascope.industry.entity.ThemeResearchCohort;
import com.alphascope.market.entity.Instrument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 保存不可变披露研究样本及其完整成员，不自动加入用户自选。
 *
 * @author chenxuefeng
 */
@Mapper
public interface ThemeCohortMapper {

  /**
   * 登记交易所披露来源；相同公开材料已存在时复用原文档主键。
   *
   * @param row 公开材料链接、标题、披露/核验时间、内容指纹和正文；成功后回填 id
   */
  void source(SourceDocument row);

  /**
   * 登记披露样本中的股票标的；已存在时复用主键且不覆盖既有主数据。
   *
   * @param row 证券代码、名称及交易所；成功后 id 为新增或已存在的证券主键
   */
  void instrument(Instrument row);

  /**
   * 按主题业务代码新增不可变披露样本，已有同版本记录时不覆盖其定义。
   *
   * <p>调用方须核对已有样本的内容指纹并拒绝版本内容冲突；本方法不自动将样本成员加入自选。
   *
   * @param row 主题代码、样本代码、公开来源、可用时点、生效日、预期成员数及完整定义
   */
  void cohort(CohortWriteCommand row);

  /**
   * 按样本代码读取主键和内容指纹，用于不可覆盖版本的查重。
   *
   * @param code 不可变披露研究样本的版本代码，不是主题代码
   * @return 仅含已查询主键及内容指纹的样本对象；不存在时返回 null
   */
  ThemeResearchCohort get(String code);

  /**
   * 将已登记证券关联到指定披露样本；重复成员关系保持不变。
   *
   * @param cohort 披露样本主键，关联 theme_research_cohort.id
   * @param instrument 证券主键，关联 instrument.id
   */
  void member(@Param("cohort") long cohort, @Param("instrument") long instrument);

  /**
   * 统计指定不可变样本已保存的成员数量，用于核对披露名单完整性。
   *
   * @param id 披露样本主键，关联 theme_research_cohort.id
   * @return 已保存成员数量；无成员时为 0
   */
  int memberCount(long id);
}

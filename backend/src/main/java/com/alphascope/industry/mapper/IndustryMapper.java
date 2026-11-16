package com.alphascope.industry.mapper;

import com.alphascope.industry.dto.CohortDetailDO;
import com.alphascope.industry.dto.ThemeMemberDO;
import com.alphascope.industry.dto.ThemeScoreQuery;
import com.alphascope.industry.entity.Theme;
import com.alphascope.industry.entity.ThemeScore;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 按业务时点选择真实披露样本或人工归属，并隔离演示主题评分。
 *
 * @author chenxuefeng
 */
@Mapper
public interface IndustryMapper {

  /**
   * 读取全部主题字典，按主题主键升序返回。
   *
   * @return 主题列表；未配置主题时返回空列表
   */
  List<Theme> themes();

  /**
   * 读取业务时点可见且已生效的最新披露样本成员；该时点无样本时读取人工归属。
   *
   * <p>披露截止时刻采用业务日 16:10，且必须同时满足样本和原始文档的可见性。
   *
   * @param code 主题稳定业务代码，例如 PCB
   * @param date 对应的业务日期，按上海时区解释
   * @return 当前选定范围内的成员和来源信息；无有效成员时返回空列表
   */
  List<ThemeMemberDO> members(@Param("code") String code, @Param("date") LocalDate date);

  /**
   * 只按人工归属的真实生效区间读取主题成员，不选择披露研究样本。
   *
   * @param code 主题稳定业务代码，例如 PCB
   * @param date 对应的业务日期，按上海时区解释
   * @return 有效人工归属成员；无记录时返回空列表
   */
  List<ThemeMemberDO> legacyMembers(@Param("code") String code, @Param("date") LocalDate date);

  /**
   * 读取证券在当前选定样本中的归属起始日；无匹配样本成员时尝试有效人工归属。
   *
   * @param code 主题稳定业务代码，例如 PCB
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param date 对应的业务日期，按上海时区解释
   * @return 匹配的真实归属生效日期；无有效归属时返回 null
   */
  LocalDate membershipSince(
      @Param("code") String code, @Param("symbol") String symbol, @Param("date") LocalDate date);

  /**
   * 读取业务日 16:10 以前可见且已经生效的最新披露样本及原始文档信息。
   *
   * @param code 主题稳定业务代码，例如 PCB
   * @param date 对应的业务日期，按上海时区解释
   * @return 当前可用披露样本；尚无可见样本时返回 null
   */
  CohortDetailDO cohort(@Param("code") String code, @Param("date") LocalDate date);

  /**
   * 按同一主题、日期区间和真实/演示模式读取历史评分，按交易日升序返回。
   *
   * @param query 主题业务代码、闭区间起止日期和真实/演示模式
   * @return 已保存的主题收益、广度、覆盖率及评分；无记录时返回空列表
   */
  List<ThemeScore> scores(ThemeScoreQuery query);

  /**
   * 保存等权收益模型的主题评分及覆盖证据，已存在时更新同日同模式结果。
   *
   * <p>模型固定为 EQUAL_RETURN_V1；证据不足时评分保持 null，不能将样本覆盖等同于完整行业覆盖。
   *
   * @param row 主题主键、交易日、收益、广度、覆盖率、可空评分及证据 JSON
   */
  void save(ThemeScore row);
}

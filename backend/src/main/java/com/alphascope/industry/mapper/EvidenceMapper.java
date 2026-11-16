package com.alphascope.industry.mapper;

import com.alphascope.industry.dto.IndustryMetricDO;
import com.alphascope.industry.dto.MetricInput;
import com.alphascope.industry.entity.SourceDocument;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 公开来源文档与产业指标的持久化入口，保留来源内容指纹。
 *
 * @author chenxuefeng
 */
@Mapper
public interface EvidenceMapper {

  /**
   * 保存带公开链接、披露时刻和内容指纹的来源文档，并回填生成的主键。
   *
   * @param row 来源类型、链接、标题、披露时刻、原文和内容指纹；成功后 id 为新主键
   */
  void source(SourceDocument row);

  /**
   * 按主题业务代码新增带来源引用、单位及披露时刻的产业指标。
   *
   * @param row 已校验的主题代码、指标代码、日期、数值、单位及来源文档主键
   * @return 插入行数；1 表示成功，0 表示主题代码不存在
   */
  int metric(MetricInput row);

  /**
   * 读取指定主题在业务日期及公开截止时点可见的产业指标和来源链接。
   *
   * @param code 主题稳定业务代码，例如 PCB
   * @param date 对应的业务日期，按上海时区解释
   * @param cutoff 可采用公开证据的截止时刻，上海时区，包含该时刻
   * @return 按指标日期倒序排列的最多 100 条指标；无可见记录时返回空列表
   */
  List<IndustryMetricDO> metrics(
      @Param("code") String code,
      @Param("date") LocalDate date,
      @Param("cutoff") LocalDateTime cutoff);

  /**
   * 按来源文档主键读取公开来源元信息，不加载大体积原文正文。
   *
   * @param id 来源文档主键，关联 source_document.id
   * @return 来源元信息；不存在时返回 null
   */
  SourceDocument get(long id);
}

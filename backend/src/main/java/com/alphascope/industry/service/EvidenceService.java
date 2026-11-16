package com.alphascope.industry.service;

import com.alphascope.industry.dto.IndustryMetricDO;
import com.alphascope.industry.dto.MetricInput;
import com.alphascope.industry.dto.SourceInput;
import java.time.LocalDate;
import java.util.List;

/**
 * 管理公开材料和有明确单位、披露时间的产业指标引用。
 *
 * @author chenxuefeng
 */
public interface EvidenceService {

  /**
   * 登记带公开链接、披露时刻和原文指纹的产业来源。
   *
   * @param input 来源类型、公开链接、标题、披露时刻及原文正文
   * @return 新保存来源文档的主键
   */
  long source(SourceInput input);

  /**
   * 校验公开来源、主题及披露日期后新增带明确单位的产业指标。
   *
   * @param input 主题/指标代码、业务日期、披露时刻、数值、单位及来源主键
   * @throws IllegalArgumentException 来源或主题不存在，或指标日期晚于披露日期
   */
  void metric(MetricInput input);

  /**
   * 读取业务日及其后一天零时之前可见的主题产业指标和原始来源，不触发采集。
   *
   * @param code 主题稳定业务代码，例如 PCB
   * @param date 研究或记录对应的业务日期，上海时区
   * @return 已保存产业指标列表；没有可见记录时为空列表
   */
  List<IndustryMetricDO> metrics(String code, LocalDate date);
}

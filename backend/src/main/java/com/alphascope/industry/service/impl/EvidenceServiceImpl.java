package com.alphascope.industry.service.impl;

import com.alphascope.common.util.Jsons;
import com.alphascope.industry.dto.IndustryMetricDO;
import com.alphascope.industry.dto.MetricInput;
import com.alphascope.industry.dto.SourceInput;
import com.alphascope.industry.entity.SourceDocument;
import com.alphascope.industry.mapper.EvidenceMapper;
import com.alphascope.industry.service.EvidenceService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * EvidenceService 的业务实现。
 *
 * <p>管理公开材料和有明确单位、披露时间的产业指标引用。
 *
 * @author chenxuefeng
 */
@Service
public class EvidenceServiceImpl implements EvidenceService {
  private final EvidenceMapper mapper;

  public EvidenceServiceImpl(EvidenceMapper mapper) {
    this.mapper = mapper;
  }

  /** {@inheritDoc} */
  @Override
  public long source(SourceInput input) {
    var row = new SourceDocument();
    row.setSourceType(input.sourceType());
    row.setUrl(input.url());
    row.setTitle(input.title());
    row.setPublishedAt(input.publishedAt());
    row.setContent(input.content());
    row.setContentHash(Jsons.hash(input.content()));
    mapper.source(row);
    return row.getId();
  }

  /** {@inheritDoc} */
  @Override
  public void metric(MetricInput input) {
    var source = mapper.get(input.sourceId());
    if (source == null) {
      throw new IllegalArgumentException("来源不存在");
    }
    if (input.asOfDate().isAfter(input.publishedAt().toLocalDate())) {
      throw new IllegalArgumentException("产业指标业务日期不能晚于公开披露日期");
    }
    if (mapper.metric(input) != 1) {
      throw new IllegalArgumentException("主题不存在");
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<IndustryMetricDO> metrics(String code, LocalDate date) {
    return mapper.metrics(code, date, date.plusDays(1).atStartOfDay());
  }
}

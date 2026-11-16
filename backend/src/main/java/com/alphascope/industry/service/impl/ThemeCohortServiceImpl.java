package com.alphascope.industry.service.impl;

import com.alphascope.common.util.Jsons;
import com.alphascope.industry.dto.CohortDefinition;
import com.alphascope.industry.dto.CohortWriteCommand;
import com.alphascope.industry.entity.SourceDocument;
import com.alphascope.industry.mapper.IndustryMapper;
import com.alphascope.industry.mapper.ThemeCohortMapper;
import com.alphascope.industry.service.ThemeCohortService;
import com.alphascope.industry.validator.ThemeCohortValidator;
import com.alphascope.market.entity.Instrument;
import com.alphascope.market.service.MarketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ThemeCohortService 的业务实现。
 *
 * <p>导入已核对原始披露的固定样本；不把当前概念股名单伪装成历史官方成分。
 *
 * @author chenxuefeng
 */
@Service
public class ThemeCohortServiceImpl implements ThemeCohortService {

  private final ThemeCohortMapper mapper;
  private final IndustryMapper industry;
  private final MarketService market;
  private final TransactionTemplate tx;
  private final List<CohortDefinition> definitions;

  public ThemeCohortServiceImpl(
      ThemeCohortMapper mapper,
      IndustryMapper industry,
      MarketService market,
      TransactionTemplate tx,
      ObjectMapper json)
      throws IOException {
    this.mapper = mapper;
    this.industry = industry;
    this.market = market;
    this.tx = tx;
    try (var in = getClass().getResourceAsStream("/research/theme-cohorts.json")) {
      definitions = List.of(json.readValue(Objects.requireNonNull(in), CohortDefinition[].class));
    }
    definitions.forEach(ThemeCohortValidator::validate);
  }

  /** {@inheritDoc} */
  @Override
  public String catalogHash() {
    return Jsons.hash(Jsons.write(definitions));
  }

  /** {@inheritDoc} */
  @Override
  public void prepare(LocalDate date) {
    if (market.synthetic()) {
      return;
    }
    var cutoff = date.atTime(16, 10);
    for (var d : definitions) {
      if (!d.availableAt().isAfter(cutoff)) {
        tx.executeWithoutResult(status -> persist(d));
      }
    }
  }

  private void persist(CohortDefinition d) {
    String definition = Jsons.write(d), hash = Jsons.hash(definition);
    var old = mapper.get(d.code());
    if (old != null) {
      if (!hash.equals(old.getContentHash())) {
        throw new IllegalStateException("披露样本不可覆盖，请新增版本");
      }
      if (mapper.memberCount(old.getId()) != d.members().size()) {
        throw new IllegalStateException("样本成员记录不完整");
      }
      return;
    }
    SourceDocument source = new SourceDocument();
    source.setUrl(d.sourceUrl());
    source.setTitle(d.sourceTitle());
    source.setPublishedAt(d.publishedAt());
    source.setFetchedAt(d.verifiedAt());
    // 保存页码、核验后的名单和原PDF指纹；抓取时间不参与内容版本指纹。
    source.setContent(definition);
    source.setContentHash(hash);
    mapper.source(source);
    CohortWriteCommand row = new CohortWriteCommand();
    row.setCode(d.code());
    row.setThemeCode(d.themeCode());
    row.setTitle(d.title());
    row.setSourceDocumentId(source.getId());
    row.setAvailableAt(d.availableAt());
    row.setEffectiveFrom(d.availableAt().toLocalDate());
    row.setExpectedMembers(d.members().size());
    row.setContentHash(hash);
    row.setDefinitionJson(definition);
    mapper.cohort(row);
    var saved = Objects.requireNonNull(mapper.get(d.code()), "主题不存在");
    if (!hash.equals(saved.getContentHash())) {
      throw new IllegalStateException("披露样本版本冲突");
    }
    long id = saved.getId();
    for (var member : d.members()) {
      Instrument instrument = new Instrument();
      instrument.setSymbol(member.symbol());
      instrument.setName(member.name());
      instrument.setExchange(member.symbol().split("\\.")[1]);
      mapper.instrument(instrument);
      mapper.member(id, instrument.getId());
    }
    if (mapper.memberCount(id) != d.members().size()) {
      throw new IllegalStateException("样本成员数量不一致");
    }
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> symbols(LocalDate date) {
    if (market.synthetic()) {
      return Set.of();
    }
    Set<String> out = new LinkedHashSet<>();
    for (var t : industry.themes()) {
      if (industry.cohort(t.getCode(), date) != null) {
        for (var m : industry.members(t.getCode(), date)) {
          out.add(m.getSymbol());
        }
      }
    }
    return out;
  }
}

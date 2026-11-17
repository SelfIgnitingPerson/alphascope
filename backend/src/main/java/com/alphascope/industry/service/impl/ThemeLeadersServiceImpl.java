package com.alphascope.industry.service.impl;

import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.response.ResultCode;
import com.alphascope.common.util.Jsons;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.industry.dto.ThemeLeaderUpdateInput;
import com.alphascope.industry.entity.ThemeLeaderRevision;
import com.alphascope.industry.mapper.ThemeLeaderMapper;
import com.alphascope.industry.service.ThemeLeadersService;
import com.alphascope.industry.vo.ThemeLeaderRevisionView;
import com.alphascope.industry.vo.ThemeLeadersView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 从唯一研究目录读取核心企业；只读且不联网，不把研究整理结果写入历史披露成分表。
 *
 * @author chenxuefeng
 */
@Service
public class ThemeLeadersServiceImpl implements ThemeLeadersService {
  private final List<ThemeLeadersView> catalog;
  private ThemeLeaderMapper mapper;

  @Autowired
  public void setMapper(ThemeLeaderMapper mapper) {
    this.mapper = mapper;
  }

  public ThemeLeadersServiceImpl(ObjectMapper json) throws IOException {
    try (var in = getClass().getResourceAsStream("/research/theme-leaders.json")) {
      catalog = List.of(json.readValue(Objects.requireNonNull(in), ThemeLeadersView[].class));
    }
    validate(catalog);
  }

  private static void validate(List<ThemeLeadersView> catalog) {
    var themes = new HashSet<String>();
    for (var group : catalog) {
      if (!themes.add(group.themeCode())
          || group.reviewedAt() == null
          || group.members().isEmpty()
          || group.members().size() > 30
          || group.title() == null
          || group.title().isBlank()
          || group.title().length() > 100
          || group.selectionBasis() == null
          || group.selectionBasis().isBlank()
          || group.limitation() == null
          || group.limitation().isBlank()) {
        throw new IllegalStateException("核心企业目录缺少日期、成员或主题重复");
      }
      var symbols = new HashSet<String>();
      var orders = new HashSet<Integer>();
      for (var member : group.members()) {
        if (!member.symbol().matches("[0-9]{6}\\.(SH|SZ|BJ)")
            || !symbols.add(member.symbol())
            || member.displayOrder() < 1
            || !orders.add(member.displayOrder())
            || member.sources().isEmpty()
            || member.sources().size() > 10
            || member.name() == null
            || member.name().isBlank()
            || member.segment() == null
            || member.segment().isBlank()
            || member.standing() == null
            || member.standing().isBlank()
            || member.rationale() == null
            || member.rationale().isBlank()) {
          throw new IllegalStateException("核心企业目录代码、展示顺序或来源不完整");
        }
        for (var source : member.sources()) {
          var uri = URI.create(source.url());
          if (!"https".equals(uri.getScheme())
              || uri.getHost() == null
              || uri.getRawUserInfo() != null
              || source.title().isBlank()) {
            throw new IllegalStateException("核心企业目录来源必须为有标题的HTTPS原文");
          }
        }
      }
      if (!group
          .members()
          .equals(
              group.members().stream()
                  .sorted(Comparator.comparingInt(ThemeLeadersView.Member::displayOrder))
                  .toList())) {
        throw new IllegalStateException("核心企业目录必须按明确的展示优先序排列");
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<ThemeLeadersView> list() {
    return catalog.stream().map(group -> revision(group.themeCode()).directory()).toList();
  }

  /** {@inheritDoc} */
  @Override
  public ThemeLeadersView get(String code) {
    return revision(code).directory();
  }

  /** {@inheritDoc} */
  @Override
  public ThemeLeaderRevisionView revision(String code) {
    var initial =
        catalog.stream()
            .filter(group -> group.themeCode().equals(code))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, 404, "该主题尚未整理核心企业目录"));
    var row = mapper == null ? null : mapper.latest(code);
    if (row != null && !Jsons.hash(row.getDirectoryJson()).equals(row.getContentHash()))
      throw new IllegalStateException("核心目录修订指纹不匹配");
    var group = row == null ? initial : Jsons.read(row.getDirectoryJson(), ThemeLeadersView.class);
    return new ThemeLeaderRevisionView(
        row == null ? 0 : row.getId(),
        group.reviewedAt().isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(90)),
        group);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public ThemeLeaderRevisionView revise(String code, ThemeLeaderUpdateInput input) {
    if (TenantContext.user() == null || !"ADMIN".equals(TenantContext.user().role()))
      throw new BusinessException(ResultCode.FORBIDDEN, 403, "维护核心目录需要管理员权限");
    if (mapper.lockTheme(code) == null)
      throw new BusinessException(ResultCode.NOT_FOUND, 404, "主题不存在");
    var current = revision(code);
    if (current.revision() != input.expectedRevision())
      throw new BusinessException(ResultCode.INVALID_ARGUMENT, 409, "目录已被其他维护操作更新，请刷新后重新核对");
    var group = input.directory();
    if (!code.equals(group.themeCode())
        || !LocalDate.now(ZoneId.of("Asia/Shanghai")).equals(group.reviewedAt()))
      throw new IllegalArgumentException("主题代码必须一致，复核日期必须为上海当前日期，不能倒填历史");
    try {
      validate(List.of(group));
    } catch (IllegalStateException invalid) {
      throw new IllegalArgumentException(invalid.getMessage());
    }
    var row = new ThemeLeaderRevision();
    row.setThemeCode(code);
    row.setDirectoryJson(Jsons.write(group));
    if (row.getDirectoryJson().length() > 100000) throw new IllegalArgumentException("目录内容超过容量限制");
    row.setContentHash(Jsons.hash(row.getDirectoryJson()));
    row.setReviewerId(TenantContext.id());
    mapper.insert(row);
    return revision(code);
  }
}

package com.alphascope.industry.service.impl;

import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.response.ResultCode;
import com.alphascope.industry.service.ThemeLeadersService;
import com.alphascope.industry.vo.ThemeLeadersView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 从唯一研究目录读取核心企业；只读且不联网，不把研究整理结果写入历史披露成分表。
 *
 * @author chenxuefeng
 */
@Service
public class ThemeLeadersServiceImpl implements ThemeLeadersService {
  private final List<ThemeLeadersView> catalog;

  public ThemeLeadersServiceImpl(ObjectMapper json) throws IOException {
    try (var in = getClass().getResourceAsStream("/research/theme-leaders.json")) {
      catalog = List.of(json.readValue(Objects.requireNonNull(in), ThemeLeadersView[].class));
    }
    var themes = new HashSet<String>();
    for (var group : catalog) {
      if (!themes.add(group.themeCode())
          || group.reviewedAt() == null
          || group.members().isEmpty()) {
        throw new IllegalStateException("核心企业目录缺少日期、成员或主题重复");
      }
      var symbols = new HashSet<String>();
      var orders = new HashSet<Integer>();
      for (var member : group.members()) {
        if (!member.symbol().matches("[0-9]{6}\\.(SH|SZ|BJ)")
            || !symbols.add(member.symbol())
            || member.displayOrder() < 1
            || !orders.add(member.displayOrder())
            || member.sources().isEmpty()) {
          throw new IllegalStateException("核心企业目录代码、展示顺序或来源不完整");
        }
        for (var source : member.sources()) {
          var uri = URI.create(source.url());
          if (!"https".equals(uri.getScheme())
              || uri.getHost() == null
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
    return catalog;
  }

  /** {@inheritDoc} */
  @Override
  public ThemeLeadersView get(String code) {
    return catalog.stream()
        .filter(group -> group.themeCode().equals(code))
        .findFirst()
        .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, 404, "该主题尚未整理核心企业目录"));
  }
}

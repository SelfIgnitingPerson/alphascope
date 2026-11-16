package com.alphascope.intelligence.builder;

import com.alphascope.intelligence.calculator.PriceVolumeCalculator;
import com.alphascope.intelligence.dto.PriceVolumeWindow;
import com.alphascope.intelligence.vo.PriceVolumeStudy;
import com.alphascope.market.dto.MarketBarDO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 将明确冻结了新方法及日历的报告输入交给量价计算；旧修订不追加新判断。
 *
 * @author chenxuefeng
 */
public final class PriceVolumeAnalysisBuilder {
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

  private PriceVolumeAnalysisBuilder() {}

  /**
   * 只解释保存时声明的方法，缺少标识的旧报告返回空值以保持原公开响应。
   *
   * @param input 本修订冻结输入
   * @param date 业务截止日期
   * @param demo 演示模式
   * @return 新方法每只标的的研究结果；旧修订为null
   */
  public static List<PriceVolumeStudy> build(JsonNode input, LocalDate date, boolean demo) {
    if (!PriceVolumeCalculator.VERSION.equals(input.path("priceVolumeMethod").asText())) {
      return null;
    }
    var results = new ArrayList<PriceVolumeStudy>();
    if (!input.path("watchlist").isArray()) {
      return List.of();
    }
    for (var stock : input.path("watchlist")) {
      List<MarketBarDO> bars;
      Set<LocalDate> expected;
      try {
        bars = JSON.convertValue(stock.path("history"), new TypeReference<List<MarketBarDO>>() {});
        expected =
            stock.path("expectedTradingDates").isArray()
                ? JSON.convertValue(
                    stock.path("expectedTradingDates"), new TypeReference<Set<LocalDate>>() {})
                : Set.of();
      } catch (IllegalArgumentException e) {
        bars = List.of();
        expected = Set.of();
      }
      results.add(
          PriceVolumeCalculator.calculate(
              PriceVolumeWindow.builder()
                  .symbol(stock.path("symbol").asText())
                  .name(stock.path("name").asText(stock.path("symbol").asText()))
                  .date(date)
                  .bars(bars)
                  .expectedDates(expected)
                  .suspended(stock.path("suspended").asBoolean())
                  .synthetic(demo)
                  .build()));
    }
    return List.copyOf(results);
  }
}

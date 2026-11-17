package com.alphascope.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alphascope.common.util.Jsons;
import com.alphascope.fundamental.vo.FinancialView;
import com.alphascope.indicator.vo.IndicatorView;
import com.alphascope.industry.vo.CohortView;
import com.alphascope.industry.vo.IndustryMetricView;
import com.alphascope.industry.vo.ThemeMemberView;
import com.alphascope.industry.vo.ThemeScoreView;
import com.alphascope.industry.vo.ThemeView;
import com.alphascope.intelligence.vo.ReportSummary;
import com.alphascope.market.dto.MarketCacheSettings;
import com.alphascope.market.dto.MarketSnapshotReference;
import com.alphascope.market.vo.BarView;
import com.alphascope.market.vo.MarketScoreView;
import com.alphascope.scheduler.vo.JobView;
import com.alphascope.signal.vo.SignalView;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.beans.Introspector;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * 显式 VO 转换与旧公开协议的兼容性验证，包含不同字段值、空值及冻结 JSON 指纹格式。
 *
 * @author chenxuefeng
 */
class ViewMappingContractTest {
  private final ObjectMapper json =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  /**
   * 对比已发布的 JSON 转换语义；旧转换器仅作为测试参照，不保留在生产代码。
   *
   * @return 覆盖全部替换转换入口的空对象及完整字段用例
   */
  @TestFactory
  Stream<DynamicTest> publishedViewFieldsRemainCompatible() {
    List<Class<?>> views =
        List.of(
            BarView.class,
            IndicatorView.class,
            SignalView.class,
            FinancialView.class,
            ThemeView.class,
            IndustryMetricView.class,
            CohortView.class,
            ThemeMemberView.class,
            ThemeScoreView.class,
            MarketScoreView.class,
            ReportSummary.class,
            JobView.class);
    return views.stream()
        .flatMap(
            view ->
                Stream.of(false, true)
                    .map(
                        populated ->
                            DynamicTest.dynamicTest(
                                view.getSimpleName() + (populated ? "_filled" : "_null"),
                                () -> {
                                  var method =
                                      Stream.of(view.getDeclaredMethods())
                                          .filter(candidate -> candidate.getName().equals("from"))
                                          .findFirst()
                                          .orElseThrow();
                                  Object row =
                                      method.getParameterTypes()[0].getConstructor().newInstance();
                                  if (populated) {
                                    for (var property :
                                        Introspector.getBeanInfo(row.getClass())
                                            .getPropertyDescriptors()) {
                                      if (property.getWriteMethod() != null) {
                                        property
                                            .getWriteMethod()
                                            .invoke(
                                                row,
                                                sample(
                                                    property.getPropertyType(),
                                                    property.getName()));
                                      }
                                    }
                                  }
                                  Object expected = json.convertValue(row, view);
                                  Object actual = method.invoke(null, row);
                                  assertEquals(
                                      json.valueToTree(expected), json.valueToTree(actual));
                                })));
  }

  @Test
  void typedCachePolicyKeepsCanonicalFingerprintBytes() {
    assertEquals(
        Jsons.write(Map.of("cacheMinutes", 1440L, "historicalReplay", "VERIFIED_STORED_V1")),
        Jsons.write(new MarketCacheSettings(1440L, "VERIFIED_STORED_V1")));
  }

  @Test
  void typedSnapshotReferencesKeepCanonicalFingerprintBytes() {
    LocalDate date = LocalDate.of(2026, 10, 30);
    assertEquals(
        Jsons.write(
            Map.of(
                "type",
                "market_breadth_snapshot",
                "id",
                12L,
                "hash",
                "abc",
                "date",
                date.toString())),
        Jsons.write(
            MarketSnapshotReference.builder()
                .type("market_breadth_snapshot")
                .id(12L)
                .hash("abc")
                .date(date)
                .build()));
    assertEquals(
        Jsons.write(
            Map.of(
                "type", "market_factor_snapshot", "id", 13L, "hash", "def", "symbol", "sh.600000")),
        Jsons.write(
            MarketSnapshotReference.builder()
                .type("market_factor_snapshot")
                .id(13L)
                .hash("def")
                .symbol("sh.600000")
                .build()));
  }

  private Object sample(Class<?> type, String name) {
    int seed = Math.floorMod(name.hashCode(), 500) + 1;
    if (type == String.class) return name + "_公开字段";
    if ((type == Long.class || type == long.class)) return (long) seed;
    if (type == Integer.class) return seed;
    if (type == Boolean.class) return Boolean.TRUE;
    if (type == BigDecimal.class) return BigDecimal.valueOf(seed, 2);
    if (type == LocalDate.class) return LocalDate.of(2026, 10, 1).plusDays(seed % 28);
    if (type == LocalDateTime.class) return LocalDateTime.of(2026, 10, 30, 15, 0).plusSeconds(seed);
    throw new IllegalArgumentException("未定义测试字段样本：" + type);
  }
}

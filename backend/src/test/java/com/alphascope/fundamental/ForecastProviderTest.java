package com.alphascope.fundamental;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.fundamental.provider.EastmoneyForecastProvider;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;

/**
 * 公开盈利预测解析、年度 EPS 及异常响应的验证。
 *
 * @author chenxuefeng
 */
class ForecastProviderTest {
  final ObjectMapper json = new ObjectMapper();

  ArrayNode rows() {
    var rows = json.createArrayNode();
    rows.addObject()
        .put("SECUCODE", "300476.SZ")
        .put("RATING_ORG_NUM", 7)
        .put("YEAR1", 2025)
        .put("YEAR_MARK1", "A")
        .put("EPS1", 4.3)
        .put("YEAR2", 2026)
        .put("YEAR_MARK2", "E")
        .put("EPS2", 8.17)
        .put("YEAR3", 2027)
        .put("YEAR_MARK3", "E")
        .put("EPS3", -1.2);
    return rows;
  }

  @Test
  void actualEpsIsNotPresentedAsForecastAndNegativeForecastsRemainValid() {
    var v = EastmoneyForecastProvider.parse("300476.SZ", rows());
    assertEquals(2, v.estimates().size());
    assertEquals(2026, v.estimates().getFirst().year());
    assertEquals(-1.2, v.estimates().getLast().eps());
    assertEquals(7, v.ratingAgencies());
  }

  @Test
  void wrongSecurityAndDuplicateSecurityAreRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> EastmoneyForecastProvider.parse("002837.SZ", rows()));
    var duplicate = rows();
    duplicate.add(duplicate.get(0));
    assertThrows(
        IllegalArgumentException.class,
        () -> EastmoneyForecastProvider.parse("300476.SZ", duplicate));
  }

  @Test
  void missingValuesAreNotFilledWithZero() {
    var rows = rows();
    ((ObjectNode) rows.get(0)).putNull("EPS2");
    assertThrows(
        IllegalArgumentException.class, () -> EastmoneyForecastProvider.parse("300476.SZ", rows));
  }

  @Test
  void hashesRepresentProviderContentAndAreStable() {
    var a = EastmoneyForecastProvider.parse("300476.SZ", rows());
    var b = EastmoneyForecastProvider.parse("300476.SZ", rows());
    assertEquals(a.hash(), b.hash());
    var modified = rows();
    ((ObjectNode) modified.get(0)).put("EPS2", 9.2);
    assertNotEquals(a.hash(), EastmoneyForecastProvider.parse("300476.SZ", modified).hash());
  }
}

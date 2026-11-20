package com.alphascope.market.calculator;

import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.ScoreCoverage;
import com.alphascope.market.entity.MarketBreadthSnapshot;
import com.alphascope.market.vo.UniverseCoverageView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 核对已保存的全A清单声明、交易所、指纹和逐证券截面；没有声明不能凭样本数量推断完整。
 *
 * @author chenxuefeng
 */
public final class UniverseCoverageCalculator {
  private UniverseCoverageCalculator() {}

  /**
   * 对一份真实供应商截面执行确定性覆盖核验。
   *
   * @param row 精确业务日的不可变清单与日线
   * @return 可直接供评分使用的准入证据及实际缺项
   */
  public static UniverseCoverageView calculate(MarketBreadthSnapshot row) {
    var missing = new ArrayList<String>();
    var exchanges = new HashMap<String, Integer>();
    var codes = new TreeSet<String>();
    var statuses = new HashMap<String, String>();
    var observed = new HashSet<String>();
    String url = null;
    boolean verified = false;
    try {
      JsonNode raw = new ObjectMapper().readTree(row.getRawJson());
      if (row.getContentHash() == null
          || !Jsons.hash(Jsons.write(Jsons.evidenceObject(raw))).equals(row.getContentHash())) {
        missing.add("市场清单和截面原文内容指纹不符");
      }
      if (!raw.path("universe").isArray() || !raw.path("daily").isArray())
        throw new IllegalArgumentException("清单或截面原文缺失");
      for (var member : raw.path("universe")) {
        String code = normalize(member.path("code").asText());
        if (code == null) continue;
        if (!codes.add(code)) throw new IllegalArgumentException("清单证券重复");
        String status = member.path("tradeStatus").asText();
        if ("UNKNOWN".equals(status)) missing.add(code + "的交易状态尚未核验");
        else if (!Set.of("0", "1").contains(status)) throw new IllegalArgumentException("清单交易状态未知");
        statuses.put(code, status);
        exchanges.merge(exchange(code), 1, Integer::sum);
      }
      for (var daily : raw.path("daily")) {
        String code = normalize(daily.path("code").asText());
        if (code == null) continue;
        if (!codes.contains(code)
            || !row.getTradeDate().toString().equals(daily.path("date").asText())
            || !observed.add(code)) throw new IllegalArgumentException("截面证券、日期或唯一性不符");
        if (!statuses.get(code).equals(daily.path("tradestatus").asText()))
          throw new IllegalArgumentException("清单与截面交易状态不符");
        if ("1".equals(statuses.get(code))) {
          if (new BigDecimal(daily.path("close").asText()).signum() <= 0
              || new BigDecimal(daily.path("amount").asText()).signum() < 0)
            throw new IllegalArgumentException("截面价格或成交额无效");
        }
      }
      statuses.forEach(
          (code, status) -> {
            if (status.equals("0")) observed.add(code);
          });
      for (String exchange : Set.of("SSE", "SZSE", "BSE")) {
        if (!exchanges.containsKey(exchange)) missing.add("未提供" + exchange + "的当日完整成员清单");
      }
      if (observed.size() != codes.size())
        missing.add("清单中" + (codes.size() - observed.size()) + "只交易证券缺少截面");
      JsonNode proof = raw.path("universeProof");
      if (HistoricalFullMarketAssembler.imported(row.getSource())
          && !raw.path("rosterEvidence").path("rosterSources").isObject()) {
        missing.add("历史导入档案缺少独立交易所名册证据，需按现行口径重新核验");
      }
      if (HistoricalFullMarketAssembler.PUBLIC_HISTORY_SOURCE.equals(row.getSource())) {
        var comparison = raw.path("rosterEvidence").path("comparison");
        if (!row.getTradeDate().toString().equals(comparison.path("date").asText())
            || !comparison.path("excludedInstruments").isObject()
            || comparison.path("shSzCount").asInt(-1)
                    - comparison.path("excludedInstruments").size()
                != exchanges.getOrDefault("SSE", 0) + exchanges.getOrDefault("SZSE", 0)
            || comparison.path("bseCount").asInt(-1) != exchanges.getOrDefault("BSE", 0))
          missing.add("历史重建缺少匹配的当日独立市场统计");
      }
      if (!"FULL_A".equals(row.getUniverseScope()) || !proof.isObject()) {
        missing.add("没有完整全A清单证明；当前实际范围为" + row.getUniverseScope());
      } else {
        url = proof.path("sourceUrl").asText();
        var declared = new HashSet<String>();
        proof.path("exchanges").forEach(value -> declared.add(value.asText()));
        if (!row.getTradeDate().toString().equals(proof.path("businessDate").asText())
            || !row.getSource().equals(proof.path("source").asText())
            || !"FULL_A".equals(proof.path("scope").asText())
            || !proof.path("completeRoster").asBoolean()
            || !declared.equals(Set.of("SSE", "SZSE", "BSE"))
            || proof.path("expectedCount").asInt(-1) != codes.size()
            || !Jsons.hash(Jsons.write(codes)).equals(proof.path("rosterSha256").asText())
            || !URI.create(url).getScheme().equals("https")
            || URI.create(url).getHost() == null) missing.add("完整清单声明、来源、数量或指纹不符");
      }
      if (codes.isEmpty()) missing.add("没有有效A股成员");
      verified = missing.isEmpty();
    } catch (Exception e) {
      missing.add("清单原文无法核验：" + e.getClass().getSimpleName());
    }
    return new UniverseCoverageView(
        row.getTradeDate(),
        verified ? "VERIFIED" : "PARTIAL",
        new ScoreCoverage("FULL_A", verified ? codes.size() : 0, observed.size(), verified),
        row.getSource(),
        row.getId(),
        row.getContentHash(),
        Map.copyOf(exchanges),
        List.copyOf(missing),
        url);
  }

  /**
   * 未采集时保留未知完整分母。
   *
   * @param date 请求业务日期
   * @return 具体的无证据状态
   */
  public static UniverseCoverageView unavailable(LocalDate date) {
    return new UniverseCoverageView(
        date,
        "NOT_AVAILABLE",
        new ScoreCoverage("FULL_A", 0, 0, false),
        null,
        null,
        null,
        Map.of(),
        List.of("当日没有可核验的供应商市场清单与截面"),
        null);
  }

  private static String normalize(String code) {
    if (code.matches("sh\\.6[0-9]{5}")) return code.substring(3) + ".SH";
    if (code.matches("sz\\.(?:00|30)[0-9]{4}")) return code.substring(3) + ".SZ";
    if (code.matches("bj\\.[0-9]{6}")) return code.substring(3) + ".BJ";
    if (code.matches("6[0-9]{5}\\.SH|(?:00|30)[0-9]{4}\\.SZ|[0-9]{6}\\.BJ")) return code;
    return null;
  }

  private static String exchange(String code) {
    return code.endsWith(".SH") ? "SSE" : code.endsWith(".SZ") ? "SZSE" : "BSE";
  }
}

package com.alphascope.operations.policy;

import com.alphascope.common.util.Jsons;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.TreeSet;

/**
 * 从本次实际证据提取来源；不把当前通道配置当成历史实际来源。
 *
 * @author chenxuefeng
 */
public final class SourceEvidence {
  private SourceEvidence() {}

  /**
   * 提取实际证据来源与批次。
   *
   * @param evidence 冻结输入或响应
   * @return 去重的实际来源和批次
   */
  public static References inspect(Object evidence) {
    JsonNode node =
        evidence instanceof JsonNode n ? n : Jsons.read(Jsons.write(evidence), JsonNode.class);
    Set<String> sources = new TreeSet<>(), batches = new TreeSet<>();
    walk(node, "", sources, batches, 0);
    return new References(Set.copyOf(sources), Set.copyOf(batches), material(node, "", 0));
  }

  /**
   * 来源别名按真实能力归一，不改写原始证据。
   *
   * @param value 原文来源
   * @return 许可登记代码
   */
  public static String canonical(String value) {
    return switch (value) {
      case "EASTMONEY_FREE" -> "EASTMONEY_FINANCIAL";
      case "EASTMONEY_PUBLIC" -> "EASTMONEY_FORECAST";
      default -> value;
    };
  }

  private static void walk(
      JsonNode node, String field, Set<String> sources, Set<String> batches, int depth) {
    if (depth > 64 || node == null) return;
    if (node.isObject()) {
      node.fields()
          .forEachRemaining(e -> walk(e.getValue(), e.getKey(), sources, batches, depth + 1));
      return;
    }
    if (node.isArray()) {
      node.forEach(n -> walk(n, field, sources, batches, depth + 1));
      return;
    }
    if (!node.isTextual()) return;
    String value = node.asText();
    if (Set.of("source", "inputSources", "sources", "sourcePermissions").contains(field)
        && value.matches("[A-Z_]{2,40}")
        && !Set.of("UNKNOWN", "DEMO", "MANUAL", "REGISTERED", "MULTI").contains(value))
      sources.add(canonical(value));
    if (Set.of("source_batch", "sourceBatch").contains(field)
        && value.matches("[A-Za-z0-9:_-]{1,128}")) batches.add(value);
    if ((field.endsWith("Json")
            || field.endsWith("_json")
            || Set.of("input", "evidence", "snapshot").contains(field))
        && (value.startsWith("{") || value.startsWith("["))) {
      try {
        walk(Jsons.read(value, JsonNode.class), field, sources, batches, depth + 1);
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("研究来源证据JSON无效", e);
      }
    }
  }

  private static boolean material(JsonNode node, String field, int depth) {
    if (node == null || depth > 64) return false;
    if (node.isObject()) {
      var it = node.fields();
      while (it.hasNext()) {
        var e = it.next();
        if (material(e.getValue(), e.getKey(), depth + 1)) return true;
      }
      return false;
    }
    if (node.isArray()) {
      for (var value : node) if (material(value, field, depth + 1)) return true;
      return false;
    }
    if (node.isNumber())
      return !Set.of(
              "id",
              "instrument_id",
              "instrumentId",
              "total",
              "page",
              "size",
              "revision",
              "attempt",
              "tokenUsage",
              "token_usage",
              "completedUnits",
              "totalUnits",
              "completed",
              "completed_units",
              "total_units",
              "experimentId")
          .contains(field);
    if (node.isTextual()
        && Set.of("status", "state").contains(field)
        && Set.of("TRIGGERED", "NOT_TRIGGERED", "STRONG", "WEAK", "NEUTRAL", "CONDITIONAL_MATCH")
            .contains(node.asText())) return true;
    if (node.isTextual()
        && (field.endsWith("Json")
            || field.endsWith("_json")
            || Set.of("input", "evidence", "snapshot").contains(field))
        && (node.asText().startsWith("{") || node.asText().startsWith("[")))
      return material(Jsons.read(node.asText(), JsonNode.class), field, depth + 1);
    return false;
  }

  /**
   * 实际来源及用于旧指标追溯的不可变价格批次。
   *
   * @author chenxuefeng
   * @param sources 来源代码
   * @param batches 原始批次
   * @param material 是否存在真实研究数值或判断
   */
  public record References(Set<String> sources, Set<String> batches, boolean material) {}
}

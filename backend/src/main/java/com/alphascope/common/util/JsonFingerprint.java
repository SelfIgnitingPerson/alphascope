package com.alphascope.common.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import java.io.IOException;
import java.util.TreeSet;

/**
 * 完整JSON的语义指纹：保留数组顺序并规范对象键与精确数字，避免MySQL格式化影响校验。
 *
 * @author chenxuefeng
 */
public final class JsonFingerprint {
  private static final ObjectMapper JSON =
      new ObjectMapper()
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);

  private JsonFingerprint() {}

  /**
   * 对冻结JSON计算精确语义SHA256。
   *
   * @param value 合法完整JSON
   * @return 与空白和对象键顺序无关、与数组顺序和实际值有关的指纹
   */
  public static String hash(String value) {
    try {
      return Jsons.hash(canonical(JSON.readTree(value)).toString());
    } catch (IOException e) {
      throw new IllegalArgumentException("冻结JSON无效", e);
    }
  }

  private static JsonNode canonical(JsonNode value) {
    if (value.isObject()) {
      var result = JSON.createObjectNode();
      var keys = new TreeSet<String>();
      value.fieldNames().forEachRemaining(keys::add);
      keys.forEach(key -> result.set(key, canonical(value.get(key))));
      return result;
    }
    if (value.isArray()) {
      var result = JSON.createArrayNode();
      value.forEach(item -> result.add(canonical(item)));
      return result;
    }
    return value.isNumber()
        ? DecimalNode.valueOf(value.decimalValue().stripTrailingZeros())
        : value;
  }
}

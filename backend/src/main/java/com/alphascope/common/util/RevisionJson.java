package com.alphascope.common.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * 原始修订的精确JSON归档编码，数值保存为字符串以避免MySQL JSON的浮点规范化丢失DECIMAL精度。
 *
 * @author chenxuefeng
 */
public final class RevisionJson {
  private static final ObjectMapper JSON =
      new ObjectMapper()
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
          .enable(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS);

  private RevisionJson() {}

  /**
   * 保留原JSON结构及每个数值的十进制表示，读取时由明确字段类型解析。
   *
   * @param value 未经过数据库JSON转换的完整原文
   * @return 数值为字符串的RAW_V2归档
   */
  public static String encode(String value) {
    try {
      return JSON.writeValueAsString(JSON.readTree(value));
    } catch (IOException e) {
      throw new IllegalArgumentException("修订JSON编码失败", e);
    }
  }
}

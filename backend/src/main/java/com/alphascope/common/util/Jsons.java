package com.alphascope.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 项目内部JSON编码和内容指纹工具，统一日期序列化供证据冻结使用。
 *
 * @author chenxuefeng
 */
public final class Jsons {
  private static final ObjectMapper JSON =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  private Jsons() {}

  public static <T> T read(String value, Class<T> type) {
    try {
      return JSON.readValue(value, type);
    } catch (IOException e) {
      throw new IllegalStateException("JSON解码失败", e);
    }
  }

  /** 仅用于冻结可扩展证据文档，业务查询和数据库写入不得借此退回无类型 Map。 */
  public static Map<String, Object> evidenceObject(Object value) {
    return JSON.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  public static String write(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("JSON编码失败", e);
    }
  }

  public static String hash(String input) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * 以流式读取计算文件内容SHA256，不把大文件读入内存。
   *
   * @param file 已确认允许读取的文件
   * @return 完整字节内容指纹
   * @throws IOException 文件无法读取
   */
  public static String hashFile(Path file) throws IOException {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      try (var input = Files.newInputStream(file)) {
        byte[] bytes = new byte[65536];
        int count;
        while ((count = input.read(bytes)) != -1) {
          digest.update(bytes, 0, count);
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}

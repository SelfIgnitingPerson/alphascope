package com.alphascope.market.provider.baostock;

import com.alphascope.common.diagnostic.baostock.BaoStockApiException;
import com.alphascope.common.diagnostic.baostock.BaoStockErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;

/**
 * 上游明确拒绝访问后停止网络请求。状态保存在本机运行目录，重启不会绕过限制。
 *
 * @author chenxuefeng
 */
public final class BaoStockAccessGuard {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final Path file;

  public BaoStockAccessGuard(Path file) {
    this.file = file;
  }

  public static BaoStockAccessGuard standard() {
    return new BaoStockAccessGuard(
        Path.of(System.getProperty("alphascope.root", "."), ".runtime", "baostock-access.json"));
  }

  public synchronized boolean blocked() {
    return state().blocked();
  }

  /**
   * 只读取得原有保护证据；不测试网络、不解除保护，也不回写时间。
   *
   * @return 是否阻止请求、实际拒绝代码和原始观察时间；缺失字段保持空
   */
  public synchronized AccessState state() {
    if (!Files.exists(file)) {
      return new AccessState(false, null, null, null);
    }
    try {
      var value = JSON.readTree(Files.readString(file));
      String code = value.path("code").asText("");
      String observed = value.path("observedAt").asText("");
      return new AccessState(
          value.path("blocked").asBoolean(true),
          code.matches("[1-9][0-9]{0,9}") ? code : null,
          observed.isBlank() ? null : Instant.parse(observed),
          BaoStockErrors.safeMessage(code, value.path("errorMessage").asText(null)));
    } catch (IOException | java.time.format.DateTimeParseException e) {
      throw new IllegalStateException("BAOSTOCK_ACCESS_STATE_UNREADABLE", e);
    }
  }

  /**
   * 本机已保存的拒绝证据，不代表当前上游状态。
   *
   * @author chenxuefeng
   */
  public record AccessState(
      /** 是否阻止后续连接。 */
      boolean blocked,
      /** 原始受控拒绝代码。 */
      String code,
      /** 原始拒绝时间，读取不更新。 */
      Instant observedAt,
      /** 命中安全模板的原始消息；旧记录缺失为空，不补造。 */
      String errorMessage) {}

  public void check() {
    var saved = state();
    if (saved.blocked()) {
      if (saved.code() != null) throw new BaoStockApiException(saved.code(), saved.errorMessage());
      throw new IllegalStateException("ACCESS_RESTRICTED：已停止请求，原始拒绝代码未记录");
    }
  }

  /**
   * 保存已确认的黑名单拒绝及安全原文，不把其他业务错误当成封禁。
   *
   * @param failure 上游10001011业务异常
   */
  public synchronized void restrict(BaoStockApiException failure) {
    if (!"10001011".equals(failure.errorCode()))
      throw new IllegalArgumentException("只有黑名单拒绝才能开启访问保护");
    try {
      Files.createDirectories(file.toAbsolutePath().getParent());
      var temporary =
          Files.createTempFile(file.toAbsolutePath().getParent(), "baostock-access-", ".json");
      Map<String, Object> record = new java.util.LinkedHashMap<>();
      record.put("blocked", true);
      record.put("code", failure.errorCode());
      record.put("observedAt", Instant.now().toString());
      record.put("reason", "上游拒绝访问；确认恢复前不自动重试");
      var safe = BaoStockErrors.safeMessage(failure.errorCode(), failure.errorMessage());
      if (safe != null) record.put("errorMessage", safe);
      Files.writeString(temporary, JSON.writeValueAsString(record));
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IllegalStateException("BAOSTOCK_ACCESS_STATE_WRITE_FAILED", e);
    }
  }
}

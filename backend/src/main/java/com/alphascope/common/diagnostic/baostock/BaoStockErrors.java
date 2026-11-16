package com.alphascope.common.diagnostic.baostock;

import com.alphascope.common.diagnostic.FailureDiagnosis;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 从已校验的官方SDK错误表解释供应商数字码；原文只公开命中安全模板的短消息。
 *
 * @author chenxuefeng
 */
public final class BaoStockErrors {
  private static final Map<String, Entry> ENTRIES = load();

  private BaoStockErrors() {}

  /**
   * 显示数字码、官方代码释义和原文记录情况，未知码仍保留其数字值。
   *
   * @param code 实际供应商数字码
   * @param message 捕获的原文；旧记录缺失时为空
   * @return 受控供应商证据，不含任意原文、凭据或地址
   */
  public static FailureDiagnosis.ProviderError evidence(String code, String message) {
    if (code == null || !code.matches("[0-9]{1,10}") || code.equals("0")) return null;
    Entry entry = ENTRIES.get(code);
    String meaning = entry == null ? "未收录的供应商错误码" : entry.meaning();
    String safe = safeMessage(code, message);
    return new FailureDiagnosis.ProviderError(
        "BAOSTOCK",
        code,
        safe,
        meaning,
        message == null || message.isBlank()
            ? "NOT_RECORDED"
            : safe == null ? "WITHHELD" : "UPSTREAM_SAFE_TEXT");
  }

  /**
   * 将已确认的上游业务错误转成可持久保存的代码释义和操作建议。
   *
   * @param code 实际供应商数字码
   * @param message 原文或空；未记录不能伪装为原文
   * @return 含供应商码的受控诊断
   */
  public static FailureDiagnosis diagnose(String code, String message) {
    var evidence = evidence(code, message);
    if (evidence == null) throw new IllegalArgumentException("BaoStock业务错误码无效");
    var entry = ENTRIES.get(code);
    String category = entry == null ? "OTHER" : entry.category();
    String reason = "BaoStock返回的错误码对应“" + evidence.meaning() + "”；这是代码释义，是否保存了原文另行标明。";
    String action =
        switch (category) {
          case "BLACKLIST" ->
              "停止采集，查询官方访问统计中的待释放时间、日请求数和并发数；按官方恢复情况处理，其他情况联系官网管理员。不能从10001011单独推算触发原因。";
          case "PERMISSION" -> "核验这一次接口所需的账号/API权限与当前节点配置；不要把权限拒绝解释成黑名单，也不要重复登录重试。";
          case "SESSION" -> "检查是否重复启动采集或会话已失效；保持一个串行会话，排除重复连接后有限重试。";
          case "CLIENT_VERSION" -> "维护人员核对官方协议版本并升级适配，不能通过重复请求修复版本不兼容。";
          case "AUTHENTICATION" -> "核对该节点的登录、激活或APIKey配置；只在本机填写凭据，不在聊天或日志中公开。";
          case "NETWORK" -> "核对网络、代理和连接稳定性，按实际供应商码区分连接、发送、接收和超时；稍后有限重试。";
          case "PARAMETER" -> "维护人员核对证券代码、日期、字段、品种及单次条数；修正请求后再试，不为参数错误购买权限。";
          case "PROTOCOL" -> "维护人员核对报文、解压和解析适配；保留诊断代码，不绕过字段或完整性校验。";
          case "SERVICE" -> "供应商报告系统错误，暂停本项并稍后有限重试；持续出现时向官方提供错误码和发生时间。";
          default -> "保留此数字码、任务编号、能力及发生时间，核对官方更新；尚无可靠释义时不推断为网络或付费问题。";
        };
    if (category.equals("BLACKLIST"))
      reason = "供应商将该次访问标记为黑名单用户；本机保留的保护不证明此刻上游仍受限。具体触发条件和释放时间以官网记录为准。";
    return new FailureDiagnosis(
        "BAOSTOCK_API_ERROR_" + code,
        "BaoStock · " + code + " · " + evidence.meaning(),
        reason,
        "本项请求未完成，已有真实历史保留；不据此声称其他能力或其他来源也不可用。",
        action,
        evidence);
  }

  /**
   * 解释本次协议异常，原文只经安全模板筛选后进入视图。
   *
   * @param failure 客户端捕获的供应商业务异常
   * @return 可公开的诊断
   */
  public static FailureDiagnosis diagnose(BaoStockApiException failure) {
    return diagnose(failure.errorCode(), failure.errorMessage());
  }

  /**
   * 只允许官方短释义和已经核验的黑名单提示原文，拒绝任意附加参数、地址、Token或脚本。
   *
   * @param code 已确认数字码
   * @param message 实际原文
   * @return 命中模板的原文；未命中或没有记录时为空
   */
  public static String safeMessage(String code, String message) {
    if (message == null || message.isBlank() || message.length() > 120) return null;
    String text = message.strip();
    var entry = ENTRIES.get(code);
    if (entry != null && text.equals(entry.meaning())) return text;
    if ("10001011".equals(code) && Set.of("黑名单用户，请与管理员联系", "黑名单用户,请与管理员联系").contains(text))
      return text;
    return null;
  }

  private static Map<String, Entry> load() {
    try (var stream = BaoStockErrors.class.getResourceAsStream("/market/baostock-errors.json")) {
      if (stream == null) throw new IllegalStateException("缺少BaoStock错误码资源");
      var values = new ObjectMapper().readTree(stream).path("entries");
      var result = new LinkedHashMap<String, Entry>();
      for (var item : values) {
        String code = item.path("code").asText();
        if (!code.matches("[0-9]{8}") || result.containsKey(code))
          throw new IllegalStateException("BaoStock错误码资源无效");
        result.put(code, new Entry(item.path("meaning").asText(), item.path("category").asText()));
      }
      if (result.isEmpty()) throw new IllegalStateException("BaoStock错误码资源为空");
      return Map.copyOf(result);
    } catch (IOException error) {
      throw new IllegalStateException("BaoStock错误码资源读取失败", error);
    }
  }

  /**
   * 已核验官方SDK代码的中文含义及维护处理分类。
   *
   * @author chenxuefeng
   */
  private record Entry(
      /** 已核验的官方SDK中文释义。 */
      String meaning,
      /** 对应维护步骤的分类。 */
      String category) {}
}

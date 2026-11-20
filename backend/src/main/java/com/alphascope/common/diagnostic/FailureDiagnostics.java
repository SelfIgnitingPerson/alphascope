package com.alphascope.common.diagnostic;

import com.alphascope.common.diagnostic.baostock.BaoStockApiException;
import com.alphascope.common.diagnostic.baostock.BaoStockErrors;
import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.response.ResultCode;
import com.alphascope.common.task.TaskStoppedException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import javax.net.ssl.SSLException;

/**
 * 任务与通道共用的故障分类；保留可解释代码，未知异常不猜测为网络故障或付费权限不足。
 *
 * @author chenxuefeng
 */
public final class FailureDiagnostics {
  private FailureDiagnostics() {}

  private static final String IMPACT = "本次操作或能力未完成；相关数据不能据此视为完整，已有历史证据保留。";
  private static final Map<String, FailureDiagnosis> CATALOG =
      Map.ofEntries(
          entry("CANCELLED", "任务已取消", "已收到用户取消请求，执行已经停止。", "核对已完成阶段；仍需要结果时通过原任务入口明确重试。"),
          entry(
              "TIMED_OUT",
              "阶段执行超时",
              "阶段到达配置的执行截止时间；心跳正常也不会无限延长阶段。",
              "检查最后进展和阶段编号，排除卡住的调用后再有限重试，不要仅延长时限掩盖故障。"),
          entry("INTERRUPTED", "任务执行中断", "执行线程或进程被中断，未确认阶段完成。", "确认服务恢复后重试，重新核验已保存输入，不把未完成阶段当成功。"),
          entry(
              "ACCESS_RESTRICTED",
              "访问保护开启",
              "此前记录了上游拒绝访问；本机保护状态不证明此刻上游仍受限，也不表示必须购买会员。",
              "核对官方限制和解封情况，确认恢复后由维护人员解除保护；不要反复重试或切换节点绕过。"),
          entry(
              "DAILY_BUDGET_EXHAUSTED",
              "本机当日请求预算耗尽",
              "登录、分页和失败重试也占预算，重启不会重置。",
              "等待上海下一自然日，或复用完整同源缓存；减少重复补采，不删除计数文件。"),
          entry(
              "SESSION_BUSY",
              "已有采集进程占用连接",
              "同一运行目录中另一个客户端持有免费节点会话锁。",
              "等待正在运行的采集结束，或停止重复启动的本项目进程；不要并发采集。"),
          entry(
              "POLICY_STATE_UNREADABLE",
              "本机保护或预算记录异常",
              "本机控制记录无法可靠读取或写入，已保守停止请求，没有按零处理。",
              "检查运行目录权限、磁盘空间和记录完整性；不要直接删除记录重置额度。"),
          entry(
              "DNS_FAILURE",
              "供应商域名解析失败",
              "连接异常链中记录了域名解析失败；尚未连接到供应商。",
              "检查本机网络、DNS与代理配置；网络恢复后重试，付费权限不能解决此问题。"),
          entry(
              "CONNECTION_FAILED",
              "无法建立供应商连接",
              "TCP连接未建立；具体是服务关闭、路由还是防火墙尚需核对。",
              "检查网络、代理、防火墙及供应商服务状态；稍后有限重试。"),
          entry(
              "NETWORK_TIMEOUT",
              "供应商连接或读取超时",
              "请求在配置期限内未完成；不能据此认定供应商没有数据。",
              "稍后有限重试；持续超时时核对网络和通道状态，不重复提交整批任务。"),
          entry("TLS_FAILURE", "安全连接握手失败", "异常链记录了TLS或证书握手错误。", "检查系统时间、证书、JDK和代理；不要关闭证书校验。"),
          entry(
              "CONNECTION_CLOSED",
              "行情连接提前关闭",
              "异常链记录了连接重置、Socket关闭或响应结束过早；尚不能确定是供应商、代理还是网络设备关闭连接。",
              "检查代理和网络稳定性，稍后只重试本项；没有收到明确HTTP拒绝时，不解释为会员或黑名单问题。"),
          entry(
              "TRANSPORT_IO_FAILURE",
              "行情传输未完成",
              "已确认发生IO异常，但记录不足以确认DNS、超时、HTTP拒绝或连接重置中的哪一种。",
              "按失败时间、能力和任务编号核对服务日志中的异常链；先检查网络与代理，不购买权限来解决未确认的原因。"),
          entry(
              "ACCESS_COOLDOWN",
              "公开来源访问冷却中",
              "此前记录了HTTP 403或429后启用持久冷却；当前没有再次访问，不代表刚刚又被拒绝。",
              "等待所示冷却截止，核对来源访问规则及请求频率；不重启清除冷却，也不切换主机绕过。"),
          entry(
              "RESPONSE_INVALID",
              "供应商响应未通过校验",
              "返回数据的格式、字段、复权因子或窗口完整性不符合当前能力要求。",
              "查看缺失日期与字段，核对接口变化或等待完整数据；不要跳过校验强行生成结论。"),
          entry(
              "HTTP_FAILURE",
              "供应商HTTP请求未成功",
              "上游HTTP响应未满足要求；当前记录不足以判定是限流、权限还是临时故障。",
              "查看同能力的其他通道；持续失败时携带任务编号或错误编号核对后台日志。"),
          entry(
              "ACCESS_DENIED",
              "访问授权未满足",
              "数据源授权准入或上游权限拒绝了此项能力；不等于其他免费通道不可用。",
              "核对通道页的具体能力和产品授权登记；按实际拒绝原因处理，不先假定需要购买会员。"),
          entry(
              "NOT_CONFIGURED",
              "通道未配置",
              "该能力所需的配置尚未提供。",
              "按配置手册填写本机配置，或选择具有该能力的免费通道；不要在聊天中发送Token。"),
          entry(
              "UNSUPPORTED",
              "此通道不支持该项能力",
              "代码没有为此通道实现该能力；这是能力范围，不是网络失败。",
              "使用声明支持该能力的其他通道；重复请求此通道不会补齐能力。"),
          entry("COOLDOWN", "通道暂处冷却期", "最近请求失败后暂停发送；冷却不是新的连通性检测结果。", "结合最近错误处理，等待页面所示冷却截止时间后再尝试。"),
          entry(
              "NO_AVAILABLE_CHANNEL",
              "没有完整可用的数据通道",
              "所有适用通道均未返回完整可验证数据；具体原因可能不同，不能统一解释为网络或会员问题。",
              "打开行情通道页，逐项查看本次所需能力的诊断；历史报告可选已完成交易日补跑。"),
          entry(
              "CLOSE_NOT_READY",
              "当日收盘数据尚未就绪",
              "当前日期的收盘行情尚未形成。",
              "选择前一已完成交易日；当日最早15:30后提交，之后仍需等待供应商发布，不自动改换日期。"),
          entry(
              "UNCLASSIFIED_FAILURE",
              "原因尚未分类",
              "当前证据不足以判断具体原因；异常类型本身不能证明是网络、权限或数据问题。",
              "保留任务编号、失败阶段及时间；接口错误另带错误编号，交由维护人员查对应日志。"));

  private static Map.Entry<String, FailureDiagnosis> entry(
      String code, String title, String reason, String action) {
    return Map.entry(code, new FailureDiagnosis(code, title, reason, IMPACT, action));
  }

  /**
   * 根据受控代码或状态返回说明，正常状态返回空，未知代码明确标记未分类。
   *
   * @param value 公开错误代码或通道状态
   * @return 不包含原始value内容的受控说明
   */
  public static FailureDiagnosis code(String value) {
    if (value == null
        || value.isBlank()
        || java.util.Set.of("AVAILABLE", "SUCCESS", "NOT_CHECKED").contains(value)) return null;
    String key = value.split("[：: ]", 2)[0];
    if (key.matches("BAOSTOCK_API_ERROR_[1-9][0-9]{0,9}")) {
      return BaoStockErrors.diagnose(key.substring("BAOSTOCK_API_ERROR_".length()), null);
    }
    if (key.equals("BAOSTOCK_ACCESS_RESTRICTED_10001011")) {
      return BaoStockErrors.diagnose("10001011", null);
    }
    if (key.equals("DATABASE_SORT_MEMORY"))
      return new FailureDiagnosis(
          key,
          "数据库读取大字段时排序内存不足",
          "数据库返回1038，本次排序查询未完成；已存原始截面仍然保留",
          IMPACT,
          "维护人员先定位主键再读取原文，修复后在原任务重试；不直接放大MySQL全局内存配置。");
    if (key.startsWith("FULL_A_"))
      return new FailureDiagnosis(
          key,
          "三所全A证据未齐",
          switch (key) {
            case "FULL_A_ROSTER_DATE_MISSING" -> "所选日期没有可匹配的北交所供应商清单，不能用当前名单倒填历史";
            case "FULL_A_ROSTER_PROOF_MISSING" -> "截面缺少原始分页数量或来源证明，不能确认清单完整";
            case "FULL_A_EXCHANGE_MEMBERS_MISMATCH" -> "沪深北目录与报价中的交易所成员未对齐，尚不能证明三所覆盖";
            case "FULL_A_DAILY_COVERAGE_INCOMPLETE" -> "当日目录、行情或停牌证据未通过完整截面核验";
            case "FULL_A_FACTOR_WINDOW_INCOMPLETE" -> "价格来源的同源复权因子没有覆盖完整历史窗口";
            default -> "三所成员格式、去重或输入证明未通过核验";
          },
          IMPACT,
          "到行情通道查看三所缺口日期和能力；优先复用同日归档，取得真实历史资料后再补齐，不反复用当前截面请求过去日期。");
    if (CATALOG.containsKey(key)) return CATALOG.get(key);
    if (key.contains("ACCESS_RESTRICTED") || key.equals("10001011"))
      return CATALOG.get("ACCESS_RESTRICTED");
    if (key.contains("DAILY_BUDGET_EXHAUSTED")) return CATALOG.get("DAILY_BUDGET_EXHAUSTED");
    if (key.contains("SESSION_BUSY")) return CATALOG.get("SESSION_BUSY");
    if (key.startsWith("BAOSTOCK_")
        && (key.contains("STATE_") || key.contains("POLICY_") || key.contains("WRITE_FAILED")))
      return CATALOG.get("POLICY_STATE_UNREADABLE");
    if (key.contains("NOT_CONFIGURED")) return CATALOG.get("NOT_CONFIGURED");
    if (key.startsWith("PUBLIC_MARKET_ACCESS_COOLDOWN")) return CATALOG.get("ACCESS_COOLDOWN");
    if (key.contains("ACCESS_DENIED") || key.equals("FORBIDDEN"))
      return CATALOG.get("ACCESS_DENIED");
    if (key.equals("HTTP_BODY_LIMIT_EXCEEDED"))
      return new FailureDiagnosis(
          key,
          "供应商响应超过安全大小上限",
          "正文读取达到上限后已停止；未使用截断数据，不是账号权限不足。",
          IMPACT,
          "核对接口是否改变返回结构，缩小合法查询窗口或修正分页；不要无限扩大内存上限。");
    if (key.equals("SINA_CALENDAR_INSUFFICIENT"))
      return new FailureDiagnosis(
          key,
          "新浪日历未覆盖查询区间",
          "已取得并解析日历，但其最早或最晚日期未覆盖整个请求窗口；这不是连接被拒绝。",
          IMPACT,
          "优先使用已核验年度的交易所日历；范围外等待源更新或配置其他真实日历，不把未返回的日期一律当休市。");
    if (key.equals("SINA_FACTOR_MISSING"))
      return new FailureDiagnosis(
          key,
          "新浪缺少所需复权因子",
          "已返回因子响应，但因子列表为空，或行情日期之前没有可用因子；无法验证整段同源复权价格。",
          IMPACT,
          "核对请求起始日、证券上市日和因子响应；等待来源补齐或整窗切换。不能从别家拼因子，也不能默认填1。");
    if (key.matches("SINA_MISSING_[A-Z]+"))
      return new FailureDiagnosis(
          key,
          "新浪行情字段缺失或无效",
          "解码后某个必需数值字段缺失或无法解析；具体字段见诊断代码，不代表没有该股票。",
          IMPACT,
          "由维护人员按字段核对解码适配；尤其指数成交额不可靠时保持不支持，不能填0后继续分析。");
    if (key.startsWith("INVALID_SINA_")
        || key.equals("SINA_DECODE_FAILED")
        || key.equals("SINA_DECODE_TIMEOUT")
        || key.equals("SINA_FACTOR_INVALID"))
      return new FailureDiagnosis(
          key,
          "新浪响应解析未完成",
          "已进入本地响应解析或解码阶段，返回结构、编码或因子格式未通过验证；解码超时也属于本地处理边界。",
          IMPACT,
          "维护人员核对固定解码器和供应商响应结构；不要执行远端脚本，也不要据此认定需要会员。");
    if (key.matches(".*HTTP_[1-5][0-9]{2}")) {
      String status = key.substring(key.length() - 3);
      String action =
          status.equals("429")
              ? "按上游要求等待，减少请求频率与并发；不要立即连续重试。"
              : status.equals("401") || status.equals("403")
                  ? "核对访问准入、上游访问规则与代理；不能仅凭此状态断言需要付费。"
                  : "稍后有限重试，持续发生时核对供应商服务状态；本地请求编号可用于定位。";
      return new FailureDiagnosis(
          "HTTP_" + status,
          "上游返回HTTP " + status,
          "本次已记录供应商HTTP状态；HTTP状态本身不能证明更深层业务原因。",
          IMPACT,
          action);
    }
    if (key.startsWith("LIFECYCLE_"))
      return new FailureDiagnosis(
          key,
          "证券生命周期尚未核实",
          "上市日期、退市日期或证券身份没有取得可靠对应证据；旧精选层日期不能直接当成北交所上市日。",
          "暂停该证券完整历史初始化，不把上市前区间当缺失行情。",
          "管理员核对公开资料后，在证券生命周期维护入口补齐日期和HTTPS依据，再重试；不以首条K线日期代替上市日期。");
    if (key.equals("DATABASE_SORT_MEMORY"))
      return new FailureDiagnosis(
          key,
          "数据库读取大字段时排序内存不足",
          "数据库返回1038，本次排序查询未完成；已存原始截面仍然保留",
          IMPACT,
          "维护人员先定位主键再读取原文，修复后在原任务重试；不直接放大MySQL全局内存配置。");
    if (key.startsWith("FULL_A_"))
      return new FailureDiagnosis(
          key,
          "三所同日证据尚未完整",
          "清单日期、北交所成员、价格覆盖或同源历史窗口未通过核验。",
          "保留已有沪深观察及真实目录，全A总评级继续UNKNOWN。",
          "查看三所核验任务的精确日期与缺项；补齐真实证据后重试，不倒填历史或拼接不同来源因子。");
    if (key.startsWith("LIFECYCLE_"))
      return new FailureDiagnosis(
          key,
          "证券生命周期未核实",
          "上市日期、证券身份或退市边界尚未通过来源核验；旧精选层日期不能直接当作北交所上市日。",
          "暂停该证券完整历史采集，不把上市前区间当缺失，也不把缺失当停牌。",
          "管理员在运行检查补录公开公告依据与上市/退市日期后重试；不要拿首条K线代替上市日。");
    if (key.equals("DATABASE_SORT_MEMORY"))
      return new FailureDiagnosis(
          key,
          "数据库读取大字段时排序内存不足",
          "数据库返回1038，本次排序查询未完成；已存原始截面仍然保留",
          IMPACT,
          "维护人员先定位主键再读取原文，修复后在原任务重试；不直接放大MySQL全局内存配置。");
    if (key.startsWith("FULL_A_"))
      return new FailureDiagnosis(
          key,
          "三所同日证据仍不完整",
          "当日清单、交易所成员、价格/停牌或同源因子未通过逐项核验。",
          "保留既有沪深观察和冻结报告，完整评级继续UNKNOWN。",
          "按目录日期核对三所分母；历史日期需使用真实归档，同源历史不足时继续积累，不倒填。");
    if (key.startsWith("PUBLIC_ROSTER_"))
      return new FailureDiagnosis(
          key,
          "当前证券目录未通过完整核验",
          "供应商目录的分页数量、代码唯一性、排序、交易所范围或前后确认发生异常；不把已返回部分名单当作完整目录。",
          "本次目录不发布，保留此前有效归档；当前日期缺项不改成零。",
          "查看诊断代码区分PAGE、COUNT、MEMBER或DATE；稍后有限重试，持续出现时由维护人员核对供应商目录协议。");
    if (key.equals("UNIVERSE_SOURCE_NOT_CONFIGURED"))
      return new FailureDiagnosis(
          key, "未启用北交所目录来源", "当前真实通道配置未包含新浪。", "不能执行此目录补齐。", "由管理员在真实通道配置启用SINA；PUBLIC仍需对应来源使用依据。");
    if (key.equals("UNIVERSE_EVIDENCE_INVALID") || key.equals("UNIVERSE_SAVE_INVALID"))
      return new FailureDiagnosis(
          key, "目录归档完整性核验失败", "成员、分页证据或内容指纹不一致。", "该记录不会用于覆盖判断。", "维护人员按目录日期与归档编号核对存储，不覆盖或伪造原指纹。");
    if (key.equals("PUBLIC_MARKET_HISTORY_NOT_ARCHIVED") || key.equals("HISTORY_NOT_ARCHIVED"))
      return new FailureDiagnosis(
          "HISTORY_NOT_ARCHIVED",
          "历史截面尚未归档",
          "该免费接口没有提供所请求日期的已归档截面，当前目录不能倒填历史清单。",
          IMPACT,
          "查看已归档日期，逐日积累真实快照；没有可靠历史来源时保留缺项，不伪造历史成分。");
    if (key.equals("PUBLIC_MARKET_NOT_CLOSED")) return CATALOG.get("CLOSE_NOT_READY");
    if (key.equals("FULL_A_DAILY_COVERAGE_INCOMPLETE"))
      return new FailureDiagnosis(
          key, "三所行情或停牌记录不齐", "通道响应未覆盖当日全部应有证券。", IMPACT, "检查同日交易和停牌缺项，使用能提供完整截面的通道重新核验。");
    if (key.equals("FULL_A_ROSTER_DATE_MISSING")
        || key.equals("FULL_A_ROSTER_PROOF_MISSING")
        || key.equals("FULL_A_EXCHANGE_MEMBERS_MISMATCH"))
      return new FailureDiagnosis(
          key, "三所清单核验未通过", "证券目录的日期、完整性或北交所成员未与独立目录对齐。", IMPACT, "检查所选日真实证券清单与通道覆盖，避免用其他日期清单替代。");
    if (key.equals("PUBLIC_MARKET_NOT_TRADING_DAY") || key.equals("NOT_TRADING_DAY"))
      return new FailureDiagnosis(
          "NOT_TRADING_DAY", "所选日期不是交易日", "该日期不产生正常交易日截面。", IMPACT, "明确选择有效交易日，不把非交易日行情补成0。");
    if (key.contains("HTTP_")) return CATALOG.get("HTTP_FAILURE");
    if (key.equals("INCOMPLETE_HISTORY") || key.equals("STALE_DATA"))
      return new FailureDiagnosis(
          key,
          "返回的行情日期不齐",
          "来源已返回行情，但没有覆盖所需的全部交易日；不能据此认定是网络故障，也不能确认只是等待发布。",
          IMPACT,
          "查看主提示中的缺失日期。等待来源补齐后重试，或明确选择较早的研究日期；不把前一日价格充当当日行情。");
    if (key.equals("EMPTY_RESPONSE"))
      return new FailureDiagnosis(
          key,
          "来源未返回行情记录",
          "请求结果为空；现有证据不足以确定是日期、证券范围还是上游异常。",
          IMPACT,
          "核对证券代码和交易日期，查看其他支持该能力的来源，不把空响应填成0。");
    if (key.contains("RESPONSE_INVALID")
        || key.contains("FACTOR_")
        || key.contains("SCHEMA_")
        || key.contains("MISMATCH")
        || key.startsWith("INVALID_")
        || key.contains("FIELDS_MISSING")
        || key.matches("SINA_MISSING_[A-Z]+")
        || key.contains("CALENDAR_INSUFFICIENT")) return CATALOG.get("RESPONSE_INVALID");
    if (key.equals("RETRY_READY"))
      return new FailureDiagnosis(
          "RETRY_READY",
          "冷却已结束，可有限重试",
          "允许再次尝试，不代表已确认恢复连通。",
          IMPACT,
          "先核对最近错误原因，再发起一次有限重试，不重复提交整批任务。");
    return CATALOG.get("UNCLASSIFIED_FAILURE");
  }

  /**
   * 有界检查异常链中的已知代码与网络异常类型，不公开原始异常文本。
   *
   * @param failure 实际捕获的异常
   * @return 证据支持的诊断，未知时保留未分类
   */
  public static FailureDiagnosis from(Throwable failure) {
    FailureDiagnosis fallback = CATALOG.get("UNCLASSIFIED_FAILURE");
    for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
      if (failure instanceof java.sql.SQLException sql && sql.getErrorCode() == 1038)
        return code("DATABASE_SORT_MEMORY");
      if (failure instanceof BaoStockApiException api) {
        return BaoStockErrors.diagnose(api);
      }
      if (failure instanceof TaskStoppedException stopped)
        return CATALOG.get(stopped.getReason().name());
      String message = failure.getMessage();
      if (failure instanceof BusinessException business
          && (business.getCode() == ResultCode.FORBIDDEN
              || (message != null && message.startsWith("数据源使用范围尚未就绪："))))
        return CATALOG.get("ACCESS_DENIED");
      if (failure instanceof BusinessException
          && message != null
          && message.startsWith("当前日期的收盘行情尚未形成")) return CATALOG.get("CLOSE_NOT_READY");
      if (failure instanceof UnknownHostException) return CATALOG.get("DNS_FAILURE");
      if (failure instanceof ConnectException) return CATALOG.get("CONNECTION_FAILED");
      if (failure instanceof SocketTimeoutException || failure instanceof HttpTimeoutException)
        return CATALOG.get("NETWORK_TIMEOUT");
      if (failure instanceof java.util.concurrent.TimeoutException)
        return CATALOG.get("NETWORK_TIMEOUT");
      if (failure instanceof SSLException) return CATALOG.get("TLS_FAILURE");
      if (failure instanceof java.io.EOFException || failure instanceof java.net.SocketException)
        return CATALOG.get("CONNECTION_CLOSED");
      if (failure instanceof UnsupportedOperationException) return CATALOG.get("UNSUPPORTED");
      if (failure instanceof SecurityException) return CATALOG.get("ACCESS_DENIED");
      // 只接受代码前缀，不根据任意供应商中文正文推断原因。
      if (message != null && message.matches("[A-Z_0-9]{1,80}([：: ].*)?")) {
        var known = code(message);
        if (known != null && !known.code().equals("UNCLASSIFIED_FAILURE")) return known;
      }
      if (failure instanceof java.io.IOException) fallback = CATALOG.get("TRANSPORT_IO_FAILURE");
      if (message != null
          && (message.startsWith("所有真实行情通道不可用（不会回退模拟）：") || message.startsWith("真实数据能力不可用（")))
        fallback = aggregate(message);
    }
    return fallback;
  }

  /**
   * 解释旧任务中已经保存的受控代码，不更新记录，也不把历史错误当作当前探测。
   *
   * @param recorded 已保存公开错误
   * @return 已知代码的当前释义，无法识别时明确未分类
   */
  public static FailureDiagnosis recorded(String recorded) {
    if (recorded != null) {
      for (String marker : java.util.List.of("所有真实行情通道不可用（不会回退模拟）：", "真实数据能力不可用（")) {
        int at = recorded.indexOf(marker);
        if (at >= 0) return aggregate(recorded.substring(at));
      }
      if (recorded.startsWith("当前日期的收盘行情尚未形成")) return CATALOG.get("CLOSE_NOT_READY");
      if (recorded.matches("[A-Z_0-9]{1,80}")) return code(recorded);
    }
    return CATALOG.get("UNCLASSIFIED_FAILURE");
  }

  private static String sourceName(String code) {
    return switch (code) {
      case "EXCHANGE_CALENDAR" -> "交易所日历";
      case "BAOSTOCK" -> "证券宝";
      case "SINA" -> "新浪";
      case "EASTMONEY" -> "东方财富";
      case "TUSHARE" -> "Tushare";
      default -> "其他来源";
    };
  }

  /** 冻结聚合失败中的受控通道代码及建议，重启后仍能阅读，不依赖进程内最近状态。 */
  private static FailureDiagnosis aggregate(String message) {
    var base = CATALOG.get("NO_AVAILABLE_CHANNEL");
    int at = message.indexOf('：');
    if (at < 0) return base;
    String list = message.substring(at + 1);
    if (!list.matches("[A-Z_0-9:,]{1,1000}")) return base;
    var reasons = new java.util.ArrayList<String>();
    var actions = new java.util.LinkedHashSet<String>();
    for (String item : list.split(",")) {
      String[] pair = item.split(":", 3);
      if (pair.length < 2 || pair[1].equals("UNSUPPORTED")) continue;
      if (pair[1].equals("FULL_A_SCOPE_UNSUPPORTED")) {
        reasons.add(sourceName(pair[0]) + "：缺少沪深北三所截面能力，已跳过");
        continue;
      }
      var diagnosis = code(pair[1]);
      if (diagnosis == null) continue;
      String attempts = pair.length == 3 && pair[2].matches("[2-3]") ? "（已尝试" + pair[2] + "次）" : "";
      reasons.add(
          sourceName(pair[0])
              + "："
              + (pair[1].equals("UNCLASSIFIED_FAILURE") || pair[1].equals("UNAVAILABLE")
                  ? "采集未成功，记录中没有可确认的具体原因"
                  : pair[1].equals("UNSUPPORTED")
                      ? "不承担本项行情，已正常跳过"
                      : pair[1].equals("ACCESS_RESTRICTED") ? "本机仍保留此前访问限制的保护" : diagnosis.title())
              + attempts);
      if (actions.size() < 3) actions.add(diagnosis.action());
    }
    if (reasons.isEmpty())
      return new FailureDiagnosis(
          base.code(),
          "没有配置能完成本项的来源",
          "所列来源均未提供本项能力，已经正常跳过；这不是这些来源都发生网络故障。",
          base.impact(),
          "在行情通道页确认所需能力，并配置至少一个支持该能力的来源。");
    return new FailureDiagnosis(
        base.code(),
        base.title(),
        String.join("；", reasons),
        base.impact(),
        String.join(" ", actions));
  }
}

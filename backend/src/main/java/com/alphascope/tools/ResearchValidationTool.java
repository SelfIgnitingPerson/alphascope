package com.alphascope.tools;

import com.alphascope.backtest.calculator.MarketScoreValidationCalculator;
import com.alphascope.backtest.calculator.ResearchValidationVerifier;
import com.alphascope.backtest.dto.ResearchValidationRequest;
import com.alphascope.backtest.dto.ValidationProtocol;
import com.alphascope.backtest.vo.ResearchValidationView;
import com.alphascope.backtest.vo.SignalStatisticView;
import com.alphascope.backtest.vo.ValidationTaskView;
import com.alphascope.common.util.Jsons;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 导出唯一一份本机研究验证结果，并从冻结输入离线复算，不导出数据库备份。
 *
 * @author chenxuefeng
 */
public final class ResearchValidationTool {
  private ResearchValidationTool() {}

  /**
   * 提交持久后台验证并只轮询轻量状态，成功后显式下载完整证据。
   *
   * @param root 研究产物所在项目根目录
   * @param request 明确日期及范围
   * @throws Exception 本机服务不可用、任务未成功或证据不一致
   */
  public static void run(Path root, ResearchValidationRequest request) throws Exception {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      var submit =
          HttpRequest.newBuilder(
                  LocalApiEndpoint.resolve("/api/v1/admin/backtest-validation-tasks"))
              .timeout(Duration.ofSeconds(30))
              .header("Content-Type", "application/json")
              .POST(BodyPublishers.ofString(Jsons.write(request)))
              .build();
      var task = Jsons.read(data(client, submit).toString(), ValidationTaskView.class);
      long until = System.nanoTime() + Duration.ofMinutes(20).toNanos();
      String previous = "";
      while (Set.of("PENDING", "RUNNING", "CANCEL_REQUESTED", "TIMEOUT_REQUESTED")
          .contains(task.status())) {
        String progress = task.status() + ": " + task.message();
        if (!progress.equals(previous)) {
          System.out.println("validation #" + task.id() + " " + progress);
          previous = progress;
        }
        if (System.nanoTime() > until)
          throw new IllegalStateException("等待验证超时，可在历史信号页查看或取消任务#" + task.id());
        Thread.sleep(500);
        task =
            Jsons.read(
                data(
                        client,
                        HttpRequest.newBuilder(
                                LocalApiEndpoint.resolve(
                                    "/api/v1/backtest/validation-tasks/" + task.id()))
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build())
                    .toString(),
                ValidationTaskView.class);
      }
      require("SUCCESS".equals(task.status()), "验证未成功：" + task.status() + " " + task.message());
      exportTask(root, client, task);
    }
  }

  /**
   * 导出一项已经完成的验证，不重新提交任务、不采集行情，也不改变原证据。
   *
   * @param root 项目根目录，替换唯一的研究验收交付文件
   * @param taskId 已完成验证的任务编号
   * @throws Exception 任务不属于当前用户、未成功或完整性核对失败
   */
  public static void exportTask(Path root, long taskId) throws Exception {
    require(taskId > 0, "验证编号必须大于0");
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      var task =
          Jsons.read(
              data(
                      client,
                      HttpRequest.newBuilder(
                              LocalApiEndpoint.resolve(
                                  "/api/v1/backtest/validation-tasks/" + taskId))
                          .timeout(Duration.ofSeconds(30))
                          .GET()
                          .build())
                  .toString(),
              ValidationTaskView.class);
      require("SUCCESS".equals(task.status()), "仅可导出已完成验证：" + task.status());
      exportTask(root, client, task);
    }
  }

  private static void exportTask(Path root, HttpClient client, ValidationTaskView task)
      throws Exception {
    var response =
        client.send(
            HttpRequest.newBuilder(
                    LocalApiEndpoint.resolve(
                        "/api/v1/backtest/validation-tasks/" + task.id() + "/evidence"))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build(),
            BodyHandlers.ofString(StandardCharsets.UTF_8));
    require(
        response.statusCode() == 200 && Jsons.hash(response.body()).equals(task.evidenceSha256()),
        "下载证据响应或指纹不一致");
    export(root, Jsons.read(response.body(), ResearchValidationView.class));
  }

  private static com.fasterxml.jackson.databind.JsonNode data(
      HttpClient client, HttpRequest request) throws Exception {
    var response = client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
    var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
    require(
        response.statusCode() >= 200
            && response.statusCode() < 300
            && "SUCCESS".equals(body.path("code").asText()),
        "本机验证请求失败：HTTP " + response.statusCode() + " " + body.path("message").asText());
    return body.path("data");
  }

  /**
   * 保存完整JSON与可阅读结论，覆盖同名研究验收产物。
   *
   * @param root 项目根目录
   * @param report 来自只读验证服务的完整结果
   * @throws IOException 交付目录无法写入
   */
  public static void export(Path root, ResearchValidationView report) throws IOException {
    Path directory = root.resolve("artifacts");
    Files.createDirectories(directory);
    ResearchValidationVerifier.verify(report);
    Path rawTemporary = Files.createTempFile(directory, "research-validation-", ".json.tmp");
    Path textTemporary = Files.createTempFile(directory, "research-validation-", ".md.tmp");
    try {
      Files.writeString(rawTemporary, Jsons.write(report), StandardCharsets.UTF_8);
      Files.writeString(textTemporary, markdown(report), StandardCharsets.UTF_8);
      Files.move(
          rawTemporary,
          directory.resolve("research-validation.json"),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      Files.move(
          textTemporary,
          directory.resolve("research-validation.md"),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(rawTemporary);
      Files.deleteIfExists(textTemporary);
    }
    reproduce(root);
    System.out.println("artifacts/research-validation.md and research-validation.json");
  }

  /**
   * 仅从已导出输入复算；不读取数据库、现行参数或网络。
   *
   * @param root 项目根目录，读取固定的研究验收JSON
   * @throws IOException 验收JSON不存在或无法读取
   */
  public static void reproduce(Path root) throws IOException {
    String raw = Files.readString(root.resolve("artifacts/research-validation.json"));
    var metadata = new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
    require(
        ValidationProtocol.VERSION.equals(metadata.path("protocolVersion").asText()),
        "冻结协议不支持，不能用新算法复算旧口径");
    var result = ResearchValidationVerifier.verify(Jsons.read(raw, ResearchValidationView.class));
    System.out.println(
        "Full chain reproduced from prices: market windows="
            + result.marketWindows()
            + ", technical stocks="
            + result.technicalStocks()
            + ", algorithm="
            + result.technicalVersion());
  }

  private static String markdown(ResearchValidationView report) {
    var text = new StringBuilder("# AlphaScope 真实研究验证\n\n");
    text.append("区间：")
        .append(report.start())
        .append("至")
        .append(report.end())
        .append("；核验时刻：")
        .append(report.checkedAt().atZone(ZoneId.of("Asia/Shanghai")))
        .append("；协议：")
        .append(report.protocolVersion())
        .append("。\n\n")
        .append(
            report.marketReplay() == null
                ? "本次未请求全A效果检验。当前完整评分计数："
                : "**全A五维方法尚未确立有效性。** 当前完整全A评分天数：")
        .append(report.qualifiedScoreDays())
        .append("；结束日评级：")
        .append(report.scoreAtEnd() == null ? "未请求市场评分" : report.scoreAtEnd().state())
        .append("。以下是实际执行结果，不是收益承诺。\n\n")
        .append("## 全A评分与未来沪深300表现\n\n")
        .append("按固定日期70%/30%切分，分别核对5/10/20日成熟、不重叠窗口。高分组≥60、低分组<40；秩相关为空表示无法估计，不是0。\n\n")
        .append("| 区间 | 交易日窗口 | 配对数 | 秩相关 | 高减低平均收益 | 状态 |\n")
        .append("| --- | --- | --- | --- | --- | --- |\n");
    for (var value :
        report.marketReplay() == null
            ? List.<MarketScoreValidationCalculator.Result>of()
            : report.marketReplay().results()) {
      text.append("| ")
          .append(value.period().equals("EARLY") ? "较早区间" : "较晚留出")
          .append(" | ")
          .append(value.horizon())
          .append(" | ")
          .append(value.sampleCount())
          .append(" | ")
          .append(
              value.rankCorrelation() == null
                  ? "—"
                  : String.format(Locale.ROOT, "%.4f", value.rankCorrelation()))
          .append(" | ")
          .append(percent(value.highMinusLow()))
          .append(" | ")
          .append(status(value.status()))
          .append(" |\n");
    }
    for (var stock : report.stocks()) {
      text.append("\n## ")
          .append(stock.symbol())
          .append("\n\n")
          .append("真实价格：")
          .append(stock.firstPriceDate())
          .append("至")
          .append(stock.lastPriceDate())
          .append("，共")
          .append(stock.priceDays())
          .append("天；已有规则记录：")
          .append(stock.savedRuleRecords())
          .append("条。\n\n")
          .append("已有信号：回顾状态 ")
          .append(stock.savedRetrospective().dataStatus())
          .append("，严格历史时点状态 ")
          .append(stock.savedPointInTime().dataStatus())
          .append("。\n\n");
      if (stock.replay() == null) {
        text.append("本次重建未完成：").append(stock.failure()).append("。\n");
        continue;
      }
      text.append("五条技术规则使用当日及以前的真实价格/成交量逐日重建，阈值未按结果调优，不写回历史信号。较晚区间自")
          .append(stock.replay().result().splitDate())
          .append("开始；只列较晚区间，完整较早统计和排除原因在JSON。\n\n")
          .append("**这些是风险规则：触发后上涨不代表风险提示成功，正收益比例不是策略胜率。**\n\n")
          .append("| 规则 | 窗口 | 样本 | 触发后均值 | 沪深300均值 | 超额均值 | 正收益比例 | 状态 |\n")
          .append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");
      stock.replay().result().validationWindows().stream()
          .filter(window -> window.label().equals("HOLDOUT"))
          .forEach(
              window ->
                  window
                      .statistics()
                      .forEach(
                          (horizon, rules) ->
                              rules.forEach((rule, stat) -> append(text, rule, horizon, stat))));
      var simulation = stock.replay().strategy();
      text.append("\n### 两月量价组合执行模拟\n\n");
      if (simulation == null) {
        text.append("原验证未保存该扩展，不在导出时补算。\n");
      } else {
        text.append("方法：")
            .append(simulation.version())
            .append("。收盘形成条件，次个交易日开盘尝试执行；各段独立起仓。\n\n")
            .append("| 区间 | 扣费收益 | 扣费后已平仓胜率 | 最大回撤 | 基准收益 | 卖出次数 | 跳过委托 | 证据结论 |\n")
            .append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (var period : simulation.periods()) {
          var stats = period.tradeStatistics();
          text.append("| ")
              .append(
                  switch (period.label()) {
                    case "ALL" -> "全区间";
                    case "DISCOVERY" -> "较早区间";
                    case "HOLDOUT" -> "较晚留出";
                    default -> period.label();
                  })
              .append(" | ")
              .append(percent(period.netReturn()))
              .append(" | ")
              .append(
                  stats == null || stats.winRate() == null
                      ? "—"
                      : percent(stats.winRate())
                          + "（"
                          + stats.winningTrades()
                          + "/"
                          + stats.closedTrades()
                          + "）")
              .append(" | ")
              .append(percent(period.maximumDrawdown()))
              .append(" | ")
              .append(percent(period.benchmarkReturn()))
              .append(" | ")
              .append(period.completedTrades())
              .append(" | ")
              .append(period.skippedOrders())
              .append(" | ")
              .append(
                  "INCOMPLETE".equals(period.status())
                      ? "证据不足：" + period.reason()
                      : period.completedTrades() == 0
                          ? "没有完成卖出样本，不能据此认定策略有效"
                          : "仅为执行假设下的回顾，卖出次数不是独立样本量")
              .append(" |\n");
        }
        text.append("\n");
        simulation.limitations().forEach(value -> text.append("- ").append(value).append("\n"));
      }
    }
    text.append("\n## 本次仍不足以确认有效的原因\n\n");
    report.blockers().forEach(value -> text.append("- ").append(value).append("\n"));
    text.append("\n## 方法边界\n\n");
    report.limitations().forEach(value -> text.append("- ").append(value).append("\n"));
    text.append("\n## 如何继续检验\n\n")
        .append("先固定股票池、规则和执行参数并保存时间，再收集此后尚未观察的数据；不得看到结果后筛选股票或修改阈值。当前较晚区间仍是回顾切分，不能作为真正前瞻结果。\n\n")
        .append("补齐历史ST、逐日价格限制、停复牌和公司行动账务，核验可成交性；独立报告扣费收益、基准、回撤及未成交情况。当前不完整区间不可剔除后只展示有利股票。\n\n")
        .append("需要跨市场阶段的足量、独立证据；软件测试通过、复算一致、少数正收益或固定20次样本提示线，都不能替代效果检验。此报告保持尚未证实，不自动产生实盘结论。\n");
    text.append(
        "\n完整输入和结果保存于同目录research-validation.json；离线执行 `bash scripts/tools.sh reproduce-research-validation` 核对SHA256并复算。它是本次研究证据，不是数据库备份。\n");
    return text.toString();
  }

  private static void append(
      StringBuilder text, String rule, String horizon, SignalStatisticView stat) {
    text.append("| ")
        .append(
            switch (rule) {
              case "TREND_BREAK_20" -> "20日趋势走弱";
              case "TREND_BREAK_60" -> "60日趋势走弱";
              case "NEW_LOW_20" -> "20日新低";
              case "VOLUME_SELL" -> "放量下跌";
              case "RELATIVE_WEAK" -> "相对沪深300走弱";
              default -> rule;
            })
        .append(" | ")
        .append(horizon)
        .append(" | ")
        .append(stat.sampleCount())
        .append(" | ")
        .append(percent(stat.meanReturn()))
        .append(" | ")
        .append(percent(stat.meanBenchmarkReturn()))
        .append(" | ")
        .append(percent(stat.meanExcessReturn()))
        .append(" | ")
        .append(percent(stat.positiveRatio()))
        .append(" | ")
        .append(status(stat.dataStatus()))
        .append(" |\n");
  }

  private static String percent(Double value) {
    return value == null ? "—" : String.format(Locale.ROOT, "%.2f%%", value * 100);
  }

  private static String status(String value) {
    return switch (value) {
      case "NO_VALID_SCORES" -> "无完整评分";
      case "INSUFFICIENT_PAIRS" -> "无有效配对";
      case "SMALL_SAMPLE" -> "小样本";
      case "DESCRIPTIVE_ONLY" -> "仅描述统计";
      case "NO_TRIGGERED_SIGNALS" -> "无触发";
      case "WAITING_WINDOW" -> "窗口未成熟";
      case "INSUFFICIENT_DATA" -> "证据不足";
      case "OBSERVED" -> "已有成熟样本";
      default -> value;
    };
  }

  private static void require(boolean condition, String reason) {
    if (!condition) throw new IllegalStateException(reason);
  }
}

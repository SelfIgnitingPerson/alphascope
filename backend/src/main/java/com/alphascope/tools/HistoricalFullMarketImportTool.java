package com.alphascope.tools;

import com.alphascope.AlphaScopeApplication;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.calculator.HistoricalFullMarketAssembler;
import com.alphascope.market.dto.HistoricalFullMarketArchive;
import com.alphascope.market.dto.ResearchSnapshotQuery;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.entity.MarketBreadthSnapshot;
import com.alphascope.market.mapper.MarketBreadthMapper;
import com.alphascope.market.service.FullMarketService;
import com.alphascope.market.service.MarketService;
import com.alphascope.operations.policy.SourceEvidence;
import com.alphascope.operations.service.SourceLicenseService;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 本机数据维护入口，校验全部文件后显式导入，发布结果使用同一来源历史整段复算。
 *
 * @author chenxuefeng
 */
final class HistoricalFullMarketImportTool {
  private static final String DATE_FILE = "[0-9]{4}-[0-9]{2}-[0-9]{2}\\.json";

  private HistoricalFullMarketImportTool() {}

  static void run(Path root, LocalDate date, boolean apply) throws IOException {
    Path directory = root.resolve(".runtime/full-market-import");
    if (!Files.isDirectory(directory))
      throw new IllegalArgumentException("没有可核验的授权历史资料，请将逐日 JSON 放入 .runtime/full-market-import");
    List<Path> files;
    try (var paths = Files.list(directory)) {
      files =
          paths
              .filter(file -> file.getFileName().toString().matches(DATE_FILE))
              .filter(file -> !fileDate(file).isAfter(date))
              .sorted()
              .toList();
    }
    if (files.isEmpty() || !fileDate(files.getLast()).equals(date) || files.size() > 140)
      throw new IllegalArgumentException("应提供目标交易日的档案，每批最多 140 个交易日");
    var importedAt = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    // 无论是否写入都先验证全批技术口径。逐日处理以限制全市场 JSON 内存。
    var sources = new TreeSet<String>();
    for (Path file : files) sources.add(snapshot(file, importedAt).getSource());
    if (sources.size() != 1) throw new IllegalArgumentException("同一批次必须采用一致的历史档案口径");
    if (!apply) {
      System.out.printf(
          "已通过文件校验 %d 日，%s 至 %s。未执行入库。%n", files.size(), fileDate(files.getFirst()), date);
      return;
    }
    try (var context = maintenanceContext()) {
      var market = context.getBean(MarketService.class);
      if (market.synthetic()) throw new IllegalStateException("历史重建要求真实市场模式");
      var confirmedDates =
          market.expectedDates(
              TradingDatesQuery.builder()
                  .symbol("000300.SH")
                  .start(fileDate(files.getFirst()))
                  .end(date)
                  .build());
      if (files.stream()
          .map(HistoricalFullMarketImportTool::fileDate)
          .anyMatch(day -> !confirmedDates.contains(day)))
        throw new IllegalArgumentException("档案含本机日历未确认的交易日");
      var mapper = context.getBean(MarketBreadthMapper.class);
      var licenses = context.getBean(SourceLicenseService.class);
      var service = context.getBean(FullMarketService.class);
      new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
          .executeWithoutResult(
              transaction -> {
                for (Path file : files) {
                  try {
                    var row = snapshot(file, importedAt);
                    var raw = Jsons.read(row.getRawJson(), JsonNode.class);
                    var evidence = raw.path("rosterEvidence");
                    for (String source : SourceEvidence.inspect(evidence).sources())
                      licenses.require(source, "COLLECT");
                    verifyReference(mapper, row, raw);
                    mapper.save(row);
                  } catch (IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                  }
                }
              });
      int rebuilt =
          rebuildDates(
              service, files.stream().map(HistoricalFullMarketImportTool::fileDate).toList());
      var result = service.readiness(date);
      System.out.printf(
          "已核验 %d 份导入文件并复算 %d 日，截止 %s 同源完整历史 %d/%d 日。%n",
          files.size(), rebuilt, date, result.archivedDays(), result.requiredDays());
    }
  }

  /**
   * 复算已入库的历史观察，适用于旧批次仅在终点发布过结果的情况。
   *
   * @param start 起始交易日期，含当日
   * @param end 结束交易日期，含当日
   */
  static void rebuildStored(LocalDate start, LocalDate end) {
    if (start.isAfter(end)
        || start.plusDays(200).isBefore(end)
        || end.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai"))))
      throw new IllegalArgumentException("请指定有效范围，每次最多覆盖 200 个自然日");
    try (var context = maintenanceContext()) {
      var mapper = context.getBean(MarketBreadthMapper.class);
      var market = context.getBean(MarketService.class);
      var dates =
          market
              .expectedDates(
                  TradingDatesQuery.builder().symbol("000300.SH").start(start).end(end).build())
              .stream()
              .sorted()
              .filter(
                  day ->
                      java.util.stream.Stream.of(
                              HistoricalFullMarketAssembler.SOURCE,
                              HistoricalFullMarketAssembler.PUBLIC_HISTORY_SOURCE)
                          .anyMatch(
                              source ->
                                  mapper.fullForDate(
                                          ResearchSnapshotQuery.builder()
                                              .date(day)
                                              .source(source)
                                              .build())
                                      != null))
              .toList();
      if (dates.isEmpty()) throw new IllegalArgumentException("范围内没有已入库的全 A 历史档案");
      rebuildDates(context.getBean(FullMarketService.class), dates);
    }
  }

  private static int rebuildDates(FullMarketService service, List<LocalDate> dates) {
    var failed = new TreeSet<LocalDate>();
    for (var day : dates) {
      try {
        service.rebuildImported(day);
        System.out.println("已复算全 A 观察 " + day);
      } catch (RuntimeException failure) {
        failed.add(day);
        org.slf4j.LoggerFactory.getLogger(HistoricalFullMarketImportTool.class)
            .error("全 A 观察复算失败 date={}", day, failure);
      }
    }
    if (!failed.isEmpty()) throw new IllegalStateException("部分日期复算失败 " + failed);
    return dates.size();
  }

  private static org.springframework.context.ConfigurableApplicationContext maintenanceContext() {
    return new SpringApplicationBuilder(AlphaScopeApplication.class)
        .web(WebApplicationType.NONE)
        .run("--alphascope.jobs.worker-enabled=false", "--alphascope.scheduler.enabled=false");
  }

  /**
   * 核对已有全 A 档案的日期、指纹和逐只交易状态，防止引用相似日期或不同比较口径。
   *
   * @param mapper 已保存的市场截面
   * @param row 本次重建截面
   * @param raw 本次重建的原始证据
   */
  private static void verifyReference(
      MarketBreadthMapper mapper, MarketBreadthSnapshot row, JsonNode raw) {
    var evidence = raw.path("rosterEvidence").path("comparison");
    long id = evidence.path("referenceSnapshotId").asLong(0);
    if (id == 0) return;
    String previous = mapper.coverageRaw(id);
    if (previous == null) throw new IllegalArgumentException("正式原档引用不存在 " + id);
    var reference = Jsons.read(previous, JsonNode.class);
    var proof = reference.path("universeProof");
    if (!proof.path("completeRoster").asBoolean()
        || !"FULL_A".equals(proof.path("scope").asText())
        || !row.getTradeDate().toString().equals(proof.path("businessDate").asText())
        || !Jsons.hash(Jsons.write(Jsons.evidenceObject(reference)))
            .equals(evidence.path("sha256").asText())
        || !members(raw).equals(members(reference)))
      throw new IllegalArgumentException("当日正式原档与重建名单不一致 " + row.getTradeDate());
  }

  private static Map<String, String> members(JsonNode raw) {
    var members = new TreeMap<String, String>();
    for (var row : raw.path("universe")) {
      if (members.put(row.path("code").asText(), row.path("tradeStatus").asText()) != null)
        throw new IllegalArgumentException("引用的历史名册含重复证券");
    }
    return members;
  }

  private static MarketBreadthSnapshot snapshot(Path file, LocalDateTime importedAt)
      throws IOException {
    if (!Files.isRegularFile(file) || Files.size(file) > 8_000_000L)
      throw new IllegalArgumentException(file.getFileName() + " 文件类型或大小无效");
    try {
      var input = Jsons.read(Files.readString(file), HistoricalFullMarketArchive.class);
      if (input == null || !fileDate(file).equals(input.date()))
        throw new IllegalArgumentException("文件名与业务日期不一致");
      return HistoricalFullMarketAssembler.assemble(input, importedAt);
    } catch (RuntimeException invalid) {
      throw new IllegalArgumentException(
          file.getFileName() + " 校验失败，" + invalid.getMessage(), invalid);
    }
  }

  private static LocalDate fileDate(Path file) {
    return LocalDate.parse(file.getFileName().toString().substring(0, 10));
  }
}

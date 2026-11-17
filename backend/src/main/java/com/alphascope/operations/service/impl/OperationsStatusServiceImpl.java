package com.alphascope.operations.service.impl;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.service.MarketService;
import com.alphascope.operations.maintenance.DatabaseMaintenance.BackupOutcome;
import com.alphascope.operations.service.DatabaseBackupService;
import com.alphascope.operations.service.OperationsStatusService;
import com.alphascope.operations.vo.BackupRunStatus;
import com.alphascope.operations.vo.OperationsStatusView;
import com.alphascope.operations.vo.SecondaryBackupStatus;
import com.alphascope.portfolio.service.PortfolioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/**
 * 明确区分技术健康、行情时效和备份证明，状态文件损坏显示INVALID而不是假定成功。
 *
 * @author chenxuefeng
 */
@Service
public class OperationsStatusServiceImpl implements OperationsStatusService {
  private final DatabaseBackupService backupService;
  private final MarketService market;
  private final PortfolioService portfolio;
  private final AlphaScopeProperties properties;
  private final Clock clock;
  private final ObjectMapper json;
  private final Path directory;

  public OperationsStatusServiceImpl(
      MarketService market,
      PortfolioService portfolio,
      AlphaScopeProperties properties,
      Clock clock,
      ObjectMapper json,
      DatabaseBackupService backupService) {
    this.market = market;
    this.backupService = backupService;
    this.portfolio = portfolio;
    this.properties = properties;
    this.clock = clock;
    this.json = json;
    this.directory =
        Path.of(System.getProperty("alphascope.root", "."))
            .toAbsolutePath()
            .resolve(".runtime/backups");
  }

  /** {@inheritDoc} */
  @Override
  public OperationsStatusView status() {
    var issues = new ArrayList<String>();
    var options = properties.getOperations();
    var now = clock.instant().atZone(ZoneId.of("Asia/Shanghai"));
    LocalDate reference =
        now.toLocalTime().isBefore(LocalTime.of(16, 10))
            ? now.toLocalDate().minusDays(1)
            : now.toLocalDate();
    LocalDate expected = null;
    String calendar = "AVAILABLE";
    try {
      var dates =
          market.expectedDates(
              TradingDatesQuery.builder()
                  .symbol("000300.SH")
                  .start(reference.minusDays(14))
                  .end(reference)
                  .build());
      expected =
          dates.stream()
              .max(LocalDate::compareTo)
              .orElseThrow(() -> new IllegalStateException("窗口内没有可确认开市日"));
    } catch (RuntimeException e) {
      calendar = "UNKNOWN_CALENDAR";
      issues.add("近期权威交易日历不足，无法确认应有收盘日期；不能据此把旧行情标成最新");
    }
    var latest =
        market.providerStatus().latestData().stream()
            .collect(Collectors.toMap(row -> row.getSymbol(), row -> row, (a, c) -> a));
    var required = new LinkedHashSet<String>();
    required.add("000300.SH");
    portfolio.watchlist().stream()
        .filter(row -> Boolean.TRUE.equals(row.getEnabled()))
        .forEach(row -> required.add(row.getSymbol()));
    var freshness = new ArrayList<OperationsStatusView.Freshness>();
    for (String symbol : required) {
      var row = latest.get(symbol);
      String status =
          expected == null
              ? "UNKNOWN_CALENDAR"
              : row == null ? "MISSING" : expected.equals(row.getTradeDate()) ? "FRESH" : "STALE";
      if (expected != null && !"FRESH".equals(status)) {
        String day = market.dayStatus(symbol, expected);
        if ("SUSPENDED".equals(day) || "NOT_LISTED".equals(day) || "DELISTED".equals(day)) {
          status = "NOT_APPLICABLE";
        }
      }
      if ("STALE".equals(status) || "MISSING".equals(status)) {
        issues.add(symbol + " 尚无应有业务日的有效行情");
      }
      freshness.add(
          new OperationsStatusView.Freshness(
              symbol,
              status,
              row == null ? null : row.getTradeDate(),
              row == null ? null : row.getFetchedAt(),
              row == null ? null : row.getSource()));
    }
    var backup = new OperationsStatusView.BackupHealth("MISSING", null, null, null, null);
    try {
      var value = read("latest-success.json", BackupOutcome.class);
      if (value != null) {
        if (!value.verified()
            || !value.fileName().matches("alphascope-\\d{8}T\\d{6}-[a-f0-9]{8}\\.sql\\.gz")) {
          throw new IllegalStateException("无效托管备份名");
        }
        Path file = directory.resolve(value.fileName());
        Path marker = directory.resolve(value.fileName() + ".verified.json");
        boolean valid =
            Files.isRegularFile(file)
                && !Files.isSymbolicLink(file)
                && Files.isRegularFile(marker)
                && value
                    .sha256()
                    .equals(json.readTree(Files.readString(marker)).path("sha256").asText())
                && value.sha256().equals(Jsons.hashFile(file));
        Instant due =
            CronExpression.parse(options.getBackupCron())
                .next(value.createdAt().atZone(now.getZone()))
                .toInstant();
        backup =
            new OperationsStatusView.BackupHealth(
                !valid
                    ? "INVALID"
                    : clock.instant().isAfter(due.plusSeconds(7200)) ? "STALE" : "AVAILABLE",
                value.fileName(),
                value.createdAt(),
                due,
                value.sha256());
      }
    } catch (Exception e) {
      backup = new OperationsStatusView.BackupHealth("INVALID", null, null, null, null);
    }
    if (options.isBackupEnabled() && !"AVAILABLE".equals(backup.status())) {
      issues.add("本机备份状态：" + backup.status() + "，请创建并实际恢复验证");
    }
    BackupRunStatus attempt = null;
    try {
      attempt = read("latest-run.json", BackupRunStatus.class);
    } catch (Exception e) {
      issues.add("最近备份尝试状态无法读取");
    }
    if (attempt != null
        && Set.of("QUEUED", "RUNNING").contains(attempt.status())
        && !backupService.isActive()) {
      attempt =
          new BackupRunStatus(
              "INTERRUPTED",
              attempt.observedAt(),
              attempt.fileName(),
              attempt.sha256(),
              "上次请求所属进程已退出，没有实际运行中的备份；可重新请求");
    }
    if (attempt != null
        && (backup.verifiedAt() == null
            || attempt.observedAt() == null
            || attempt.observedAt().isAfter(backup.verifiedAt()))
        && ("FAILED".equals(attempt.status()) || "PARTIAL".equals(attempt.status()))) {
      issues.add(attempt.reason());
    }
    SecondaryBackupStatus secondary =
        new SecondaryBackupStatus(
            "NOT_CONFIGURED", null, null, null, false, "尚未配置第二存储，不具备第二位置备份证明");
    if (!options.getBackupSecondaryDirectory().isBlank()) {
      try {
        var value = read("secondary-status.json", SecondaryBackupStatus.class);
        secondary =
            value == null
                ? new SecondaryBackupStatus(
                    "NOT_CHECKED", null, null, null, false, "第二存储已配置，尚未完成复制核验")
                : value;
      } catch (Exception e) {
        secondary =
            new SecondaryBackupStatus("FAILED", clock.instant(), null, null, false, "第二存储状态无法读取");
      }
    }
    if (!options.getBackupSecondaryDirectory().isBlank()
        && !"VERIFIED".equals(secondary.status())) {
      issues.add(secondary.reason());
    }
    return new OperationsStatusView(
        clock.instant(),
        issues.isEmpty() ? "HEALTHY" : "ATTENTION",
        expected,
        calendar,
        options.isBackupEnabled(),
        options.getBackupCron(),
        backup,
        attempt,
        secondary,
        freshness,
        issues);
  }

  private <T> T read(String name, Class<T> type) throws IOException {
    Path file = directory.resolve(name);
    return Files.exists(file) ? json.readValue(Files.readString(file), type) : null;
  }
}

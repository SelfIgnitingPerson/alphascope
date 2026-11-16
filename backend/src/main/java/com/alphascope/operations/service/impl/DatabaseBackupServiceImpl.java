package com.alphascope.operations.service.impl;

import com.alphascope.common.config.AlphaScopeProperties;
import com.alphascope.common.util.Jsons;
import com.alphascope.operations.maintenance.DatabaseMaintenance;
import com.alphascope.operations.service.DatabaseBackupService;
import com.alphascope.operations.vo.BackupRunStatus;
import com.alphascope.operations.vo.SecondaryBackupStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/**
 * 使用现有数据源执行本机定期备份；应用未运行时不会承诺自动执行，失败状态持久记录。
 *
 * @author chenxuefeng
 */
@Service
public class DatabaseBackupServiceImpl implements DatabaseBackupService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseBackupServiceImpl.class);
  private final DatabaseMaintenance maintenance;
  private final AlphaScopeProperties properties;
  private final Clock clock;
  private final Path status;
  private final ThreadPoolTaskScheduler executor;
  private final AtomicBoolean active = new AtomicBoolean();
  private final AtomicBoolean requested = new AtomicBoolean();

  public DatabaseBackupServiceImpl(
      DataSource dataSource,
      AlphaScopeProperties properties,
      Clock clock,
      @Qualifier("backupScheduler") ThreadPoolTaskScheduler executor) {
    Path root = Path.of(System.getProperty("alphascope.root", ".")).toAbsolutePath().normalize();
    this.maintenance = new DatabaseMaintenance(dataSource, root, clock);
    this.properties = properties;
    this.clock = clock;
    this.executor = executor;
    this.status = root.resolve(".runtime/backups/latest-run.json");
  }

  /** {@inheritDoc} */
  @Override
  public DatabaseMaintenance.BackupOutcome backupAndVerify() throws Exception {
    if (!active.compareAndSet(false, true)) {
      throw new IllegalStateException("备份正在运行");
    }
    try {
      persist(new BackupRunStatus("RUNNING", clock.instant(), null, null, "正在导出一致性快照并进行隔离恢复验证"));
      var result = maintenance.backupAndVerify(properties.getOperations().getBackupRetentionDays());
      var options = properties.getOperations();
      String state = "VERIFIED", reason = "本机备份已实际隔离恢复验证";
      if (!options.getBackupSecondaryDirectory().isBlank()) {
        try {
          maintenance.replicate(
              result,
              Path.of(options.getBackupSecondaryDirectory()),
              options.getBackupRetentionDays());
        } catch (Exception e) {
          state = "PARTIAL";
          reason = "本机备份已验证，第二存储复制失败，请检查目标挂载和权限";
          write(
              status.resolveSibling("secondary-status.json"),
              new SecondaryBackupStatus(
                  "FAILED", clock.instant(), result.fileName(), null, false, reason));
          LOGGER.warn("secondary_backup_failed failureType={}", e.getClass().getSimpleName());
        }
      }
      persist(
          new BackupRunStatus(state, clock.instant(), result.fileName(), result.sha256(), reason));
      LOGGER.info(
          "backup_verified file={} tables={} rows={} secondaryState={}",
          result.fileName(),
          result.tables(),
          result.rows(),
          state);
      return result;
    } catch (Exception e) {
      persist(
          new BackupRunStatus(
              "FAILED", clock.instant(), null, null, "备份或隔离恢复未完成，旧成功备份保留；请检查数据库、磁盘和Docker状态"));
      LOGGER.warn(
          "backup_failed failureType={} previous_success_preserved=true",
          e.getClass().getSimpleName());
      throw e;
    } finally {
      active.set(false);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String requestBackup() {
    if (active.get() || !requested.compareAndSet(false, true)) {
      return "ALREADY_RUNNING";
    }
    try {
      persist(new BackupRunStatus("QUEUED", clock.instant(), null, null, "等待独立备份执行器"));
      executor.execute(
          () -> {
            try {
              backupAndVerify();
            } catch (Exception e) {
              /* 失败已持久记录。 */
            } finally {
              requested.set(false);
            }
          });
      return "QUEUED";
    } catch (RuntimeException e) {
      requested.set(false);
      throw e;
    }
  }

  private void persist(BackupRunStatus value) {
    write(status, value);
  }

  private void write(Path path, Object value) {
    try {
      Files.createDirectories(path.getParent());
      Path temporary = Files.createTempFile(path.getParent(), "status-", ".partial");
      try {
        Files.writeString(temporary, Jsons.write(value));
        Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
        Files.move(
            temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException e) {
      throw new IllegalStateException("无法保存备份运行状态", e);
    }
  }

  /** 按上海时区执行配置中的备份时间，默认关闭自动执行，显式启用后不忽略失败。 */
  @Scheduled(
      cron = "${alphascope.operations.backup-cron:0 0 2 * * *}",
      zone = "Asia/Shanghai",
      scheduler = "backupScheduler")
  public void scheduledBackup() {
    if (!properties.getOperations().isBackupEnabled()) {
      return;
    }
    try {
      backupAndVerify();
    } catch (Exception e) {
      /* 失败已记录到状态文件和受限日志，下一次调度重新尝试。 */
    }
  }

  /** 应用错过计划时在启动后补做一次；当天失败不每小时反复创建未验证备份。 */
  @Scheduled(initialDelay = 60000, fixedDelay = 3600000, scheduler = "backupScheduler")
  public void catchUpBackup() {
    if (!properties.getOperations().isBackupEnabled()) {
      return;
    }
    try {
      var json = new ObjectMapper();
      ZoneId zone = ZoneId.of("Asia/Shanghai");
      LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
      if (Files.exists(status)) {
        var last = json.readTree(Files.readString(status));
        if ("FAILED".equals(last.path("status").asText())
            && last.hasNonNull("observedAt")
            && today.equals(
                LocalDate.ofInstant(Instant.parse(last.path("observedAt").asText()), zone))) {
          return;
        }
      }
      Path success = status.resolveSibling("latest-success.json");
      boolean due = !Files.exists(success);
      if (!due) {
        Instant last =
            Instant.parse(json.readTree(Files.readString(success)).path("createdAt").asText());
        var next =
            CronExpression.parse(properties.getOperations().getBackupCron())
                .next(last.atZone(zone));
        due = next != null && !next.toInstant().isAfter(clock.instant());
      }
      if (due) {
        backupAndVerify();
      }
    } catch (Exception e) {
      LOGGER.warn("backup_catchup_not_completed failureType={}", e.getClass().getSimpleName());
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isActive() {
    return active.get() || requested.get();
  }
}

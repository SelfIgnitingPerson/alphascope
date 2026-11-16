package com.alphascope.operations.maintenance;

import com.alphascope.common.util.Jsons;
import com.alphascope.operations.vo.SecondaryBackupStatus;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/**
 * 备份维护门面：组合一致性导出、隔离恢复、保留策略与显式结构同步，统一跨进程互斥。
 *
 * @author chenxuefeng
 */
public final class DatabaseMaintenance {
  private final BackupFiles files;
  private final DatabaseSnapshotWriter writer;
  private final DatabaseRestoreVerifier verifier;
  private final BackupRetentionPolicy retention;
  private final DevelopmentSchemaSynchronizer synchronizer;
  private final SecondaryBackupStore secondary;

  public DatabaseMaintenance(DataSource dataSource, Path root, Clock clock) {
    files = new BackupFiles(root.toAbsolutePath().normalize());
    writer = new DatabaseSnapshotWriter(dataSource, files, clock);
    verifier = new DatabaseRestoreVerifier(files, writer, clock);
    retention = new BackupRetentionPolicy(files, clock);
    synchronizer = new DevelopmentSchemaSynchronizer(dataSource, files, writer, clock);
    secondary = new SecondaryBackupStore(files, clock);
  }

  /**
   * 一致性备份并实际隔离恢复，成功后才执行保留策略。
   *
   * @param retentionDays 至少7天，保留最近两份已验证备份
   * @return 可公开备份结果
   * @throws Exception 导出、互斥或隔离验证失败
   */
  public BackupOutcome backupAndVerify(int retentionDays) throws Exception {
    if (retentionDays < 7) {
      throw new IllegalArgumentException("备份至少保留7天");
    }
    files.prepare();
    try (var channel =
            FileChannel.open(
                files.root.resolve(".runtime/database-maintenance.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        var lock = channel.tryLock()) {
      if (lock == null) {
        throw new IllegalStateException("已有数据库维护在进行");
      }
      Manifest manifest = writer.backup();
      verifier.verify(manifest);
      int pruned = retention.prune(retentionDays);
      var result =
          new BackupOutcome(
              manifest.fileName(),
              manifest.sha256(),
              manifest.createdAt(),
              true,
              manifest.tables().size(),
              manifest.tables().values().stream().mapToLong(TableSignature::rows).sum(),
              pruned);
      files.writePrivate(files.directory.resolve("latest-success.json"), Jsons.write(result));
      return result;
    }
  }

  /**
   * 再次实际恢复验证最新托管备份，复用同一维护锁。
   *
   * @return 已验证的完整清单
   * @throws Exception 不存在备份、互斥或验证失败
   */
  public Manifest verifyLatest() throws Exception {
    files.prepare();
    try (var channel =
            FileChannel.open(
                files.root.resolve(".runtime/database-maintenance.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        var lock = channel.tryLock()) {
      if (lock == null) {
        throw new IllegalStateException("已有数据库维护在进行");
      }
      var manifest = files.latest(false);
      verifier.verify(manifest);
      return manifest;
    }
  }

  /**
   * 使用已验证的新鲜备份显式同步当前单份开发基线，不覆盖业务值。
   *
   * @return 实际执行的受控DDL数量
   * @throws Exception 未满足备份、无活动任务或数据一致性条件
   */
  public int upgradeSchema() throws Exception {
    return synchronizer.synchronize(true);
  }

  /**
   * 在用户明确选择本次不创建备份时，仅核对现有数据指纹后执行受控新增结构。
   *
   * @return 实际执行的DDL数量
   * @throws Exception 活动任务、错误基线或原数据指纹不一致
   */
  public int upgradeSchemaDirect() throws Exception {
    return synchronizer.synchronize(false);
  }

  /**
   * 将已实际恢复验证的备份复制到用户指定的第二存储并核对指纹。
   *
   * @param result 已验证的本机备份
   * @param directory 已挂载且存在的目标父目录
   * @param retentionDays 托管副本保留天数
   * @return 真实复制结果，明确同一文件系统的局限
   * @throws Exception 路径、复制或内容验证失败，不修改本机成功备份
   */
  public SecondaryBackupStatus replicate(BackupOutcome result, Path directory, int retentionDays)
      throws Exception {
    return secondary.copy(result, directory, retentionDays);
  }

  /**
   * 一致性快照清单，列名及语义内容指纹不含数据库凭据。
   *
   * @author chenxuefeng
   */
  public record Manifest(
      /** 备份格式版本。 */
      int formatVersion,
      /** 仅允许本工具生成的备份文件名。 */
      String fileName,
      /** 压缩备份SHA-256。 */
      String sha256,
      /** 实际导出完成时刻。 */
      Instant createdAt,
      /** 按表名索引的快照校验结果。 */
      Map<String, TableSignature> tables) {}

  /**
   * 稳定主键排序后的表快照；JSON按结构和精确数值归一化比较。
   *
   * @author chenxuefeng
   */
  public record TableSignature(
      /** 快照原有列，升级后核对时排除新增可空列。 */
      List<String> columns,
      /** 实际行数。 */
      long rows,
      /** 去除自增计数后的表结构指纹。 */
      String structureHash,
      /** 全字段语义内容指纹。 */
      String contentHash) {}

  /**
   * 可公开的本机备份执行结果，不携带数据内容。
   *
   * @author chenxuefeng
   */
  public record BackupOutcome(
      /** 备份文件名。 */
      String fileName,
      /** 已验证的压缩内容指纹。 */
      String sha256,
      /** 实际备份时刻。 */
      Instant createdAt,
      /** 是否已在隔离MySQL实际恢复。 */
      boolean verified,
      /** 包含框架元数据的表数量。 */
      int tables,
      /** 全部备份记录数量。 */
      long rows,
      /** 本次安全清理的过期托管备份数量。 */
      int prunedBackups) {}
}

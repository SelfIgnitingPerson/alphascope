package com.alphascope.operations.maintenance;

import com.alphascope.common.util.Jsons;
import com.alphascope.operations.maintenance.DatabaseMaintenance.BackupOutcome;
import com.alphascope.operations.vo.SecondaryBackupStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.List;

/**
 * 向用户指定的已挂载目录复制托管备份与验证清单，临时文件校验后才原子提交。
 *
 * @author chenxuefeng
 */
final class SecondaryBackupStore {
  private final BackupFiles source;
  private final Clock clock;

  SecondaryBackupStore(BackupFiles source, Clock clock) {
    this.source = source;
    this.clock = clock;
  }

  SecondaryBackupStatus copy(BackupOutcome result, Path destination, int retentionDays)
      throws Exception {
    if (!result.verified() || !Files.isDirectory(destination)) {
      throw new IllegalArgumentException("第二存储须是已挂载且可访问的目录");
    }
    Path parent = destination.toRealPath();
    var target = new BackupFiles(source.root, parent.resolve("alphascope-backups"));
    Path original = source.directory.toRealPath();
    if (target.directory.startsWith(original) || original.startsWith(target.directory)) {
      throw new IllegalArgumentException("第二存储不能与源备份目录重叠");
    }
    source.prepare();
    target.prepare();
    var manifest =
        Jsons.read(
            Files.readString(source.manifestPath(result.fileName())),
            DatabaseMaintenance.Manifest.class);
    if (!source.verified(manifest) || !manifest.sha256().equals(result.sha256())) {
      throw new IllegalStateException("仅允许复制已实际恢复验证的备份");
    }
    for (String suffix : List.of("", ".manifest.json", ".verified.json")) {
      Path input = source.directory.resolve(result.fileName() + suffix),
          output = target.directory.resolve(result.fileName() + suffix);
      Path staging = Files.createTempFile(target.directory, "replica-", ".partial");
      try {
        Files.copy(input, staging, StandardCopyOption.REPLACE_EXISTING);
        if (!source.hashFile(input).equals(target.hashFile(staging))) {
          throw new IllegalStateException("第二存储复制指纹不一致");
        }
        Files.setPosixFilePermissions(staging, PosixFilePermissions.fromString("rw-------"));
        if (Files.exists(output)) {
          if (!source.hashFile(input).equals(target.hashFile(output))) {
            throw new IllegalStateException("目标存在同名但内容不同的备份，拒绝覆盖");
          }
        } else {
          Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE);
        }
      } finally {
        Files.deleteIfExists(staging);
      }
    }
    if (!target.verified(manifest)) {
      throw new IllegalStateException("第二存储完整核验失败");
    }
    new BackupRetentionPolicy(target, clock).prune(retentionDays);
    boolean different = !Files.getFileStore(original).equals(Files.getFileStore(target.directory));
    var status =
        new SecondaryBackupStatus(
            different ? "VERIFIED" : "VERIFIED_SAME_FILESYSTEM",
            clock.instant(),
            result.fileName(),
            result.sha256(),
            different,
            different ? "已复制压缩备份、清单及恢复验证标记，并核对内容指纹" : "已核验第二份副本，但仍在同一文件系统，不能覆盖整盘损坏风险");
    source.writePrivate(source.directory.resolve("secondary-status.json"), Jsons.write(status));
    source.writePrivate(
        source.directory.resolve("secondary-latest-success.json"), Jsons.write(status));
    return status;
  }
}

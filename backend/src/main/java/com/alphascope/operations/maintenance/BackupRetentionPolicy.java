package com.alphascope.operations.maintenance;

import com.alphascope.common.util.Jsons;
import com.alphascope.operations.maintenance.DatabaseMaintenance.Manifest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 只清理过期托管备份，保留最近两份实际恢复验证成功的备份。
 *
 * @author chenxuefeng
 */
final class BackupRetentionPolicy {
  private final BackupFiles files;
  private final Clock clock;

  BackupRetentionPolicy(BackupFiles files, Clock clock) {
    this.files = files;
    this.clock = clock;
  }

  int prune(int days) throws Exception {
    List<Manifest> values = new ArrayList<>();
    try (var stream = Files.list(files.directory)) {
      for (Path path :
          stream
              .filter(
                  file ->
                      file.getFileName()
                          .toString()
                          .matches(
                              "alphascope-\\d{8}T\\d{6}-[a-f0-9]{8}\\.sql\\.gz\\.manifest\\.json"))
              .toList()) {
        values.add(Jsons.read(Files.readString(path), Manifest.class));
      }
    }
    values.sort(Comparator.comparing(Manifest::createdAt).reversed());
    int newerVerified = 0, removed = 0;
    for (Manifest value : values) {
      boolean successful = files.verified(value);
      if (newerVerified >= 2
          && value.createdAt().isBefore(clock.instant().minusSeconds(days * 86400L))) {
        Files.deleteIfExists(files.managed(value.fileName()));
        Files.deleteIfExists(files.manifestPath(value.fileName()));
        Files.deleteIfExists(files.verifiedPath(value.fileName()));
        removed++;
      } else if (successful) {
        newerVerified++;
      }
    }
    return removed;
  }
}

package com.alphascope.operations.maintenance;

import com.alphascope.common.util.Jsons;
import com.alphascope.operations.maintenance.DatabaseMaintenance.Manifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;

/**
 * 本机托管备份文件、受限路径、语义清单和验证标记；不执行数据库操作。
 *
 * @author chenxuefeng
 */
final class BackupFiles {
  private static final ObjectMapper JSON = new ObjectMapper();
  final Path root;
  final Path directory;

  BackupFiles(Path root) {
    this(root, root.resolve(".runtime/backups"));
  }

  BackupFiles(Path root, Path directory) {
    this.root = root;
    this.directory = directory;
  }

  Manifest latest(boolean verified) throws Exception {
    List<Path> paths;
    try (var files = Files.list(directory)) {
      paths =
          files
              .filter(
                  path ->
                      path.getFileName()
                          .toString()
                          .matches(
                              "alphascope-\\d{8}T\\d{6}-[a-f0-9]{8}\\.sql\\.gz\\.manifest\\.json"))
              .sorted(Comparator.reverseOrder())
              .toList();
    }
    for (Path path : paths) {
      Manifest value = Jsons.read(Files.readString(path), Manifest.class);
      if (!verified || verified(value)) {
        return value;
      }
    }
    throw new IllegalStateException("没有符合要求的托管备份，请先运行backup");
  }

  boolean verified(Manifest manifest) throws Exception {
    Path path = verifiedPath(manifest.fileName());
    return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
        && JSON.readTree(Files.readString(path)).path("sha256").asText().equals(manifest.sha256())
        && hashFile(managed(manifest.fileName())).equals(manifest.sha256());
  }

  String hashFile(Path path) throws Exception {
    return Jsons.hashFile(path);
  }

  Path managed(String name) {
    if (!name.matches("alphascope-\\d{8}T\\d{6}-[a-f0-9]{8}\\.sql\\.gz")) {
      throw new IllegalArgumentException("不是本工具的备份文件");
    }
    Path path = directory.resolve(name);
    if (Files.isSymbolicLink(path)) {
      throw new IllegalStateException("备份文件不能是符号链接");
    }
    return path;
  }

  Path manifestPath(String name) {
    return managed(name).resolveSibling(name + ".manifest.json");
  }

  Path verifiedPath(String name) {
    return managed(name).resolveSibling(name + ".verified.json");
  }

  void prepare() throws IOException {
    Files.createDirectories(directory);
    try {
      Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
    } catch (UnsupportedOperationException ignored) {
      /* 非POSIX平台沿用账户访问控制。 */
    }
  }

  void privateFile(Path path) throws IOException {
    Files.createFile(path);
    try {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    } catch (UnsupportedOperationException ignored) {
      /* 非POSIX平台沿用账户访问控制。 */
    }
  }

  void writePrivate(Path path, String content) throws IOException {
    if (!Files.exists(path)) {
      privateFile(path);
    }
    Files.writeString(path, content);
  }
}

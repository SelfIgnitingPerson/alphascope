package com.alphascope.operations.maintenance;

import com.alphascope.common.util.Jsons;
import com.alphascope.operations.maintenance.DatabaseMaintenance.Manifest;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

/**
 * 仅在本次专用MySQL容器中实际恢复并校验，不向业务库执行恢复。
 *
 * @author chenxuefeng
 */
final class DatabaseRestoreVerifier {
  private static final String IMAGE = "mysql:8.0.40";
  private final BackupFiles files;
  private final DatabaseSnapshotWriter writer;
  private final Clock clock;

  DatabaseRestoreVerifier(BackupFiles files, DatabaseSnapshotWriter writer, Clock clock) {
    this.files = files;
    this.writer = writer;
    this.clock = clock;
  }

  void verify(Manifest manifest) throws Exception {
    Path backup = files.managed(manifest.fileName());
    if (!files.hashFile(backup).equals(manifest.sha256())) {
      throw new IllegalStateException("备份压缩包校验失败");
    }
    String container = "alphascope-restore-" + UUID.randomUUID().toString().substring(0, 12);
    String password = UUID.randomUUID().toString().replace("-", "");
    Map<String, String> environment =
        Map.of("MYSQL_ROOT_PASSWORD", password, "MYSQL_DATABASE", "alphascope");
    boolean created = false;
    try {
      command(List.of("docker", "image", "inspect", IMAGE), Map.of(), null);
      command(
          List.of(
              "docker",
              "run",
              "--rm",
              "-d",
              "--name",
              container,
              "--label",
              "alphascope.restore-verification=true",
              "-p",
              "127.0.0.1::3306",
              "-e",
              "MYSQL_ROOT_PASSWORD",
              "-e",
              "MYSQL_DATABASE",
              IMAGE),
          environment,
          null);
      created = true;
      String port =
          command(List.of("docker", "port", container, "3306/tcp"), Map.of(), null).strip();
      int number = Integer.parseInt(port.substring(port.lastIndexOf(':') + 1));
      String url =
          "jdbc:mysql://127.0.0.1:"
              + number
              + "/alphascope?connectionTimeZone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
      try (var connection = awaitConnection(url, password);
          var zone = new BackupSession(connection)) {
        try (var input = new GZIPInputStream(Files.newInputStream(backup))) {
          command(
              List.of(
                  "docker",
                  "exec",
                  "-i",
                  container,
                  "sh",
                  "-c",
                  "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" exec mysql --default-character-set=utf8mb4 -u root alphascope"),
              Map.of(),
              input);
        }
        writer.assertSignatures(connection, manifest, true);
      }
      files.writePrivate(
          files.verifiedPath(manifest.fileName()),
          Jsons.write(
              Map.of(
                  "sha256",
                  manifest.sha256(),
                  "verifiedAt",
                  clock.instant(),
                  "tables",
                  manifest.tables().size(),
                  "isolatedRestore",
                  true)));
    } finally {
      if (created) {
        command(List.of("docker", "rm", "-f", container), Map.of(), null);
      }
    }
  }

  Connection awaitConnection(String url, String password) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
    while (System.nanoTime() < deadline) {
      try {
        return DriverManager.getConnection(url, "root", password);
      } catch (SQLException e) {
        Thread.sleep(500);
      }
    }
    throw new IllegalStateException("隔离MySQL未能在时限内启动");
  }

  String command(List<String> arguments, Map<String, String> environment, InputStream input)
      throws Exception {
    Path output = Files.createTempFile(files.directory, "command-", ".out");
    Path error = Files.createTempFile(files.directory, "command-", ".err");
    try {
      ProcessBuilder builder =
          new ProcessBuilder(arguments)
              .directory(files.root.toFile())
              .redirectOutput(output.toFile())
              .redirectError(error.toFile());
      builder.environment().putAll(environment);
      Process process = builder.start();
      var feedFailure = new AtomicReference<Exception>();
      Thread feeder =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try (var stream = process.getOutputStream()) {
                      if (input != null) {
                        input.transferTo(stream);
                      }
                    } catch (Exception e) {
                      feedFailure.set(e);
                    }
                  });
      try {
        if (!process.waitFor(180, TimeUnit.SECONDS)) {
          throw new IllegalStateException("隔离备份校验命令超时");
        }
        feeder.join(5000);
        if (feeder.isAlive() || feedFailure.get() != null || process.exitValue() != 0) {
          throw new IllegalStateException("隔离备份校验命令失败：" + arguments.get(1) + "；未向现有库执行恢复");
        }
      } finally {
        if (process.isAlive()) {
          process.destroyForcibly();
        }
        if (feeder.isAlive()) {
          process.getOutputStream().close();
          feeder.interrupt();
        }
      }
      return Files.size(output) < 100000 ? Files.readString(output) : "";
    } finally {
      Files.deleteIfExists(output);
      Files.deleteIfExists(error);
    }
  }
}

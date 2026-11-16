package com.alphascope.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 验证源码白名单和嵌套排除，运行日志、密钥及指向本机配置的符号链接不进入交付包。
 *
 * @author chenxuefeng
 */
class SourcePackagingTest {
  @TempDir Path root;

  @Test
  void nestedRuntimeSecretsBuildFilesAndSymlinksAreExcluded() throws Exception {
    fixture();
    for (String file :
        List.of(
            "backend/.runtime/logs/current.log",
            "frontend/.env",
            "backend/.env.production",
            "deploy/private.key",
            "deploy/.env",
            "backend/target/old.class",
            "frontend/node_modules/old.js",
            "backend/artifacts/old.zip",
            ".env")) {
      Files.createDirectories(root.resolve(file).getParent());
      Files.writeString(root.resolve(file), "must-not-deliver");
    }
    Files.writeString(root.resolve("backend/source.java"), "source");
    Files.createSymbolicLink(root.resolve("backend/local-config.txt"), root.resolve(".env"));
    Files.createSymbolicLink(root.resolve("frontend/linked-directory"), root.resolve("backend"));
    String previous = System.getProperty("alphascope.root");
    try {
      System.setProperty("alphascope.root", root.toString());
      ProjectTools.main(new String[] {"package"});
    } finally {
      if (previous == null) {
        System.clearProperty("alphascope.root");
      } else {
        System.setProperty("alphascope.root", previous);
      }
    }
    try (var zip = new ZipFile(root.resolve("artifacts/alphascope-source.zip").toFile())) {
      assertNotNull(zip.getEntry("alphascope/backend/source.java"));
      assertNotNull(zip.getEntry("alphascope/.env.example"));
      assertEquals(13, zip.size());
      assertTrue(
          zip.stream()
              .noneMatch(
                  entry ->
                      entry.getName().contains(".runtime")
                          || entry.getName().endsWith(".env")
                          || entry.getName().endsWith("local-config.txt")));
    }
  }

  @Test
  void rejectsLinkedSourceRootAndPreservesPreviousArchive() throws Exception {
    fixture();
    SourceArchive.write(root);
    Path archive = root.resolve("artifacts/alphascope-source.zip");
    byte[] original = Files.readAllBytes(archive);
    Files.delete(root.resolve("backend"));
    Files.createSymbolicLink(root.resolve("backend"), root.resolve("frontend"));
    assertThrows(IOException.class, () -> SourceArchive.write(root));
    assertTrue(Arrays.equals(original, Files.readAllBytes(archive)));
    try (var artifacts = Files.list(archive.getParent())) {
      assertEquals(
          List.of("alphascope-source.zip"),
          artifacts.map(path -> path.getFileName().toString()).toList());
    }
  }

  private void fixture() throws IOException {
    for (String directory :
        List.of("backend", "frontend", "database", "docs", "scripts", "deploy", ".mvn")) {
      Files.createDirectories(root.resolve(directory));
    }
    for (String file :
        List.of(
            "README.md",
            "THIRD-PARTY-NOTICES.md",
            "pom.xml",
            "docker-compose.yml",
            ".gitlab-ci.yml",
            ".editorconfig",
            "Dockerfile",
            ".dockerignore",
            ".env.example",
            ".gitignore",
            "mvnw",
            "mvnw.cmd")) {
      Files.writeString(root.resolve(file), "source");
    }
  }
}

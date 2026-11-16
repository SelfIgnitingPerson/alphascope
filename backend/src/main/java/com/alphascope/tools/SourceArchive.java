package com.alphascope.tools;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 生成唯一源码交付包；目录白名单、密钥排除和链接边界集中维护，不访问业务库。
 *
 * @author chenxuefeng
 */
final class SourceArchive {
  private static final List<String> SOURCE_DIRECTORIES =
      List.of("backend", "frontend", "database", "docs", "scripts", "deploy", ".mvn");
  private static final List<String> ROOT_FILES =
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
          "mvnw.cmd");
  private static final Set<String> EXCLUDED_NAMES =
      Set.of(
          "target",
          "node_modules",
          "dist",
          "__pycache__",
          ".DS_Store",
          ".env",
          ".runtime",
          "artifacts",
          ".git",
          ".idea",
          ".venv-market",
          ".m2",
          ".npm");

  private SourceArchive() {}

  /**
   * 将当前受控源码写入固定交付路径；先完成临时包再替换，失败保留上一份完整包。
   *
   * @param root 项目根目录
   * @return ZIP 中实际写入的源码文件数
   * @throws IOException 必需文件缺失、目录为符号链接或文件操作失败
   */
  static int write(Path root) throws IOException {
    List<Path> files = collect(root);
    Path artifacts = root.resolve("artifacts");
    Files.createDirectories(artifacts);
    Path temporary = Files.createTempFile(artifacts, "source-", ".tmp");
    try {
      try (var zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
        for (Path file : files.stream().sorted().toList()) {
          zip.putNextEntry(
              new ZipEntry("alphascope/" + root.relativize(file).toString().replace('\\', '/')));
          Files.copy(file, zip);
          zip.closeEntry();
        }
      }
      Files.move(
          temporary,
          artifacts.resolve("alphascope-source.zip"),
          StandardCopyOption.REPLACE_EXISTING);
      return files.size();
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static List<Path> collect(Path root) throws IOException {
    var files = new ArrayList<Path>();
    for (String name : SOURCE_DIRECTORIES) {
      Path directory = root.resolve(name);
      if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("源码目录缺失或不是普通目录：" + name);
      }
      // 在进入依赖和编译目录前剪枝，不遍历数万个文件后再过滤；默认不跟随符号链接。
      Files.walkFileTree(
          directory,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) {
              return excluded(path) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) {
              if (attributes.isRegularFile() && !excluded(path)) {
                files.add(path);
              }
              return FileVisitResult.CONTINUE;
            }
          });
    }
    for (String name : ROOT_FILES) {
      Path file = root.resolve(name);
      if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("必需源码文件缺失或不是普通文件：" + name);
      }
      files.add(file);
    }
    return files;
  }

  private static boolean excluded(Path path) {
    String name = path.getFileName().toString();
    return EXCLUDED_NAMES.contains(name)
        || name.startsWith(".env.") && !name.equals(".env.example")
        || name.matches(".*\\.(pem|key|p12|pfx|jks)$");
  }
}

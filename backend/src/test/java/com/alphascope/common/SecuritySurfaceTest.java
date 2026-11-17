package com.alphascope.common;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 锁定本轮Spring告警评估的适用面；新增相应能力必须重新安全评审，不替代依赖升级。
 *
 * @author chenxuefeng
 */
class SecuritySurfaceTest {
  @Test
  void applicationDoesNotIntroduceReviewedXsltSseOrFunctionalEndpointSurfaces() throws Exception {
    try (var files = Files.walk(Path.of("src/main/java"))) {
      for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String text = Files.readString(file);
        for (String name :
            new String[] {"XsltView", "SseEmitter", "ServerSentEvent", "RouterFunctions"})
          assertFalse(text.contains(name), file + "新增已评估的安全敏感能力，须先升级框架并重新评审：" + name);
      }
    }
  }
}

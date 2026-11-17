package com.alphascope.tools;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * 本机工具支持Docker及开发端口，但不把配置错误或凭据发送到远端。
 *
 * @author chenxuefeng
 */
class LocalApiEndpointTest {
  @Test
  void dockerAndExplicitDevelopmentPortsKeepPathAndQuery() {
    assertEquals(
        "http://127.0.0.1:5173/api/v1/market/coverage?date=2026-11-16",
        LocalApiEndpoint.resolve("http://127.0.0.1:5173", "/api/v1/market/coverage?date=2026-11-16")
            .toString());
    assertEquals(
        8080, LocalApiEndpoint.resolve("http://localhost:8080/", "/actuator/health").getPort());
  }

  @Test
  void remoteHostsCredentialsAndAlternateOriginsAreRejected() {
    for (String base :
        new String[] {
          "https://example.test",
          "http://user:password@localhost:8080",
          "http://localhost:8080/prefix",
          "http://localhost:8080?token=value",
          "file:///tmp"
        }) {
      assertThrows(
          IllegalArgumentException.class,
          () -> LocalApiEndpoint.resolve(base, "/api/v1/watchlist"));
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> LocalApiEndpoint.resolve("http://localhost:8080", "//example.test/api"));
  }
}

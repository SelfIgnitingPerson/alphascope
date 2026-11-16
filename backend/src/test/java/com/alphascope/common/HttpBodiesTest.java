package com.alphascope.common;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.common.util.HttpBodies;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 使用真实本机HTTP验证正文大小限制和响应头到达后的超时，防止上游挂起或截断成功。
 *
 * @author chenxuefeng
 */
class HttpBodiesTest {
  @Test
  void oversizedBodyIsRejectedInsteadOfReturningTruncatedSuccess() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/body",
        exchange -> {
          try (exchange) {
            byte[] body = "0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          }
        });
    server.start();
    try (var client = HttpClient.newHttpClient()) {
      var request =
          HttpRequest.newBuilder(
                  URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/body"))
              .build();
      assertThrows(
          java.io.IOException.class,
          () -> client.send(request, HttpBodies.limited(5, Duration.ofSeconds(1))));
      assertEquals(
          "0123456789",
          new String(
              client.send(request, HttpBodies.limited(10, Duration.ofSeconds(1))).body(),
              java.nio.charset.StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void stalledBodyIsCancelledAfterHeadersWithoutWaitingForUpstream() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/stall",
        exchange -> {
          try (exchange) {
            exchange.sendResponseHeaders(200, 10);
            exchange.getResponseBody().flush();
            try {
              Thread.sleep(700);
            } catch (InterruptedException error) {
              Thread.currentThread().interrupt();
            }
          }
        });
    server.start();
    try (var client = HttpClient.newHttpClient()) {
      var request =
          HttpRequest.newBuilder(
                  URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/stall"))
              .build();
      long start = System.nanoTime();
      assertThrows(
          java.io.IOException.class,
          () -> client.send(request, HttpBodies.limited(20, Duration.ofMillis(100))));
      assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 600);
    } finally {
      server.stop(0);
    }
  }
}

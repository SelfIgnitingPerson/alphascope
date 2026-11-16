package com.alphascope.market.provider.tushare;

import com.alphascope.common.task.ExecutionContext;
import com.alphascope.market.dto.TushareRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 共用的官方HTTP协议客户端，串行限速；权限失败不反复请求，取消不作为网络错误重试。
 *
 * @author chenxuefeng
 */
public class TushareClient implements AutoCloseable {
  private final String token;
  private final ObjectMapper json = new ObjectMapper();
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

  public TushareClient(String token) {
    this.token = token;
  }

  /**
   * 调用已声明的官方接口，保留原始列名称，不输出Token或完整请求正文。
   *
   * @param request 接口、官方参数及输出列
   * @return 原始供应商列字典，空响应保留为空
   */
  public synchronized List<Map<String, String>> query(TushareRequest request) {
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("TUSHARE_NOT_CONFIGURED");
    }
    for (int attempt = 0; attempt < 3; attempt++) {
      ExecutionContext.checkpoint();
      try {
        Thread.sleep(1300L * (attempt + 1));
        String body =
            json.writeValueAsString(
                Map.of(
                    "api_name",
                    request.api(),
                    "token",
                    token,
                    "params",
                    request.parameters(),
                    "fields",
                    request.fields() == null ? "" : request.fields()));
        var response =
            client.send(
                HttpRequest.newBuilder(URI.create("https://api.tushare.pro"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
          throw new IllegalStateException("TUSHARE_HTTP_" + response.statusCode());
        }
        JsonNode root = json.readTree(response.body());
        int code = root.path("code").asInt(-1);
        if (code != 0) {
          if (Set.of(-2001, -2002).contains(code)) {
            throw new SecurityException("TUSHARE_ACCESS_DENIED");
          }
          throw new IllegalStateException("TUSHARE_API_ERROR_" + code);
        }
        JsonNode fields = root.path("data").path("fields"), items = root.path("data").path("items");
        if (!fields.isArray() || !items.isArray()) {
          throw new IllegalStateException("TUSHARE_SCHEMA_ERROR");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (JsonNode item : items) {
          Map<String, String> row = new HashMap<>();
          for (int index = 0; index < fields.size(); index++) {
            if (!item.path(index).isNull()) {
              row.put(fields.get(index).asText(), item.path(index).asText());
            }
          }
          rows.add(row);
        }
        return rows;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        ExecutionContext.checkpoint();
        throw new IllegalStateException("TUSHARE_INTERRUPTED", e);
      } catch (SecurityException e) {
        throw e;
      } catch (Exception e) {
        ExecutionContext.checkpoint();
        if (attempt == 2) {
          throw new IllegalStateException("TUSHARE_UNAVAILABLE", e);
        }
      }
    }
    throw new IllegalStateException("TUSHARE_UNAVAILABLE");
  }

  /** 关闭HTTP连接池，释放客户端资源。 */
  @Override
  public void close() {
    client.close();
  }
}

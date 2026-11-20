package com.alphascope.market.provider.baostock;

import com.alphascope.common.diagnostic.baostock.BaoStockApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;

/**
 * 原生Java实现BaoStock公开线协议；有界收包、分页、超时及串行持久会话，无Python进程。
 *
 * @author chenxuefeng
 */
public final class BaoStockClient implements AutoCloseable {
  private static final String SEP = "\u0001";
  private static final byte[] END = "<![CDATA[]]>\n".getBytes(StandardCharsets.US_ASCII);
  private final String host;
  private final int port, timeout;
  private final ObjectMapper json = new ObjectMapper();
  private final BaoStockAccessGuard accessGuard;
  private final BaoStockRequestPolicy requestPolicy;
  private Socket socket;
  private InputStream input;

  public BaoStockClient(String host, int port, int timeoutMillis) {
    this(
        host,
        port,
        timeoutMillis,
        "public-api.baostock.com".equals(host) ? BaoStockAccessGuard.standard() : null);
  }

  public BaoStockClient(String host, int port, int timeoutMillis, BaoStockAccessGuard accessGuard) {
    this(
        new Settings(
            host,
            port,
            timeoutMillis,
            accessGuard,
            "public-api.baostock.com".equals(host) ? BaoStockRequestPolicy.standard(40000) : null));
  }

  public BaoStockClient(Settings settings) {
    this.host = settings.host();
    this.port = settings.port();
    this.timeout = settings.timeoutMillis();
    this.accessGuard = settings.accessGuard();
    this.requestPolicy = settings.requestPolicy();
  }

  public synchronized List<Map<String, String>> query(
      String type, String method, int fieldIndex, String... arguments) {
    if (accessGuard != null) {
      accessGuard.check();
    }
    for (int retry = 0; retry < 2; retry++) {
      try {
        if (socket == null || socket.isClosed()) {
          if (requestPolicy != null) requestPolicy.acquireSession();
          socket = new Socket();
          socket.connect(new InetSocketAddress(host, port), timeout);
          socket.setSoTimeout(timeout);
          input = new BufferedInputStream(socket.getInputStream());
          check(exchange("00", String.join(SEP, "login", "anonymous", "123456", "0")));
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (int page = 1; page <= 100; page++) {
          List<String> params =
              new ArrayList<>(List.of(method, "anonymous", String.valueOf(page), "2000"));
          params.addAll(Arrays.asList(arguments));
          String[] fields = exchange(type, String.join(SEP, params));
          check(fields);
          if (fields.length <= fieldIndex) {
            throw new IOException("INVALID_RESPONSE_FIELDS");
          }
          String[] names = fields[fieldIndex].strip().split(",", -1);
          JsonNode records = json.readTree(fields[6]).path("record");
          if (!records.isArray()) {
            throw new IOException("INVALID_RESPONSE_RECORDS");
          }
          for (JsonNode row : records) {
            if (row.size() != names.length) {
              throw new IOException("INVALID_RESPONSE_COLUMNS");
            }
            Map<String, String> value = new LinkedHashMap<>();
            for (int i = 0; i < names.length; i++) {
              value.put(names[i].strip(), row.get(i).asText());
            }
            result.add(value);
          }
          if (records.size() < 2000) {
            return result;
          }
        }
        throw new IOException("PAGE_LIMIT");
      } catch (RuntimeException e) {
        close();
        throw e;
      } catch (IOException e) {
        close();
        if (retry == 1) {
          throw new IllegalStateException("BAOSTOCK_UNAVAILABLE", e);
        }
      }
    }
    throw new IllegalStateException("BAOSTOCK_UNAVAILABLE");
  }

  /** 官方98/99报文为单日全量快照，不带分页参数，字段与记录位置也不同。 */
  public synchronized List<Map<String, String>> dailyStocks(LocalDate date) {
    return dailyReport("98", "query_daily_history_k_AStock", date);
  }

  /** 获取供应商指定日的全量除权事件，成功的空数组表示当天无事件。 */
  public synchronized List<Map<String, String>> dailyAdjustments(LocalDate date) {
    return dailyReport("9C", "query_daily_adjust_factor", date);
  }

  private List<Map<String, String>> dailyReport(String type, String method, LocalDate date) {
    if (accessGuard != null) {
      accessGuard.check();
    }
    for (int retry = 0; retry < 2; retry++) {
      try {
        if (socket == null || socket.isClosed()) {
          if (requestPolicy != null) requestPolicy.acquireSession();
          socket = new Socket();
          socket.connect(new InetSocketAddress(host, port), timeout);
          socket.setSoTimeout(timeout);
          input = new BufferedInputStream(socket.getInputStream());
          check(exchange("00", String.join(SEP, "login", "anonymous", "123456", "0")));
        }
        var fields = exchange(type, String.join(SEP, method, "anonymous", date.toString()));
        check(fields);
        if (fields.length < 6) {
          throw new IOException("INVALID_RESPONSE_FIELDS");
        }
        var names = fields[5].strip().split(",", -1);
        var rows = json.readTree(fields[4]).path("record");
        if (!rows.isArray() || rows.size() > 20000) {
          throw new IOException("INVALID_RESPONSE_RECORDS");
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (var row : rows) {
          if (row.size() != names.length) {
            throw new IOException("INVALID_RESPONSE_COLUMNS");
          }
          Map<String, String> values = new LinkedHashMap<>();
          for (int i = 0; i < names.length; i++) {
            values.put(names[i].strip(), row.get(i).asText());
          }
          result.add(values);
        }
        return result;
      } catch (RuntimeException e) {
        close();
        throw e;
      } catch (IOException e) {
        close();
        if (retry == 1) {
          throw new IllegalStateException("BAOSTOCK_UNAVAILABLE", e);
        }
      }
    }
    throw new IllegalStateException("BAOSTOCK_UNAVAILABLE");
  }

  private void check(String[] fields) throws IOException {
    if (fields.length == 0 || !fields[0].matches("[0-9]{1,10}")) {
      throw new IOException("INVALID_RESPONSE_ERROR_CODE");
    }
    if (!"0".equals(fields[0])) {
      var failure = new BaoStockApiException(fields[0], fields.length > 1 ? fields[1] : null);
      if (fields[0].equals("10001011") && accessGuard != null) accessGuard.restrict(failure);
      // 上游已明确返回业务错误，不按IO断线重连；包括未收录的新数字码。
      throw failure;
    }
    if (fields.length < 2) throw new IOException("INVALID_RESPONSE_FIELDS");
  }

  private String[] exchange(String type, String body) throws IOException {
    if (requestPolicy != null) requestPolicy.beforeRequest();
    byte[] request = encode(type, body);
    socket.getOutputStream().write(request);
    socket.getOutputStream().flush();
    byte[] header = readExact(input, 21);
    String[] parts = new String(header, StandardCharsets.US_ASCII).split(SEP, -1);
    if (parts.length != 3) {
      throw new IOException("INVALID_HEADER");
    }
    int length;
    try {
      length = Integer.parseInt(parts[2]);
    } catch (NumberFormatException e) {
      throw new IOException("INVALID_LENGTH", e);
    }
    if (length < 0 || length > 20_000_000) {
      throw new IOException("FRAME_TOO_LARGE");
    }
    // 非压缩中文响应的头长度可能按字符计数；以官方帧尾截断并保持总大小上限。
    ByteArrayOutputStream frame = new ByteArrayOutputStream();
    int matched = 0;
    for (int i = 0; i < 20_000_128; i++) {
      int next = input.read();
      if (next < 0) {
        throw new EOFException();
      }
      frame.write(next);
      matched = next == END[matched] ? matched + 1 : next == END[0] ? 1 : 0;
      if (matched == END.length) {
        byte[] bytes = frame.toByteArray();
        if (Set.of("96", "99", "9B", "9D").contains(parts[1])) {
          if (length > bytes.length - END.length) {
            throw new IOException("INVALID_LENGTH");
          }
          return decodeBody(parts[1], Arrays.copyOf(bytes, length)).split(SEP, -1);
        }
        int end = bytes.length - END.length;
        while (end > 0 && bytes[end - 1] != 1) {
          end--;
        }
        if (end == 0) {
          throw new IOException("INVALID_FRAME_CRC");
        }
        return new String(bytes, 0, end - 1, StandardCharsets.UTF_8).split(SEP, -1);
      }
    }
    throw new IOException("INVALID_FRAME_END");
  }

  public static byte[] encode(String type, String body) {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    String header =
        "00.9.40" + SEP + type + SEP + String.format(Locale.ROOT, "%010d", bytes.length);
    byte[] content = (header + body).getBytes(StandardCharsets.UTF_8);
    CRC32 crc = new CRC32();
    crc.update(content);
    return (header + body + SEP + crc.getValue() + "\n").getBytes(StandardCharsets.UTF_8);
  }

  public static String decodeBody(String type, byte[] payload) throws IOException {
    if (!Set.of("96", "99", "9B", "9D").contains(type)) {
      return new String(payload, StandardCharsets.UTF_8);
    }
    try (var input = new InflaterInputStream(new ByteArrayInputStream(payload));
        var output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int n;
      while ((n = input.read(buffer)) != -1) {
        if (output.size() + n > 40_000_000) {
          throw new IOException("INFLATED_FRAME_TOO_LARGE");
        }
        output.write(buffer, 0, n);
      }
      return output.toString(StandardCharsets.UTF_8);
    }
  }

  /**
   * 客户端连接选项；生产必须提供访问保护与共享目录预算，测试可使用隔离本地协议服务。
   *
   * @author chenxuefeng
   */
  public record Settings(
      /** 服务主机。 */
      String host,
      /** 服务端口。 */
      int port,
      /** 连接及读取超时毫秒。 */
      int timeoutMillis,
      /** 持久访问保护。 */
      BaoStockAccessGuard accessGuard,
      /** 连接互斥与请求预算。 */
      BaoStockRequestPolicy requestPolicy) {}

  private static byte[] readExact(InputStream input, int length) throws IOException {
    byte[] bytes = input.readNBytes(length);
    if (bytes.length != length) {
      throw new EOFException();
    }
    return bytes;
  }

  public synchronized void close() {
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException ignored) {
      } finally {
        socket = null;
        input = null;
      }
    }
    if (requestPolicy != null) requestPolicy.close();
  }
}

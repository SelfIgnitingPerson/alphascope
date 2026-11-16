package com.alphascope.market.provider.sina;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;

/**
 * JVM内运行固定数据解码器。只接受编码字符，禁用Java类访问，不执行远端返回的脚本。
 *
 * @author chenxuefeng
 */
public final class SinaDecoder {
  private final String algorithm;
  private final ObjectMapper json = new ObjectMapper();

  public SinaDecoder() {
    try (var input = getClass().getResourceAsStream("/market/sina-decoder.js")) {
      if (input == null) {
        throw new IllegalStateException("缺少固定解码资源");
      }
      algorithm = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public JsonNode decode(String encoded) {
    if (encoded == null
        || encoded.isEmpty()
        || encoded.length() > 8_000_000
        || !encoded.matches("[A-Za-z0-9+/=]+")) {
      throw new IllegalArgumentException("INVALID_SINA_ENCODING");
    }
    ContextFactory factory =
        new ContextFactory() {
          protected Context makeContext() {
            Context c = super.makeContext();
            c.setOptimizationLevel(-1);
            c.setInstructionObserverThreshold(10000);
            return c;
          }

          protected void observeInstructionCount(Context c, int count) {
            if (System.nanoTime() > (long) c.getThreadLocal("deadline")) {
              throw new IllegalStateException("SINA_DECODE_TIMEOUT");
            }
          }
        };
    try {
      return factory.call(
          context -> {
            context.putThreadLocal("deadline", System.nanoTime() + 5_000_000_000L);
            context.setClassShutter(name -> false);
            Scriptable scope = context.initSafeStandardObjects();
            context.evaluateString(scope, algorithm, "fixed-sina-decoder", 1, null);
            Function function = (Function) scope.get("d", scope);
            Object value = function.call(context, scope, scope, new Object[] {encoded});
            Object serialized = NativeJSON.stringify(context, scope, value, null, null);
            try {
              return json.readTree(Context.toString(serialized));
            } catch (IOException e) {
              throw new IllegalStateException("INVALID_SINA_DATA", e);
            }
          });
    } catch (RhinoException e) {
      throw new IllegalStateException("SINA_DECODE_FAILED", e);
    }
  }

  public static String assignmentData(String response) {
    // 上游可能附加另一条赋值或防缓存注释，只提取首条赋值中的编码字串。
    var match =
        Pattern.compile("^\\s*(?:var\\s+)?[A-Za-z_$][A-Za-z0-9_$]*\\s*=\\s*\"([A-Za-z0-9+/=]+)\"")
            .matcher(response);
    if (!match.find()) {
      throw new IllegalArgumentException("INVALID_SINA_ASSIGNMENT");
    }
    return match.group(1);
  }
}

package com.alphascope.common.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/**
 * 在流读取期间限制HTTP正文大小与独立时限；不依赖仅覆盖响应头的请求超时。
 *
 * @author chenxuefeng
 */
public final class HttpBodies {
  private HttpBodies() {}

  /**
   * 构造超出长度立即取消订阅的正文处理器。
   *
   * @param maximumBytes 正文字节上限
   * @return 完整且未超限的响应字节处理器
   */
  public static HttpResponse.BodyHandler<byte[]> limited(int maximumBytes) {
    if (maximumBytes < 1) throw new IllegalArgumentException("HTTP正文上限必须为正数");
    return limited(maximumBytes, Duration.ofSeconds(30));
  }

  /**
   * 构造具有独立正文截止的有界处理器。
   *
   * @param maximumBytes 最大正文字节
   * @param timeout 响应头到达后的最长正文读取时间
   * @return 超限或超时会取消订阅的处理器
   */
  public static HttpResponse.BodyHandler<byte[]> limited(int maximumBytes, Duration timeout) {
    if (maximumBytes < 1 || timeout == null || timeout.isZero() || timeout.isNegative())
      throw new IllegalArgumentException("HTTP正文限制无效");
    return information -> new LimitedSubscriber(maximumBytes, timeout);
  }

  /**
   * 逐批有界接收，不缓存超出上限的数据，也不返回被截断的成功正文。
   *
   * @author chenxuefeng
   */
  private static final class LimitedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final int maximum;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private volatile Flow.Subscription subscription;

    private LimitedSubscriber(int maximum, Duration timeout) {
      this.maximum = maximum;
      body.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
          .whenComplete(
              (value, error) -> {
                if (error != null && subscription != null) subscription.cancel();
              });
    }

    @Override
    public CompletionStage<byte[]> getBody() {
      return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription value) {
      if (subscription != null) {
        value.cancel();
        return;
      }
      subscription = value;
      if (body.isDone()) {
        value.cancel();
        return;
      }
      value.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
      if (body.isDone()) return;
      long size = bytes.size();
      for (var buffer : buffers) size += buffer.remaining();
      if (size > maximum) {
        subscription.cancel();
        body.completeExceptionally(new IOException("HTTP_BODY_LIMIT_EXCEEDED"));
        return;
      }
      for (var buffer : buffers) {
        byte[] part = new byte[buffer.remaining()];
        buffer.get(part);
        bytes.writeBytes(part);
      }
      subscription.request(1);
    }

    @Override
    public void onError(Throwable error) {
      body.completeExceptionally(error);
    }

    @Override
    public void onComplete() {
      body.complete(bytes.toByteArray());
    }
  }
}

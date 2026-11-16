package com.alphascope.market.provider.baostock;

import com.alphascope.common.task.ExecutionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 免费节点请求策略：连接全生命周期互斥，上海自然日预算先落盘后发送，重启不重置额度。
 *
 * <p>锁只约束使用同一运行目录的本项目进程，无法统计同一出口IP的其他程序。
 *
 * @author chenxuefeng
 */
public final class BaoStockRequestPolicy implements AutoCloseable {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final Path directory;
  private final int limit;
  private final Clock clock;
  private FileChannel channel;
  private FileLock lock;
  private long lastRequestNanos;

  public BaoStockRequestPolicy(Path directory, int limit, Clock clock) {
    if (limit < 1 || limit > 45000) throw new IllegalArgumentException("BaoStock本机预算必须在1至45000之间");
    this.directory = directory;
    this.limit = limit;
    this.clock = clock;
  }

  /**
   * 标准运行目录；不连接供应商，也不更换主机绕过限制。
   *
   * @param limit 本机每日保守预算，包含登录、分页和重试
   * @return 当前进程的策略对象
   */
  public static BaoStockRequestPolicy standard(int limit) {
    return new BaoStockRequestPolicy(
        Path.of(System.getProperty("alphascope.root", "."), ".runtime", "baostock"),
        limit,
        Clock.systemUTC());
  }

  /** 在打开Socket之前取得连接锁；其他进程正在持有会话时明确失败，不建立第二条连接。 */
  public synchronized void acquireSession() {
    if (lock != null && lock.isValid()) return;
    try {
      Files.createDirectories(directory);
      channel =
          FileChannel.open(
              directory.resolve("session.lock"),
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE);
      try {
        lock = channel.tryLock();
      } catch (OverlappingFileLockException e) {
        lock = null;
      }
      if (lock == null) {
        close();
        throw new IllegalStateException("BAOSTOCK_SESSION_BUSY：本项目其他进程持有连接，请停止重复采集进程后再试");
      }
    } catch (IOException e) {
      close();
      throw new IllegalStateException("BAOSTOCK_POLICY_UNAVAILABLE", e);
    }
  }

  /** 每条网络请求发送前预占预算；断网、登录和失败重试同样计数，不退回可能已被上游接收的请求。 */
  public synchronized void beforeRequest() {
    ExecutionContext.checkpoint();
    if (lock == null || !lock.isValid())
      throw new IllegalStateException("BAOSTOCK_SESSION_LOCK_REQUIRED");
    // 节流使用单调时钟，时区与系统校时不影响请求间隔。
    long remaining = 250_000_000L - (System.nanoTime() - lastRequestNanos);
    if (remaining > 0) {
      try {
        Thread.sleep(java.time.Duration.ofNanos(remaining));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        ExecutionContext.checkpoint();
      }
    }
    ExecutionContext.checkpoint();
    Usage usage = usage();
    if (usage.requests() >= limit)
      throw new IllegalStateException("BAOSTOCK_DAILY_BUDGET_EXHAUSTED：本机当日请求预算已用完，等待上海下一自然日");
    Path temporary = null;
    try {
      temporary = Files.createTempFile(directory, "usage-", ".json");
      Files.writeString(
          temporary, JSON.writeValueAsString(new Usage(usage.date(), usage.requests() + 1)));
      Files.move(
          temporary,
          directory.resolve("usage.json"),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      lastRequestNanos = System.nanoTime();
    } catch (IOException e) {
      throw new IllegalStateException("BAOSTOCK_BUDGET_WRITE_FAILED", e);
    } finally {
      if (temporary != null)
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          /* 原子替换后通常已不存在。 */
        }
    }
  }

  /**
   * 只读统计，不创建文件、不获取会话锁、不访问上游。
   *
   * @return 当日已预占请求数；损坏或时钟倒退时拒绝默认为零
   */
  public Usage usage() {
    LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
    Path file = directory.resolve("usage.json");
    if (!Files.exists(file)) return new Usage(today.toString(), 0);
    try {
      var data = JSON.readTree(Files.readString(file));
      var date = LocalDate.parse(data.path("date").asText());
      var requests = data.path("requests");
      if (!requests.isIntegralNumber()
          || !requests.canConvertToInt()
          || requests.asInt() < 0
          || date.isAfter(today)) throw new IllegalArgumentException("invalid usage");
      return new Usage(today.toString(), date.equals(today) ? requests.asInt() : 0);
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException("BAOSTOCK_BUDGET_STATE_UNREADABLE", e);
    }
  }

  /** 关闭Socket后释放连接锁；不删除预算及上游访问保护。 */
  @Override
  public synchronized void close() {
    try {
      if (lock != null) lock.release();
    } catch (IOException ignored) {
      /* 关闭通道仍会释放文件锁。 */
    } finally {
      lock = null;
      try {
        if (channel != null) channel.close();
      } catch (IOException ignored) {
        /* 关闭失败交由系统回收。 */
      } finally {
        channel = null;
      }
    }
  }

  /**
   * 本机请求预算记录，不代表供应商对同一出口IP的总计数。
   *
   * @author chenxuefeng
   */
  public record Usage(String date, int requests) {}
}

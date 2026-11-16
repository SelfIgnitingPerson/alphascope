package com.alphascope.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.common.config.TaskConfig;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 真实执行线程验证：业务与备份阻塞时心跳和截止监控仍获得独立执行机会。
 *
 * @author chenxuefeng
 */
class TaskIsolationTest {
  @Test
  void blockedWorkerAndBackupDoNotStarveHeartbeatOrDeadlineMonitoring() throws Exception {
    var config = new TaskConfig();
    var worker = config.workerScheduler();
    var backup = config.backupScheduler();
    var heartbeat = config.heartbeatScheduler();
    var control = config.controlScheduler();
    var schedulers = List.of(worker, backup, heartbeat, control);
    schedulers.forEach(scheduler -> scheduler.initialize());
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    Runnable slow =
        () -> {
          entered.countDown();
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };
    try {
      worker.execute(slow);
      backup.execute(slow);
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      var beat = new CompletableFuture<String>();
      var deadline = new CompletableFuture<String>();
      heartbeat.execute(() -> beat.complete(Thread.currentThread().getName()));
      control.execute(() -> deadline.complete(Thread.currentThread().getName()));
      assertTrue(beat.get(1, TimeUnit.SECONDS).startsWith("alphascope-heartbeat-"));
      assertTrue(deadline.get(1, TimeUnit.SECONDS).startsWith("alphascope-control-"));
    } finally {
      release.countDown();
      schedulers.forEach(scheduler -> scheduler.destroy());
    }
  }
}

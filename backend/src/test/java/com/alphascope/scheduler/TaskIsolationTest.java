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
  void sharedHistoryCollectionDoesNotBlockCachedPrivateResearch() throws Exception {
    var config = new TaskConfig();
    var collection = config.collectionScheduler();
    var privateResearch = config.workerScheduler();
    collection.initialize();
    privateResearch.initialize();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try {
      collection.execute(
          () -> {
            entered.countDown();
            try {
              release.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      var completed = new CompletableFuture<String>();
      privateResearch.execute(() -> completed.complete(Thread.currentThread().getName()));
      assertTrue(completed.get(1, TimeUnit.SECONDS).startsWith("alphascope-worker-"));
      assertEquals(1, release.getCount(), "私人研究完成时共享采集仍在执行");
    } finally {
      release.countDown();
      collection.destroy();
      privateResearch.destroy();
    }
  }

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

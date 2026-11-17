package com.alphascope.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alphascope.common.exception.BusinessException;
import com.alphascope.operations.config.ProductProperties;
import com.alphascope.operations.config.StorageWriteGuard;
import java.nio.file.Path;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 低磁盘拒绝新增，但任务终止和错误审计仍能保存，避免预留永不释放。 @author chenxuefeng */
class StorageWriteGuardTest {
  @TempDir Path root;

  @Test
  void lowDiskBlocksNewEvidenceButAllowsStageFinalization() throws Throwable {
    String before = System.getProperty("alphascope.root");
    System.setProperty("alphascope.root", root.toString());
    try {
      var options = new ProductProperties();
      options.setMinimumRuntimeFreeBytes(Long.MAX_VALUE);
      var guard = new StorageWriteGuard(options);
      var configuration = new Configuration();
      var executor = mock(Executor.class);
      var method = Executor.class.getMethod("update", MappedStatement.class, Object.class);
      var insert =
          new MappedStatement.Builder(
                  configuration,
                  "ReportMapper.insert",
                  new StaticSqlSource(
                      configuration, "INSERT INTO analysis_report(content_json) VALUES(?)"),
                  SqlCommandType.INSERT)
              .build();
      assertThrows(
          BusinessException.class,
          () -> guard.intercept(new Invocation(executor, method, new Object[] {insert, null})));
      verifyNoInteractions(executor);
      var finish =
          new MappedStatement.Builder(
                  configuration,
                  "JobMapper.stageEnd",
                  new StaticSqlSource(
                      configuration, "UPDATE job_stage SET details_json=?,status='FAILED'"),
                  SqlCommandType.UPDATE)
              .build();
      when(executor.update(finish, null)).thenReturn(1);
      assertEquals(
          1, guard.intercept(new Invocation(executor, method, new Object[] {finish, null})));
    } finally {
      if (before == null) System.clearProperty("alphascope.root");
      else System.setProperty("alphascope.root", before);
    }
  }
}

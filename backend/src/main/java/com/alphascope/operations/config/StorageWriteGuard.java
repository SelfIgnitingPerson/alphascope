package com.alphascope.operations.config;

import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.response.ResultCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.stereotype.Component;

/**
 * 运行资料磁盘不足时拒绝新增证据；数据库逻辑预算由原子触发器独立保证。
 *
 * @author chenxuefeng
 */
@Component
@Intercepts(
    @Signature(
        type = Executor.class,
        method = "update",
        args = {MappedStatement.class, Object.class}))
public class StorageWriteGuard implements Interceptor {
  private final ProductProperties options;
  private volatile long checkedAt, available = Long.MAX_VALUE;

  public StorageWriteGuard(ProductProperties options) {
    this.options = options;
  }

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    var statement = (MappedStatement) invocation.getArgs()[0];
    String sql =
        statement
            .getBoundSql(invocation.getArgs()[1])
            .getSql()
            .stripLeading()
            .toUpperCase(java.util.Locale.ROOT);
    // 终止和心跳必须仍可持久化；低磁盘不能阻止任务释放预留和保存失败原因。
    boolean terminalMetadata =
        statement.getId().endsWith(".stageEnd") || statement.getId().endsWith(".stopRunningStages");
    if (!terminalMetadata
        && (sql.startsWith("INSERT") || sql.startsWith("UPDATE"))
        && (sql.contains("JSON")
            || statement.getId().contains("Save")
            || statement.getId().contains(".save")
            || statement.getId().contains(".insert")
            || statement.getId().contains(".input")
            || statement.getId().contains(".start")
            || statement.getId().contains(".review"))) {
      long now = System.nanoTime();
      if (now - checkedAt > 1_000_000_000L) {
        var root = Path.of(System.getProperty("alphascope.root", "."));
        if (Files.isDirectory(root.resolve(".runtime"))) root = root.resolve(".runtime");
        available = Files.getFileStore(root).getUsableSpace();
        checkedAt = now;
      }
      if (available < options.getMinimumRuntimeFreeBytes())
        throw new BusinessException(
            ResultCode.CAPACITY_EXCEEDED, 507, "运行资料所在文件系统剩余空间低于保护线，暂停新增；已有证据保留，请管理员处理存储");
    }
    return invocation.proceed();
  }
}

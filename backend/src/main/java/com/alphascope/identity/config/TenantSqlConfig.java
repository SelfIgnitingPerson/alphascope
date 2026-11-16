package com.alphascope.identity.config;

import com.alphascope.identity.context.TenantContext;
import com.alphascope.operations.mapper.PlatformCapacityMapper;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import java.util.List;
import java.util.Locale;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.schema.Column;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 使用SQL语法树将租户条件应用于查询、写入与子查询；不允许解析失败后跳过隔离。
 *
 * @author chenxuefeng
 */
@Configuration
@EnableConfigurationProperties(AccessProperties.class)
public class TenantSqlConfig {
  @Bean
  public MybatisPlusInterceptor tenantInterceptor(AccessProperties properties) {
    // plain MyBatis不自动扫描Plus注解；这里只登记唯一的数字聚合例外。
    InterceptorIgnoreHelper.initSqlParserInfoCache(PlatformCapacityMapper.class);
    var interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(
        new TenantLineInnerInterceptor(
            new TenantLineHandler() {
              @Override
              public Expression getTenantId() {
                if (properties.getMode() == AccessProperties.Mode.PUBLIC
                    && TenantContext.user() == null)
                  throw new IllegalStateException("私人研究SQL缺少已验证的执行身份");
                return new LongValue(TenantContext.id());
              }

              @Override
              public boolean ignoreTable(String table) {
                String name = table.replace("`", "").toLowerCase(Locale.ROOT);
                if (TenantTables.SHARED.contains(name) || TenantTables.CONTROL.contains(name))
                  return true;
                if (!TenantTables.PRIVATE.contains(name))
                  throw new IllegalStateException("SQL表未登记访问边界：" + name);
                return false;
              }

              @Override
              public boolean ignoreInsert(List<Column> columns, String column) {
                if (columns.stream()
                    .anyMatch(
                        value -> value.getColumnName().replace("`", "").equalsIgnoreCase(column)))
                  throw new IllegalStateException("业务SQL不能手工提供身份字段");
                return false;
              }
            }));
    return interceptor;
  }
}

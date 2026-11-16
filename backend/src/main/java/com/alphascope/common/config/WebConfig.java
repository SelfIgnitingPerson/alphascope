package com.alphascope.common.config;

import com.alphascope.identity.config.AccessProperties;
import com.alphascope.identity.config.AccessProperties.Mode;
import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 本机前端跨域与Web访问配置；不将服务暴露为公共交易接口。
 *
 * @author chenxuefeng
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final AccessProperties access;
  private final Environment environment;

  public WebConfig(AccessProperties access, Environment environment) {
    this.access = access;
    this.environment = environment;
  }

  public void addCorsMappings(CorsRegistry r) {
    r.addMapping("/api/**")
        .allowedOrigins(
            (access.getMode() == Mode.LOCAL
                        && environment.containsProperty("alphascope.web.allowed-origins")
                    ? Arrays.asList(
                        environment.getProperty("alphascope.web.allowed-origins").split(","))
                    : access.getOrigins())
                .toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("Content-Type", "X-CSRF-Token")
        .exposedHeaders("X-Request-Id")
        .allowCredentials(true);
  }
}

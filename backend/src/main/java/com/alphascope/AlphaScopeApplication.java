package com.alphascope;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 模块化单体入口；数据库结构只由 Flyway 迁移管理。
 *
 * @author chenxuefeng
 */
@SpringBootApplication
public class AlphaScopeApplication {
  public static void main(String[] args) {
    SpringApplication.run(AlphaScopeApplication.class, args);
  }
}

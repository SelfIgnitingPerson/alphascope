package com.alphascope.operations.policy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 研究响应的用途边界；类上声明后新方法自动继承，不按URL猜测。
 *
 * @author chenxuefeng
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ResearchData {
  /**
   * 本次响应所需的实际来源用途。
   *
   * @return 明确用途代码
   */
  String[] permissions() default {"DISPLAY"};
}

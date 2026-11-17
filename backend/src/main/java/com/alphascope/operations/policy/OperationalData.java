package com.alphascope.operations.policy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 身份、配置或不含研究正文的操作元数据；不得用于返回行情及派生数值。
 *
 * @author chenxuefeng
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationalData {}

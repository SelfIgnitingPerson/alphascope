package com.alphascope.operations.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用产品资源及授权配置校验。
 *
 * @author chenxuefeng
 */
@Configuration
@EnableConfigurationProperties(ProductProperties.class)
public class ProductConfig {}

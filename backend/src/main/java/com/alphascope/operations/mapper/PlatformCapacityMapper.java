package com.alphascope.operations.mapper;

import com.alphascope.operations.vo.PlatformUsage;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;

/**
 * 唯一允许跳过研究租户条件的只读总量入口，仅COUNT/SUM聚合，不能返回用户或业务行。
 *
 * @author chenxuefeng
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface PlatformCapacityMapper {
  /**
   * 读取平台资源总量供全局限额及管理员运维，不作为用户研究查询。
   *
   * @return 无身份与正文的聚合数字
   */
  PlatformUsage usage();
}

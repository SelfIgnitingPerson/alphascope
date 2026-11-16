package com.alphascope.operations.mapper;

import com.alphascope.operations.vo.ResearchCapacity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 研究资源只读核查，所有表保留租户SQL隔离。
 *
 * @author chenxuefeng
 */
@Mapper
public interface CapacityMapper {
  /**
   * 读取当前用户队列与完整验证证据预算。
   *
   * @return 当前范围的实际统计
   */
  ResearchCapacity capacity();

  /**
   * 锁定当前用户账号，序列化同一用户的配额检查与入队。
   *
   * @param userId 服务端已验证的用户主键
   * @return 被锁定主键或null
   */
  Long lockUser(long userId);
}

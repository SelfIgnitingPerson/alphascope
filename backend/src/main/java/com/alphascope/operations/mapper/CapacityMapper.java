package com.alphascope.operations.mapper;

import com.alphascope.operations.vo.ResearchCapacity;
import com.alphascope.operations.vo.StorageTotals;
import org.apache.ibatis.annotations.Mapper;

/**
 * 研究资源只读核查，所有表保留租户SQL隔离。
 *
 * @author chenxuefeng
 */
@Mapper
public interface CapacityMapper {
  /**
   * 读取当前用户队列与全部私人正文预算。
   *
   * @param scope 当前已认证用户主键
   * @param scope 当前验证身份的空间主键
   * @return 当前范围的实际统计
   */
  ResearchCapacity capacity(long scope);

  /**
   * 锁定当前用户账号，序列化同一用户的配额检查与入队。
   *
   * @param userId 服务端已验证的用户主键
   * @return 被锁定主键或null
   */
  Long lockUser(long userId);

  /**
   * 读取服务端已验证用户的逻辑预算。
   *
   * @param scope 当前身份
   * @return 私人使用、预留、上限，不含其他用户内容
   */
  StorageTotals storage(long scope);
}

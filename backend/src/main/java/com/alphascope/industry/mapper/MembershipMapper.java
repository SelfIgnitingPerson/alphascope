package com.alphascope.industry.mapper;

import com.alphascope.industry.dto.MembershipWriteCommand;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登记标的与主题的有效区间；已保存报告使用冻结输入保持历史独立。
 *
 * @author chenxuefeng
 */
@Mapper
public interface MembershipMapper {

  /**
   * 按主题业务代码新增证券的真实有效归属区间，不倒填历史成分。
   *
   * @param command 证券主键、主题代码、生效日期及可空的结束日期
   * @return 插入行数；1 表示成功，0 表示主题代码不存在
   */
  int add(MembershipWriteCommand command);
}

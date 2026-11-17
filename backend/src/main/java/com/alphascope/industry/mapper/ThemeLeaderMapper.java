package com.alphascope.industry.mapper;

import com.alphascope.industry.entity.ThemeLeaderRevision;
import org.apache.ibatis.annotations.Mapper;

/**
 * 共享研究目录追加修订；写入口由管理员校验，禁止覆盖旧版本。
 *
 * @author chenxuefeng
 */
@Mapper
public interface ThemeLeaderMapper {
  /**
   * 在当前写事务锁住主题主记录，事务结束自动释放。
   *
   * @param code 已登记主题代码
   * @return 主题主键，不存在时为null
   */
  Long lockTheme(String code);

  /**
   * 读取主题最近不可变修订。
   *
   * @param code 主题代码
   * @return 修订或null
   */
  ThemeLeaderRevision latest(String code);

  /**
   * 追加目录并回填主键，不修改既有修订。
   *
   * @param row 已核对管理员与指纹的目录
   */
  void insert(ThemeLeaderRevision row);
}

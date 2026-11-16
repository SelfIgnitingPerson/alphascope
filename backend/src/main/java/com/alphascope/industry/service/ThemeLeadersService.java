package com.alphascope.industry.service;

import com.alphascope.industry.vo.ThemeLeadersView;
import java.util.List;

/**
 * 当前产业核心企业目录，不改变冻结披露样本、个人自选或评分规则。
 *
 * @author chenxuefeng
 */
public interface ThemeLeadersService {
  /**
   * 读取全部已整理主题的核心企业与核验依据。
   *
   * @return 当前目录，不能用作历史时点的成分名单
   */
  List<ThemeLeadersView> list();

  /**
   * 根据主题读取当前核心企业组。
   *
   * @param code 已登记主题代码
   * @return 带整理日期、业务地位和原始来源的目录
   */
  ThemeLeadersView get(String code);
}

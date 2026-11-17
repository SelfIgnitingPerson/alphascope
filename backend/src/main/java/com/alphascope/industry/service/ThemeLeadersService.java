package com.alphascope.industry.service;

import com.alphascope.industry.dto.ThemeLeaderUpdateInput;
import com.alphascope.industry.vo.ThemeLeaderRevisionView;
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

  /**
   * 读取当前维护修订与复核提醒。
   *
   * @param code 已登记主题代码
   * @return 当前目录与修订
   */
  ThemeLeaderRevisionView revision(String code);

  /**
   * 管理员追加目录修订，原披露成分与历史报告不变。
   *
   * @param code 主题代码
   * @param input 预期修订及新目录
   * @return 新修订
   */
  ThemeLeaderRevisionView revise(String code, ThemeLeaderUpdateInput input);
}

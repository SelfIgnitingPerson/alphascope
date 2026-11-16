package com.alphascope.portfolio.service;

import com.alphascope.portfolio.dto.PositionDO;
import com.alphascope.portfolio.dto.PositionInput;
import com.alphascope.portfolio.dto.WatchlistDO;
import com.alphascope.portfolio.entity.PortfolioAccount;
import com.alphascope.portfolio.vo.PositionsView;
import com.alphascope.portfolio.vo.WatchlistView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 管理当前关注范围和人工资产；默认只研究自选，不评估个人持仓集中度。
 *
 * @author chenxuefeng
 */
public interface PortfolioService {

  /**
   * 读取是否启用个人持仓风险评估的配置。
   *
   * @return true 表示评估个人持仓；false 表示仅自选研究，不推算持仓风险
   */
  boolean positionRiskEnabled();

  /**
   * 读取供页面展示的自选研究列表，不暴露持久化主键。
   *
   * @return 按研究优先级排列的自选响应列表
   */
  List<WatchlistView> watchlistView();

  /**
   * 读取供页面展示的人工持仓、账户总额及人民币单位。
   *
   * @return 持仓列表与账户可用状态；未知总额保持缺失
   */
  PositionsView positionsView();

  /**
   * 读取内部研究编排使用的自选证券及启用状态。
   *
   * @return 当前自选记录列表；未登记时为空列表
   */
  List<WatchlistDO> watchlist();

  /**
   * 将已登记证券加入自选或恢复启用，并设置人工研究排序优先级。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param priority 人工研究排序优先级，不表示持仓比例或交易指令
   */
  void watch(String symbol, int priority);

  /**
   * 删除当前自选关系，不改写证券主数据或已冻结报告。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   */
  void unwatch(String symbol);

  /**
   * 读取当前人工登记持仓，不访问券商账户。
   *
   * @return 持仓记录列表；未登记时为空列表
   */
  List<PositionDO> positions();

  /**
   * 读取人工登记账户，不自动按持仓推算未知总资产。
   *
   * @return 账户记录；未登记时返回字段为空的对象
   */
  PortfolioAccount account();

  /**
   * 校验数量、成本及业务日期后保存人工持仓，不向券商下单。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param input 非负持仓股数、人民币每股成本及不晚于上海当前日期的业务日期
   * @throws IllegalArgumentException 数量或成本为负，或业务日期在未来
   */
  void position(String symbol, PositionInput input);

  /**
   * 校验后保存人工登记的本机账户资产总额及对应日期。
   *
   * @param assets 人民币资产总额；null 表示未知，非空值必须大于 0
   * @param date 人工账户总额对应的业务日期
   * @throws IllegalArgumentException 非空资产总额小于或等于 0
   */
  void account(BigDecimal assets, LocalDate date);
}

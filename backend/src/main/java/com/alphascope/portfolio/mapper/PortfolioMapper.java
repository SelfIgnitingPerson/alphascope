package com.alphascope.portfolio.mapper;

import com.alphascope.portfolio.dto.AccountInput;
import com.alphascope.portfolio.dto.PositionDO;
import com.alphascope.portfolio.dto.PositionWriteCommand;
import com.alphascope.portfolio.dto.WatchlistDO;
import com.alphascope.portfolio.entity.PortfolioAccount;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 当前自选、手工持仓及单账户资产总额的持久化入口。
 *
 * @author chenxuefeng
 */
@Mapper
public interface PortfolioMapper {

  /**
   * 读取当前自选研究范围，按研究优先级倒序、证券代码升序返回。
   *
   * @return 自选证券列表；未登记自选时返回空列表，是否启用由 enabled 字段表示
   */
  List<WatchlistDO> watchlist();

  /**
   * 新增自选证券或更新其研究优先级，并设置为启用状态。
   *
   * @param id 证券主键，关联 instrument.id
   * @param priority 人工研究排序优先级，不代表持仓比例或交易建议
   */
  void watch(@Param("id") long id, @Param("priority") int priority);

  /**
   * 按统一证券代码删除当前自选关系，不修改证券主数据和已冻结报告。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   */
  void unwatch(String symbol);

  /**
   * 读取当前手工登记持仓及证券名称，不连接券商账户。
   *
   * @return 按证券主键排序的持仓列表；未登记时返回空列表
   */
  List<PositionDO> positions();

  /**
   * 新增或更新某只证券的人工持仓数量、成本及业务日期。
   *
   * @param command 证券主键、非负股数、人民币每股成本及人工登记业务日期
   */
  void position(PositionWriteCommand command);

  /**
   * 读取固定本机账户的人工资产总额和对应业务日期。
   *
   * @return 账户记录；尚未登记时返回 null，总额未知时保留 null
   */
  PortfolioAccount account();

  /**
   * 新增或更新本机账户 id=1 的人工资产总额及业务日期。
   *
   * @param input 人工资产总额、日期及可选用款前提
   */
  void accountSave(AccountInput input);
}

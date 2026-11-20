package com.alphascope.market.mapper;

import com.alphascope.common.dto.InstrumentHistoryQuery;
import com.alphascope.market.dto.CalendarRevisionDO;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.dto.MarketBarQuery;
import com.alphascope.market.dto.MarketBarWriteCommand;
import com.alphascope.market.dto.MarketRevisionDO;
import com.alphascope.market.dto.MarketSourceDO;
import com.alphascope.market.dto.SuspensionQuery;
import com.alphascope.market.dto.SuspensionWriteCommand;
import com.alphascope.market.dto.TradingCalendarWriteCommand;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.entity.Instrument;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL 持久化入口；不包含跨模块业务编排。
 *
 * @author chenxuefeng
 */
@Mapper
public interface MarketMapper {
  /**
   * 读取按日期最近保存的真实日历行，保留来源和原记录时刻。
   *
   * @param query 只读日期范围
   * @return 含开闭市日的证据
   */
  List<CalendarRevisionDO> calendarEvidence(TradingDatesQuery query);

  /**
   * 按统一证券代码读取主数据业务字段和内部主键。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @return 证券主数据；不存在时返回 null
   */
  Instrument instrument(String symbol);

  /**
   * 按代码或名称模糊匹配主数据，按主键升序分页读取。
   *
   * @param q 证券代码或名称的匹配关键词；空字符串匹配全部主数据
   * @param size 本次分页返回条数，由调用方校验合法范围
   * @param offset 从 0 开始的分页偏移，由调用方校验为非负数
   * @return 当前页证券列表；无匹配结果时返回空列表
   */
  List<Instrument> instruments(
      @Param("q") String q, @Param("size") int size, @Param("offset") int offset);

  /**
   * 读取同一证券、日期窗口、来源和真实/演示模式的原价日线及复权因子。
   *
   * <p>因子按同证券、同日期、同来源和同模式关联；缺失因子保持 null，不跨来源补齐。
   *
   * @param query 统一证券代码、闭区间起止日期、实际来源及真实/演示模式
   * @return 按交易日升序排列的历史行情；无记录时返回空列表
   */
  List<MarketBarDO> bars(MarketBarQuery query);

  /**
   * 新增或更新同证券、同日、同来源和同模式的当前行情视图。
   *
   * <p>调用方将当前行情、因子、不可变修订及必要审计放在同一短事务内保存。
   *
   * @param row 原价 OHLC、股数、人民币成交额、实际来源、采集时间、批次和模式
   */
  void saveBar(MarketBarWriteCommand row);

  /**
   * 新增或更新与原价日线严格同源、同日期及同模式的复权因子。
   *
   * @param row 证券主键、交易日、正数复权因子、来源及真实/演示标志
   */
  void factor(MarketBarWriteCommand row);

  /**
   * 新增行情修订前后的审计证据，不修改已有审计记录。
   *
   * @param row 证券主键、交易日、批次及修改前后完整行情 JSON
   */
  void audit(MarketBarWriteCommand row);

  /**
   * 保存供应商明确返回的交易日历，已存在时更新该日期、交易所及模式的状态。
   *
   * <p>交易所存储为 SSE；未核验范围不得用星期规则代替开休市证据；官方年度规则生成的日历须记录实际来源指纹。
   *
   * @param command 交易日期、供应商开市标志、真实/演示模式及实际日历来源
   */
  void calendar(TradingCalendarWriteCommand command);

  /**
   * 读取指定模式下某日期最近保存的开休市状态。
   *
   * @param date 对应的业务日期，按上海时区解释
   * @param synthetic true 查询演示数据，false 查询真实数据，两种模式不得混用
   * @return true 为开市、false 为休市；缺少该日日历时返回 null
   */
  Boolean isOpen(@Param("date") LocalDate date, @Param("synthetic") boolean synthetic);

  /**
   * 读取指定日期区间和模式中最后一个已保存的开市日期。
   *
   * @param start 查询区间起始日期，包含当日
   * @param end 查询区间结束日期，包含当日
   * @param synthetic true 查询演示数据，false 查询真实数据，两种模式不得混用
   * @return 最近开市日期；区间内无开市记录时返回 null
   */
  LocalDate lastOpen(
      @Param("start") LocalDate start,
      @Param("end") LocalDate end,
      @Param("synthetic") boolean synthetic);

  /**
   * 读取指定模式下每只证券最近业务日实际保存的行情来源、批次和采集时刻。
   *
   * @param synthetic true 查询演示数据，false 查询真实数据，两种模式不得混用
   * @return 按证券代码排序的来源列表；没有行情时返回空列表
   */
  List<MarketSourceDO> latestSources(@Param("synthetic") boolean synthetic);

  /**
   * 读取证券在业务日期之前或当日最近保存的实际行情来源。
   *
   * @param id 证券主键，关联 instrument.id
   * @param synthetic true 查询演示数据，false 查询真实数据，两种模式不得混用
   * @param date 对应的业务日期，按上海时区解释
   * @return 该模式下的最近来源；没有历史记录时返回 null
   */
  String latestSourceAt(
      @Param("id") long id, @Param("synthetic") boolean synthetic, @Param("date") LocalDate date);

  /**
   * 读取指定模式和闭区间内已经保存的开市日期，按日期升序返回。
   *
   * @param start 查询区间起始日期，包含当日
   * @param end 查询区间结束日期，包含当日
   * @param synthetic true 查询演示数据，false 查询真实数据，两种模式不得混用
   * @return 去重后的开市日期列表；无记录时返回空列表
   */
  List<LocalDate> openDates(
      @Param("start") LocalDate start,
      @Param("end") LocalDate end,
      @Param("synthetic") boolean synthetic);

  /**
   * 统计指定模式和闭区间内已有日历记录的不同日期数，包含开市与休市。
   *
   * @param start 查询区间起始日期，包含当日
   * @param end 查询区间结束日期，包含当日
   * @param synthetic true 查询演示数据，false 查询真实数据，两种模式不得混用
   * @return 已经保存日历证据的日期数；不能将缺失日期视为休市
   */
  long calendarDays(
      @Param("start") LocalDate start,
      @Param("end") LocalDate end,
      @Param("synthetic") boolean synthetic);

  /**
   * 读取证券主数据中已经登记的上市日期。
   *
   * @param id 证券主键，关联 instrument.id
   * @return 已知上市日期；证券不存在或日期未登记时返回 null
   */
  LocalDate listedAt(long id);

  /**
   * 读取指定证券、来源、模式和日期窗口中有明确停牌证据的日期。
   *
   * @param query 证券主键、实际来源、真实/演示模式和待核对日期窗口
   * @return 已有 SUSPENDED 证据的日期列表；无证据时返回空列表
   */
  List<LocalDate> suspensions(SuspensionQuery query);

  /**
   * 保存明确证实的停牌状态及采集批次，已有记录时更新该证据。
   *
   * <p>只有供应商明确确认停牌后才能调用；未知行情缺口不能作为停牌写入。
   *
   * @param command 证券主键、交易日、实际来源、真实/演示模式和采集批次
   */
  void suspension(SuspensionWriteCommand command);

  /**
   * 新增一条带内容指纹和原采集时刻的不可变行情修订。
   *
   * @param row 证券、交易日、来源、模式、批次、内容指纹及完整新行情 JSON
   */
  void revision(MarketBarWriteCommand row);

  /**
   * 按与证券列表相同的代码或名称条件统计匹配总数。
   *
   * @param q 证券代码或名称的匹配关键词；空字符串匹配全部主数据
   * @return 符合条件的证券数量
   */
  long instrumentCount(String q);

  /**
   * 确认某日停牌后，删除该证券、来源及模式下与停牌证据冲突的当前日线。
   *
   * <p>调用方在同一事务内同步清理对应因子并保存停牌证据；不可变修订和既有报告不在此删除。
   *
   * @param command 已确认停牌的证券主键、交易日期、实际来源和模式
   */
  void removeSuspendedBar(SuspensionWriteCommand command);

  /**
   * 确认某日停牌后，删除该证券、日期、来源及模式下的当前复权因子。
   *
   * <p>与冲突日线清理及停牌证据写入处于同一事务。
   *
   * @param command 已确认停牌的证券主键、交易日期、实际来源和模式
   */
  void removeSuspendedFactor(SuspensionWriteCommand command);

  /**
   * 取得该日有效交易行情后，删除同证券、同来源及同模式的旧停牌标记。
   *
   * <p>与当前行情和复权因子的保存处于同一事务。
   *
   * @param command 已取得有效行情的证券主键、日期、实际来源及模式
   */
  void clearSuspension(SuspensionWriteCommand command);

  /**
   * 查找在各交易日结束前已入库的真实修订来源。
   *
   * @param query 标的、日期闭区间和模式
   * @return 按最近可用交易日排序的来源，不混合价格窗口
   */
  List<String> replaySources(InstrumentHistoryQuery query);

  /**
   * 按每个交易日结束时点选择最后一份不可变原价与因子修订。
   *
   * @param query 已选定来源及日期区间
   * @return 严格已知入库时刻的修订，旧的未知时刻不补造
   */
  List<MarketRevisionDO> replayBars(MarketBarQuery query);

  /**
   * 读取各日结束前已保存的完整权威日历行。
   *
   * @param query 要求覆盖的日期闭区间
   * @return 已知版本行，缺失日期由调用方拒绝
   */
  List<CalendarRevisionDO> replayCalendar(TradingDatesQuery query);
}

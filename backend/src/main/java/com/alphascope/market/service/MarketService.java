package com.alphascope.market.service;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.exception.NotFoundException;
import com.alphascope.common.response.PageResponse;
import com.alphascope.market.dto.InstrumentSearchRequest;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.dto.MarketCacheSettings;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.entity.Instrument;
import com.alphascope.market.vo.BaoStockPolicyView;
import com.alphascope.market.vo.InstrumentView;
import com.alphascope.market.vo.MarketAcquisition;
import com.alphascope.market.vo.ProviderStatusView;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 行情查询与获取编排：校验同源、模式和完整交易日，区分新采集、有效缓存与已存历史。
 *
 * @author chenxuefeng
 */
public interface MarketService {
  /**
   * 只读免费节点本机请求预算及持久保护，不产生供应商请求。
   *
   * @return 状态损坏时明确返回异常与空计数，不冒充零请求
   */
  BaoStockPolicyView baoStockPolicy();

  /**
   * 返回当前运行配置是否为演示模式。
   *
   * @return true 为演示，false 为真实行情，不能因真实通道故障改为演示
   */
  boolean synthetic();

  /**
   * 返回当前配置的数据源范围，用于持久任务互斥及输入指纹。
   *
   * @return DEMO 或 REAL:通道列表等配置标识
   */
  String dataScope();

  /**
   * 只读返回配置通道状态及已保存行情的实际来源，不探测网络。
   *
   * @return 通道优先级、日历来源及各证券最近入库来源信息
   */
  ProviderStatusView providerStatus();

  /**
   * 返回当前供应商或组合通道的稳定代码。
   *
   * @return 当前配置代码，例如 MULTI 或 DEMO，不一定是单条行情的实际来源
   */
  String provider();

  /**
   * 返回影响任务幂等复用的缓存策略参数。
   *
   * @return 缓存分钟数及历史复用策略标识的类型化配置
   */
  MarketCacheSettings cachePolicy();

  /**
   * 只读查询证券该业务日已保存的行情获取方式和原始采集时间。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param date 研究或记录对应的业务日期，上海时区
   * @return 已保存获取证据；尚无记录时返回 null
   */
  MarketAcquisition acquisition(String symbol, LocalDate date);

  /**
   * 校验分页范围后读取证券公开业务字段。
   *
   * @param request 证券关键词和分页条件
   * @return 证券当前页、匹配总数、页码及每页条数；条目不包含内部主键
   */
  PageResponse<InstrumentView> instruments(InstrumentSearchRequest request);

  /**
   * 按统一证券代码查询内部主数据，用于后续证据关联。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @return 已登记证券记录
   * @throws NotFoundException 证券代码尚未登记
   */
  Instrument instrument(String symbol);

  /**
   * 只读查询截止业务日期已保存的单一来源原价及同源因子，不读取结束日之后的行情。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param request 必填的开始、结束日期闭区间
   * @return 按交易日升序的同源历史；没有匹配当前配置的来源时为空列表
   * @throws IllegalArgumentException 起始日期晚于结束日期或窗口超过十年
   */
  List<MarketBarDO> bars(String symbol, DateRangeRequest request);

  /**
   * 加载最多十年研究区间及固定四个月指标预热，整段按结束日选择同一家来源。
   *
   * @param symbol 证券统一代码
   * @param request 研究区间，不包含额外预热日期
   * @return 同源且不晚于结束日的真实原价与因子；不做分段跨源拼接
   */
  List<MarketBarDO> researchBars(String symbol, DateRangeRequest request);

  /**
   * 按当前真实/演示模式查询已保存日历，不按星期推断开休市。
   *
   * @param date 研究或记录对应的业务日期，上海时区
   * @return true 为已确认开市，false 为已确认休市
   * @throws IllegalStateException 该业务日交易日历缺失
   */
  boolean isOpen(LocalDate date);

  /**
   * 读取指定日期闭区间内最后一个已保存开市日。
   *
   * @param start 请求区间起始日期，包含当日
   * @param end 请求区间结束日期，包含当日
   * @return 最近开市日期；没有开市记录时返回 null
   */
  LocalDate lastOpen(LocalDate start, LocalDate end);

  /**
   * 复用已结束日期的完整日历，或从配置通道采集并保存明确的开休市证据。
   *
   * @param start 请求区间起始日期，包含当日
   * @param end 请求区间结束日期，包含当日
   */
  void syncCalendar(LocalDate start, LocalDate end);

  /**
   * 依据完整日历及已知上市日期构造要求覆盖的交易日集合，仅排除指定来源证实的停牌。
   *
   * @param query 证券、日期闭区间及与原价一致的停牌证据来源
   * @return 应有交易日的有序集合；窗口位于上市前时为空集合
   * @throws IllegalStateException 请求区间交易日历不完整
   */
  Set<LocalDate> expectedDates(TradingDatesQuery query);

  /**
   * 依据已知上市日、已确认停牌及已存日线区分单日行情状态。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param date 研究或记录对应的业务日期，上海时区
   * @return NOT_LISTED、SUSPENDED、TRADING 或 UNKNOWN；未知缺口不能视为休市
   */
  String dayStatus(String symbol, LocalDate date);

  /**
   * 仅依据该业务时点所选行情来源已保存的明确证据判断停牌。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param date 研究或记录对应的业务日期，上海时区
   * @return true 为存在同源同模式停牌证据；无证据时为 false
   */
  boolean suspended(String symbol, LocalDate date);

  /**
   * 复用完整有效缓存或已验证真实历史，否则采集整窗同源行情并校验后写入。
   *
   * <p>网络请求位于写事务外；失败不回退模拟，不混合来源或用零值填补未知缺口。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param start 请求区间起始日期，包含当日
   * @param end 请求区间结束日期，包含当日
   * @return 本次复用或成功保存的日线条数；窗口完全位于上市前时为 0
   * @throws IllegalStateException 上游不可用、历史缺口未知或行情无法通过完整性校验
   */
  int sync(String symbol, LocalDate start, LocalDate end);

  /**
   * 重建每个交易日结束前已实际保存的原价和同源因子，不借用当前行。
   *
   * @param symbol 统一证券代码
   * @param request 日期闭区间
   * @return 一家允许来源的不可变修订；无已知证据时为空
   */
  List<MarketBarDO> replayBars(String symbol, DateRangeRequest request);

  /**
   * 读取可证明在各日结束前已经保存的权威开市日集合。
   *
   * @param request 必须完整覆盖的日期区间
   * @return 已知时点开市日期
   * @throws IllegalStateException 日历存在缺口或未知保存时刻
   */
  Set<LocalDate> replayCalendar(DateRangeRequest request);

  /**
   * 读取连续区间每个自然日的最新真实日历证据及来源，不联网。
   *
   * @param request 明确闭区间
   * @return 含休市日及来源的真实证据，缺口抛错
   */
  java.util.List<com.alphascope.market.dto.CalendarRevisionDO> calendarEvidence(
      DateRangeRequest request);
}

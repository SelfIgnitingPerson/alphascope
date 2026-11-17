package com.alphascope.market.provider;

import com.alphascope.market.dto.DailyBarDto;
import com.alphascope.market.dto.MarketWindow;
import com.alphascope.market.dto.ProviderProbeRequest;
import com.alphascope.market.vo.ProviderChannelView;
import com.alphascope.market.vo.ProviderProbeSample;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 行情供应商统一契约：整窗同源原价与因子、明确日历及证实的停牌，禁止未知缺口补零。
 *
 * @author chenxuefeng
 */
public interface MarketDataProvider extends AutoCloseable {
  /**
   * 声明基础行情能力，权限和运行状态另行展示。
   *
   * @return 日历、股票日线及实现支持的指数能力
   */
  default Set<MarketCapability> capabilities() {
    return supportsIndexBars()
        ? Set.of(
            MarketCapability.TRADING_CALENDAR,
            MarketCapability.STOCK_DAILY,
            MarketCapability.INDEX_DAILY)
        : Set.of(MarketCapability.TRADING_CALENDAR, MarketCapability.STOCK_DAILY);
  }

  /**
   * 显式核验当前指定通道的一日基础样本，不能自动切换其他来源。
   *
   * @param request 已配置通道、基础能力和精确日期
   * @return 通过数据契约核验的摘要，不写行情库
   */
  default ProviderProbeSample probe(ProviderProbeRequest request) {
    if (!code().equals(request.getChannel())) throw new IllegalArgumentException("通道未配置");
    return ProviderProbeSamples.fetch(this, request);
  }

  /**
   * 释放当前供应商持有的连接或会话资源。
   *
   * <p>默认实现无资源需要释放；持有资源的实现负责重写此方法，通常由 try-with-resources 调用。
   */
  @Override
  default void close() {}

  /**
   * 返回供应商或组合通道的稳定代码。
   *
   * @return 通道代码，例如 BAOSTOCK、SINA、TUSHARE、DEMO 或 MULTI
   */
  String code();

  /**
   * 取得指定证券日期窗口的同源原价、复权因子和有明确来源的停牌证据。
   *
   * <p>默认实现依据 index 委托相应日线方法，缺席原因字典为空；真实通道失败不能切换为演示数据。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param from 请求起始日期，包含当日
   * @param to 请求结束日期，包含当日
   * @param index true 请求指数行情，false 请求股票行情
   * @return 包含实际行情及已证实缺席原因的窗口，不用 0 填充未知缺口
   * @throws UnsupportedOperationException 请求了当前实现不支持的指数或其他行情能力
   */
  default MarketWindow fetchWindow(String symbol, LocalDate from, LocalDate to, boolean index) {
    return new MarketWindow(
        index ? fetchIndexBars(symbol, from, to) : fetchDailyBars(symbol, from, to), Map.of());
  }

  /**
   * 说明当前通道是否支持项目要求的指数原价及相关口径。
   *
   * @return true 表示具备该能力，false 表示应跳过指数请求；不表示当前网络可用
   */
  default boolean supportsIndexBars() {
    return true;
  }

  /**
   * 说明该通道提供的是否为演示数据，作为真实/演示隔离依据。
   *
   * @return 只有明确演示通道返回 true；真实通道和真实多通道组合返回 false
   */
  default boolean synthetic() {
    return false;
  }

  /**
   * 判断已保存来源标识是否属于当前通道允许读取的数据范围。
   *
   * @param source 已保存行情的实际供应商来源标识；null 表示尚无来源证据
   * @return true 为允许复用的来源，false 为来源不匹配或为空
   */
  default boolean acceptsSource(String source) {
    return source != null && (source.equals(code()) || source.startsWith(code() + "_"));
  }

  /**
   * 返回当前运行配置的数据源范围，用于任务互斥和输入指纹。
   *
   * @return DEMO 或 REAL:通道列表等配置范围，不等同于某条行情的实际来源
   */
  default String scope() {
    return synthetic() ? "DEMO" : "REAL:" + code();
  }

  /**
   * 返回当前日历来源标识；多通道实现使用本进程最近一次成功取得日历的实际通道。
   *
   * @return 默认是自身通道代码；组合实现尚未成功获取日历时可为 NONE
   */
  default String calendarSource() {
    return code();
  }

  /**
   * 只读返回当前通道观测状态，不主动探测网络、收费接口或重新采集行情。
   *
   * @return 按配置优先级排列的通道状态列表，未观测的状态保持 NOT_CHECKED
   */
  default List<ProviderChannelView> status() {
    return List.of(
        new ProviderChannelView(
            code(), supportsIndexBars(), "NOT_CHECKED", 0, null, null, null, synthetic()));
  }

  /**
   * 取得指定股票日期窗口的实际日线，原价、股数、人民币成交额和因子保持统一口径。
   *
   * <p>停牌、未知缺口和通道故障由窗口与供应商证据分别处理；不能用演示行情替代真实请求。
   *
   * @param symbol 股票统一代码，例如 300476.SZ
   * @param from 请求起始日期，包含当日
   * @param to 请求结束日期，包含当日
   * @return 该窗口实际存在的股票日线，保留实际来源和采集时间
   */
  List<DailyBarDto> fetchDailyBars(String symbol, LocalDate from, LocalDate to);

  /**
   * 取得指定指数日期窗口的实际日线；默认委托普通日线接口，由具体通道覆盖能力差异。
   *
   * @param symbol 指数统一代码，例如 000300.SH
   * @param from 请求起始日期，包含当日
   * @param to 请求结束日期，包含当日
   * @return 具有当前实现支持口径的指数日线，保留实际来源和采集时间
   * @throws UnsupportedOperationException 当前实现不支持项目要求的指数日线口径
   */
  default List<DailyBarDto> fetchIndexBars(String symbol, LocalDate from, LocalDate to) {
    return fetchDailyBars(symbol, from, to);
  }

  /**
   * 取得请求日期区间内供应商明确提供的开休市状态。
   *
   * <p>未核验的年度或区间不能按星期推算；本地日历必须具有交易所规则、完整年度通知及来源指纹。网络失败不能视为已确认休市。
   *
   * @param from 请求起始日期，包含当日
   * @param to 请求结束日期，包含当日
   * @return 日期到开市标志的映射；true 为开市、false 为休市，未返回的日期仍属未知
   */
  Map<LocalDate, Boolean> fetchCalendar(LocalDate from, LocalDate to);
}

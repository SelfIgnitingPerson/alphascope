package com.alphascope.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 按交易日导出的完整市场证据，记录名单、停牌、价格及对应核验资料。
 *
 * @author chenxuefeng
 * @param date 市场交易日
 * @param source 供应商代码
 * @param exportedAt 实际导出日期和时刻
 * @param expectedCounts 从逐日独立证据核对的各交易所名册总数
 * @param rosterSources 各交易所当日名册或同口径统计的资料链接
 * @param members 当日名册及明确停牌状态
 * @param quotes 同源未复权价格与后复权因子
 * @param comparison 沪深和北交所分别使用公开数据重建时的独立核验结果
 */
public record HistoricalFullMarketArchive(
    LocalDate date,
    String source,
    OffsetDateTime exportedAt,
    Map<String, Integer> expectedCounts,
    Map<String, String> rosterSources,
    List<Member> members,
    List<Quote> quotes,
    Comparison comparison) {
  public HistoricalFullMarketArchive(
      LocalDate date,
      String source,
      OffsetDateTime exportedAt,
      Map<String, Integer> expectedCounts,
      Map<String, String> rosterSources,
      List<Member> members,
      List<Quote> quotes) {
    this(date, source, exportedAt, expectedCounts, rosterSources, members, quotes, null);
  }

  /**
   * 对照独立日报的沪深与北交所证券总数，保留真实来源和文件指纹。
   *
   * @param date 独立统计的业务日期
   * @param shSzCount 独立来源原始沪深在市数
   * @param bseCount 北交所 A 股在市数
   * @param sourceUrl 独立统计资料链接
   * @param sha256 独立资料指纹
   * @param referenceSnapshotId 以本机完整日终档案核对时使用的档案主键
   * @param excludedInstruments 纳入原统计但不属于 A 股的存托凭证及其公开依据
   * @author chenxuefeng
   */
  public record Comparison(
      LocalDate date,
      int shSzCount,
      int bseCount,
      String sourceUrl,
      String sha256,
      Long referenceSnapshotId,
      Map<String, String> excludedInstruments) {}

  /**
   * 当日成员和全日停牌标记。
   *
   * @author chenxuefeng
   * @param symbol 带交易所后缀的六位代码
   * @param suspended 明确的全日停牌状态，空值不可导入
   * @param suspension 全日停牌时段及来源，按北京时间填写
   */
  public record Member(String symbol, Boolean suspended, Suspension suspension) {}

  /**
   * 覆盖交易日开盘至收盘的明确停牌证据。
   *
   * @param startAt 明确生效时间
   * @param endAt 明确恢复时间
   * @param sourceUrl 交易所公告或停复牌记录
   * @author chenxuefeng
   */
  public record Suspension(LocalDateTime startAt, LocalDateTime endAt, String sourceUrl) {}

  /**
   * 当日成交价格与同源因子，已确认停牌的证券允许无价格行。
   *
   * @author chenxuefeng
   * @param symbol 带交易所后缀的六位代码
   * @param close 未复权收盘价
   * @param previousClose 供应商日涨跌口径前收价
   * @param amount 成交额，单位元
   * @param factor 同一来源的累计后复权因子
   */
  public record Quote(
      String symbol,
      BigDecimal close,
      BigDecimal previousClose,
      BigDecimal amount,
      BigDecimal factor) {}
}

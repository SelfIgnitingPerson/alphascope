package com.alphascope.market.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 沪深市场截面的明确字段协议；缺失数值保持 null。
 *
 * @author chenxuefeng
 */
@Data
public class MarketBreadthView {
  /** 截面所属业务日期。 */
  private LocalDate businessDate;

  /** 供应商证券清单覆盖范围，SH_SZ_A 不含北交所。 */
  private String universeScope;

  /** 截面实际数据来源。 */
  private String source;

  /** COMPLETE、PARTIAL 或当前采集状态。 */
  private String status;

  /** 当日清单中的预期股票数。 */
  private Integer expected;

  /** 具有有效交易行情的股票数。 */
  private Integer observed;

  /** 有证据确认的停牌家数。 */
  private Integer suspended;

  /** 上涨家数。 */
  private Integer advancers;

  /** 下跌家数。 */
  private Integer decliners;

  /** 平盘家数。 */
  private Integer unchanged;

  /** 同日成交总金额，人民币元。 */
  private BigDecimal amount;

  /** 真实覆盖比例，0 至 1。 */
  private Double coverage;

  /** 缺失行情数量。 */
  private Integer missingCount;

  /** 最多列出 30 个缺失标的代码。 */
  private List<String> missingSymbols;

  /** 数据范围与计算边界。 */
  private List<String> limitations;

  /** 不可变原始快照主键。 */
  private Long snapshotId;

  /** 原始采集时间。 */
  private LocalDateTime fetchedAt;

  /** 无可用数据时的明确说明。 */
  private String reason;
}

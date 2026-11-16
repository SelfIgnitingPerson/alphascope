package com.alphascope.intelligence.dto;

import com.alphascope.fundamental.entity.FinancialReport;
import com.alphascope.indicator.entity.IndicatorSnapshot;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.signal.entity.SignalEvent;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * 报告冻结实际采用的行情、因子、指标、规则与财报，后续修订不改变本次证据。
 *
 * @author chenxuefeng
 */
@Builder
public record StockEvidence(
    /** 标的代码，使用六位数字及交易所后缀，例如300476.SZ。 */
    String symbol,

    /** 标的中文名称，用于页面展示。 */
    String name,

    /** 按交易日期升序保存的同源原价与复权因子证据，不读取未来业务日期。 */
    List<MarketBarDO> history,

    /** 与冻结行情对应的技术指标记录。 */
    List<IndicatorSnapshot> indicators,

    /** 该业务日期已保存的规则评估及证据列表。 */
    List<SignalEvent> signals,

    /** 截至报告业务时点可用的已披露累计财报证据。 */
    List<FinancialReport> financials,

    /** 该标的是否有供应商证实的当日停牌证据。 */
    boolean suspended,

    /** 近两个自然月按上市日期和同源停牌核对的应有交易日；空列表表示日历不足。 */
    List<LocalDate> expectedTradingDates) {}

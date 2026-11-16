package com.alphascope.indicator.service.impl;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.dto.InstrumentHistoryQuery;
import com.alphascope.common.util.Jsons;
import com.alphascope.common.validation.DateRangeValidator;
import com.alphascope.indicator.calculator.IndicatorCalculator;
import com.alphascope.indicator.calculator.TechnicalEvidenceCalculator;
import com.alphascope.indicator.dto.TechnicalWindow;
import com.alphascope.indicator.entity.IndicatorSnapshot;
import com.alphascope.indicator.mapper.IndicatorMapper;
import com.alphascope.indicator.service.IndicatorService;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.dto.TradingDatesQuery;
import com.alphascope.market.service.MarketService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * IndicatorService 的业务实现。
 *
 * <p>批量加载同源历史行情与基准，采用统一复权锚点计算各交易日指标。
 *
 * @author chenxuefeng
 */
@Service
public class IndicatorServiceImpl implements IndicatorService {
  private final IndicatorMapper mapper;
  private final MarketService market;

  public IndicatorServiceImpl(IndicatorMapper mapper, MarketService market) {
    this.mapper = mapper;
    this.market = market;
  }

  /** {@inheritDoc} */
  @Override
  public List<IndicatorSnapshot> list(String symbol, DateRangeRequest request) {
    LocalDate start = request.getStart();
    LocalDate end = request.getEnd();
    DateRangeValidator.requireValid(start, end);

    return mapper.list(
        InstrumentHistoryQuery.builder()
            .symbol(symbol)
            .startDate(start)
            .endDate(end)
            .synthetic(market.synthetic())
            .build());
  }

  private double number(Number value) {
    if (value == null) {
      throw new IllegalStateException("复权或行情缺失");
    }
    return value.doubleValue();
  }

  private BigDecimal nullable(OptionalDouble value) {
    return value.isPresent() ? BigDecimal.valueOf(value.getAsDouble()) : null;
  }

  /** {@inheritDoc} */
  @Override
  public void calculate(String symbol, LocalDate date) {
    var bars =
        market.bars(symbol, DateRangeRequest.builder().start(date.minusYears(2)).end(date).build());
    var benchmark =
        market.bars(
            "000300.SH", DateRangeRequest.builder().start(date.minusYears(2)).end(date).build());
    calculateLoaded(symbol, date, bars, benchmark, expected(symbol, bars, date));
  }

  /** {@inheritDoc} */
  @Override
  public void calculateRange(String symbol, LocalDate start, LocalDate end) {
    var all =
        market.bars(symbol, DateRangeRequest.builder().start(start.minusYears(2)).end(end).build());
    var benchmark =
        market.bars(
            "000300.SH", DateRangeRequest.builder().start(start.minusYears(2)).end(end).build());
    var expected = expected(symbol, all, end);
    for (var day : all) {
      LocalDate date = day.getTradeDate();
      if (date.isBefore(start)) {
        continue;
      }
      var window =
          all.stream()
              .filter(
                  b -> {
                    LocalDate d = b.getTradeDate();
                    return !d.isAfter(date) && !d.isBefore(date.minusYears(2));
                  })
              .toList();
      calculateLoaded(symbol, date, window, benchmark, expected);
    }
  }

  private BigDecimal decimal(Double value, int scale) {
    return value == null ? null : TechnicalEvidenceCalculator.rounded(value, scale);
  }

  private Set<LocalDate> expected(String symbol, List<MarketBarDO> bars, LocalDate end) {
    if (bars.isEmpty()) return Set.of();
    try {
      return market.expectedDates(
          TradingDatesQuery.builder()
              .symbol(symbol)
              .start(bars.getFirst().getTradeDate())
              .end(end)
              .source(bars.getLast().getSource())
              .build());
    } catch (IllegalStateException e) {
      return Set.of();
    }
  }

  private void calculateLoaded(
      String symbol,
      LocalDate date,
      List<MarketBarDO> bars,
      List<MarketBarDO> benchmark,
      Set<LocalDate> expected) {
    if (bars.isEmpty() || !date.equals(bars.getLast().getTradeDate())) {
      throw new IllegalStateException("业务日期行情尚未发布");
    }
    double anchor = number(bars.get(bars.size() - 1).getFactor());
    List<Double> closes = new ArrayList<>(),
        highs = new ArrayList<>(),
        lows = new ArrayList<>(),
        volumes = new ArrayList<>();
    boolean synthetic = false;
    for (var bar : bars) {
      double factor = number(bar.getFactor()) / anchor;
      closes.add(number(bar.getClose()) * factor);
      highs.add(number(bar.getHigh()) * factor);
      lows.add(number(bar.getLow()) * factor);
      volumes.add(number(bar.getVolume()));
      synthetic |= Boolean.TRUE.equals(bar.getSynthetic());
    }
    IndicatorSnapshot row = new IndicatorSnapshot();
    row.setInstrumentId(market.instrument(symbol).getId());
    row.setTradeDate(date);
    row.setAlgorithmVersion("ADJ_V2");
    var evidence =
        TechnicalEvidenceCalculator.calculate(new TechnicalWindow(date, bars, benchmark, expected));
    row.setMa5(nullable(IndicatorCalculator.ma(closes, 5)));
    row.setMa10(nullable(IndicatorCalculator.ma(closes, 10)));
    row.setMa20(decimal(evidence.get("ma20"), 4));
    row.setMa60(decimal(evidence.get("ma60"), 4));
    row.setAtr14(nullable(IndicatorCalculator.atr(highs, lows, closes, 14)));
    row.setVolRatio(decimal(evidence.get("volumeRatio"), 8));

    row.setRs20(decimal(evidence.get("rs20"), 8));
    row.setDataStatus(
        row.getMa60() == null || row.getRs20() == null ? "INSUFFICIENT_HISTORY" : "COMPLETE");
    row.setSynthetic(synthetic);
    row.setInputHash(Jsons.hash(Jsons.write(bars)));
    row.setSourceBatch(bars.getLast().getBatchId());
    mapper.save(row);
  }
}

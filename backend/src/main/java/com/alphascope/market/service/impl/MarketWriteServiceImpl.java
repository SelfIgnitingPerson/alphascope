package com.alphascope.market.service.impl;

import com.alphascope.common.util.Jsons;
import com.alphascope.common.util.RevisionJson;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.dto.MarketBarQuery;
import com.alphascope.market.dto.MarketBarWriteCommand;
import com.alphascope.market.dto.MarketWindow;
import com.alphascope.market.dto.SuspensionWriteCommand;
import com.alphascope.market.mapper.MarketMapper;
import com.alphascope.market.service.MarketWriteService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MarketWriteService 的业务实现。
 *
 * <p>当前视图、因子、修订和停牌证据在同一个短事务中提交。
 *
 * @author chenxuefeng
 */
@Service
public class MarketWriteServiceImpl implements MarketWriteService {
  private final MarketMapper mapper;

  public MarketWriteServiceImpl(MarketMapper mapper) {
    this.mapper = mapper;
  }

  private boolean same(BigDecimal previous, BigDecimal current) {
    return previous != null && previous.compareTo(current) == 0;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public int persist(long instrumentId, MarketWindow window) {
    String batch = UUID.randomUUID().toString();
    var first = window.bars().getFirst();
    var oldRows =
        mapper.bars(
            MarketBarQuery.builder()
                .symbol(first.symbol())
                .startDate(window.bars().getFirst().tradeDate())
                .endDate(window.bars().getLast().tradeDate())
                .synthetic(first.isSynthetic())
                .source(first.provider())
                .build());
    Map<LocalDate, MarketBarDO> previous = new HashMap<>();
    oldRows.forEach(r -> previous.put(r.getTradeDate(), r));
    for (var bar : window.bars()) {
      MarketBarWriteCommand row = new MarketBarWriteCommand();
      row.setInstrumentId(instrumentId);
      row.setTradeDate(bar.tradeDate());
      row.setOpen(bar.open());
      row.setHigh(bar.high());
      row.setLow(bar.low());
      row.setClose(bar.close());
      row.setVolume(bar.volume());
      row.setAmount(bar.amount());
      row.setFactor(bar.adjustmentFactor());
      row.setSource(bar.provider());
      row.setFetchedAt(bar.fetchedAt());
      row.setBatchId(batch);
      row.setSynthetic(bar.isSynthetic());
      var old = previous.get(bar.tradeDate());
      boolean changed = old == null;
      if (old != null) {
        changed =
            !same(old.getOpen(), row.getOpen())
                || !same(old.getHigh(), row.getHigh())
                || !same(old.getLow(), row.getLow())
                || !same(old.getClose(), row.getClose())
                || !same(old.getVolume(), row.getVolume())
                || !same(old.getAmount(), row.getAmount())
                || !same(old.getFactor(), row.getFactor());
      }
      row.setNewValue(Jsons.write(row));
      if (changed) {
        row.setContentHash(
            Jsons.hash(
                Jsons.write(
                    List.of(
                        bar.open(),
                        bar.high(),
                        bar.low(),
                        bar.close(),
                        bar.volume(),
                        bar.amount(),
                        bar.adjustmentFactor()))));
        String currentValue = row.getNewValue();
        row.setNewValue(RevisionJson.encode(currentValue));
        mapper.revision(row);
        row.setNewValue(currentValue);
        if (old != null) {
          row.setOldValue(Jsons.write(old));
          mapper.audit(row);
        }
      }
      if (!changed) {
        row.setBatchId(old.getBatchId());
      }
      mapper.saveBar(row);
      mapper.clearSuspension(
          SuspensionWriteCommand.builder()
              .instrumentId(instrumentId)
              .tradeDate(bar.tradeDate())
              .source(bar.provider())
              .synthetic(bar.isSynthetic())
              .build());
      mapper.factor(row);
    }
    for (var day : window.absences().keySet()) {
      SuspensionWriteCommand command =
          SuspensionWriteCommand.builder()
              .instrumentId(instrumentId)
              .tradeDate(day)
              .source(first.provider())
              .synthetic(first.isSynthetic())
              .build();
      mapper.removeSuspendedBar(command);
      mapper.removeSuspendedFactor(command);
    }
    for (var day : window.absences().keySet()) {
      mapper.suspension(
          SuspensionWriteCommand.builder()
              .instrumentId(instrumentId)
              .tradeDate(day)
              .source(first.provider())
              .synthetic(first.isSynthetic())
              .batchId(batch)
              .build());
    }
    return window.bars().size();
  }
}

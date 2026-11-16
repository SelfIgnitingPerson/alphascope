package com.alphascope.market.service.impl;

import com.alphascope.market.dto.InstrumentInput;
import com.alphascope.market.entity.Instrument;
import com.alphascope.market.mapper.InstrumentAdminMapper;
import com.alphascope.market.service.InstrumentAdminService;
import org.springframework.stereotype.Service;

/**
 * InstrumentAdminService 的业务实现。
 *
 * <p>证券主数据写入的业务校验入口，不承担行情采集。
 *
 * @author chenxuefeng
 */
@Service
public class InstrumentAdminServiceImpl implements InstrumentAdminService {
  private final InstrumentAdminMapper mapper;

  public InstrumentAdminServiceImpl(InstrumentAdminMapper mapper) {
    this.mapper = mapper;
  }

  /** {@inheritDoc} */
  @Override
  public void create(InstrumentInput input) {
    if (input.symbol() == null
        || input.exchange() == null
        || !input.symbol().endsWith("." + input.exchange())) {
      throw new IllegalArgumentException("证券代码后缀必须与交易所一致");
    }
    Instrument row = new Instrument();
    row.setSymbol(input.symbol());
    row.setName(input.name());
    row.setExchange(input.exchange());
    row.setType(input.type());
    row.setListedAt(input.listedAt());
    mapper.create(row);
  }
}

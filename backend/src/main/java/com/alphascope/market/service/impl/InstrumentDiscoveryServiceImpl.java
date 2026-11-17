package com.alphascope.market.service.impl;

import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.exception.NotFoundException;
import com.alphascope.common.response.ResultCode;
import com.alphascope.market.dto.InstrumentSearchRequest;
import com.alphascope.market.entity.Instrument;
import com.alphascope.market.mapper.InstrumentAdminMapper;
import com.alphascope.market.provider.InstrumentDirectoryProvider;
import com.alphascope.market.service.InstrumentDiscoveryService;
import com.alphascope.market.service.MarketService;
import com.alphascope.market.vo.InstrumentCandidate;
import com.alphascope.market.vo.InstrumentDiscoveryView;
import java.util.LinkedHashMap;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 新证券仅从目录验证结果写入；已有登记即使远端暂不可用仍可加入自选。
 *
 * @author chenxuefeng
 */
@Service
public class InstrumentDiscoveryServiceImpl implements InstrumentDiscoveryService {
  private final MarketService market;
  private final InstrumentAdminMapper mapper;
  private final InstrumentDirectoryProvider directory;

  public InstrumentDiscoveryServiceImpl(
      MarketService market, InstrumentAdminMapper mapper, InstrumentDirectoryProvider directory) {
    this.market = market;
    this.mapper = mapper;
    this.directory = directory;
  }

  /** {@inheritDoc} */
  @Override
  public InstrumentDiscoveryView search(String keyword) {
    String q = keyword == null ? "" : keyword.trim();
    if (q.length() < 2 || q.length() > 40)
      throw new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "请输入2至40字符的股票代码或名称");
    var values = new LinkedHashMap<String, InstrumentCandidate>();
    var request = new InstrumentSearchRequest();
    request.setQ(q);
    request.setSize(20);
    for (var item : market.instruments(request).items())
      if ("STOCK".equals(item.type()) && item.active())
        values.put(
            item.symbol(),
            new InstrumentCandidate(
                item.symbol(), item.name(), item.exchange(), true, "REGISTERED"));
    try {
      for (var item : directory.search(q)) values.putIfAbsent(item.symbol(), item);
      return new InstrumentDiscoveryView(
          values.values().stream().toList(), "AVAILABLE", "搜索结果来自已登记证券及公开目录；添加时再次核验代码");
    } catch (RuntimeException error) {
      return new InstrumentDiscoveryView(
          values.values().stream().toList(), "UNAVAILABLE", "公开证券目录暂不可用；仍可添加已有登记证券，新证券请稍后重试");
    }
  }

  /** {@inheritDoc} */
  @Override
  public InstrumentCandidate register(String symbol) {
    if (symbol == null || !symbol.matches("[0-9]{6}\\.(SH|SZ|BJ)"))
      throw new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "请选择完整的股票代码");
    try {
      var old = market.instrument(symbol);
      if (!Boolean.TRUE.equals(old.getActive()) || !"STOCK".equals(old.getType()))
        throw new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "该证券未启用或不是股票");
      return new InstrumentCandidate(symbol, old.getName(), old.getExchange(), true, "REGISTERED");
    } catch (NotFoundException ignored) {
    }
    InstrumentCandidate selected;
    try {
      selected =
          directory.search(symbol.substring(0, 6)).stream()
              .filter(item -> item.symbol().equals(symbol))
              .findFirst()
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ResultCode.DATA_UNAVAILABLE, 422, "目录未确认该A股代码，不能直接创建虚构证券"));
    } catch (BusinessException error) {
      throw error;
    } catch (RuntimeException error) {
      throw new BusinessException(ResultCode.DATA_UNAVAILABLE, 503, "证券目录暂不可用，请稍后重试");
    }
    if (selected.name().contains("退"))
      throw new BusinessException(
          ResultCode.DATA_UNAVAILABLE, 422, "该目录标记为退市证券，不能作为当前在市自选新增；历史研究请由管理员核对生命周期");
    var row = new Instrument();
    row.setSymbol(selected.symbol());
    row.setName(selected.name());
    row.setExchange(selected.exchange());
    row.setType("STOCK");
    row.setActive(true);
    try {
      mapper.create(row);
    } catch (DuplicateKeyException ignored) {
    }
    return selected;
  }
}

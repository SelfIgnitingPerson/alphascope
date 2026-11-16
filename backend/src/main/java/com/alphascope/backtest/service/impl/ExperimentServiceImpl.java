package com.alphascope.backtest.service.impl;

import com.alphascope.backtest.calculator.SignalEvaluationCalculator;
import com.alphascope.backtest.dto.ExperimentPageQuery;
import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.dto.ValidationSnapshot;
import com.alphascope.backtest.entity.ValidationExperiment;
import com.alphascope.backtest.mapper.ExperimentMapper;
import com.alphascope.backtest.service.BacktestService;
import com.alphascope.backtest.service.ExperimentService;
import com.alphascope.backtest.vo.BacktestView;
import com.alphascope.backtest.vo.ExperimentReplayView;
import com.alphascope.backtest.vo.ExperimentView;
import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.exception.NotFoundException;
import com.alphascope.common.response.PageResponse;
import com.alphascope.common.util.JsonFingerprint;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.service.MarketService;
import com.alphascope.signal.service.SignalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实验捕获在一致读事务内完成，复算只使用已冻结输入，Mapper没有覆盖入口。
 *
 * @author chenxuefeng
 */
@Service
public class ExperimentServiceImpl implements ExperimentService {
  private final BacktestService backtest;
  private final MarketService market;
  private final SignalService signals;
  private final ExperimentMapper mapper;
  private final ObjectMapper json;
  private final Clock clock;

  public ExperimentServiceImpl(
      BacktestService backtest,
      MarketService market,
      SignalService signals,
      ExperimentMapper mapper,
      ObjectMapper json,
      Clock clock) {
    this.backtest = backtest;
    this.market = market;
    this.signals = signals;
    this.mapper = mapper;
    this.json = json;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public ExperimentView save(SignalStatisticsRequest request) {
    var input = backtest.inputs(request);
    var result = SignalEvaluationCalculator.evaluate(input);
    var snapshot =
        new ValidationSnapshot(
            input,
            signals.catalog(),
            signals.parameters(),
            clock.instant(),
            result.methodVersion());
    var row = new ValidationExperiment();
    row.setSymbol(request.getSymbol());
    row.setDataScope(market.dataScope());
    row.setAvailabilityMode(result.availabilityMode().name());
    row.setMethodVersion(result.methodVersion());
    row.setRequestJson(Jsons.write(request));
    row.setSnapshotJson(Jsons.write(snapshot));
    row.setResultJson(Jsons.write(result));
    if (row.getSnapshotJson().getBytes(StandardCharsets.UTF_8).length > 32 * 1024 * 1024) {
      throw new IllegalArgumentException("实验输入超过32MiB，请缩小日期区间");
    }
    row.setInputHash(JsonFingerprint.hash(row.getSnapshotJson()));
    row.setResultHash(JsonFingerprint.hash(row.getResultJson()));
    mapper.insert(row);
    return get(row.getId());
  }

  /** {@inheritDoc} */
  @Override
  public PageResponse<ExperimentView> page(PageRequest request) {
    request.validate();
    return new PageResponse<>(
        mapper
            .page(
                new ExperimentPageQuery(
                    request.getSize(), (request.getPage() - 1) * request.getSize()))
            .stream()
            .map(row -> ExperimentView.from(row, null))
            .toList(),
        mapper.count(),
        request.getPage(),
        request.getSize());
  }

  /** {@inheritDoc} */
  @Override
  public ExperimentView get(long id) {
    var row = checked(id);
    return ExperimentView.from(row, decode(row.getResultJson(), BacktestView.class));
  }

  /** {@inheritDoc} */
  @Override
  public ExperimentReplayView reproduce(long id) {
    var row = checked(id);
    var snapshot = decode(row.getSnapshotJson(), ValidationSnapshot.class);
    if (!"SAVED_SIGNAL_VALIDATION_V3".equals(snapshot.methodVersion())) {
      throw new IllegalArgumentException("当前运行版本不支持该实验方法，请使用匹配版本复算");
    }
    var result = SignalEvaluationCalculator.evaluate(snapshot.input());
    String actual = JsonFingerprint.hash(Jsons.write(result));
    return new ExperimentReplayView(
        id, true, row.getResultHash().equals(actual), row.getResultHash(), actual, result);
  }

  private ValidationExperiment checked(long id) {
    var row = mapper.get(id);
    if (row == null) {
      throw new NotFoundException("实验不存在");
    }
    if (!row.getInputHash().equals(JsonFingerprint.hash(row.getSnapshotJson()))
        || !row.getResultHash().equals(JsonFingerprint.hash(row.getResultJson()))) {
      throw new IllegalStateException("实验冻结内容指纹不一致，停止读取或复算");
    }
    return row;
  }

  private <T> T decode(String value, Class<T> type) {
    try {
      return json.readValue(value, type);
    } catch (IOException e) {
      throw new IllegalStateException("实验冻结格式无效", e);
    }
  }
}

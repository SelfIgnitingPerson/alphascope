package com.alphascope;

import static org.junit.jupiter.api.Assertions.*;

import com.alphascope.backtest.dto.AvailabilityMode;
import com.alphascope.backtest.dto.SignalStatisticsRequest;
import com.alphascope.backtest.service.BacktestService;
import com.alphascope.backtest.service.ExperimentService;
import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.util.Jsons;
import com.alphascope.market.dto.MarketBarDO;
import com.alphascope.market.service.MarketService;
import com.alphascope.signal.service.SignalService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 在隔离MySQL中重建旧修订，并验证保存实验不受当前值覆盖及JSON格式化影响。
 *
 * @author chenxuefeng
 */
@SpringBootTest(
    properties = {
      "alphascope.provider=multi",
      "alphascope.jobs.worker-enabled=false",
      "alphascope.operations.backup-enabled=false",
      "alphascope.scheduler.enabled=false"
    })
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class RevisionReplayIntegrationTest {
  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.40");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
    properties.add("spring.datasource.username", MYSQL::getUsername);
    properties.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired MarketService market;
  @Autowired SignalService signals;
  @Autowired BacktestService backtest;
  @Autowired ExperimentService experiments;

  @Test
  void oldImmutableVersionsAndExperimentsSurviveCurrentRowChanges() {
    LocalDate start = LocalDate.of(1980, 1, 2), end = start.plusDays(20);
    long stock = market.instrument("300476.SZ").getId(),
        index = market.instrument("000300.SH").getId();
    for (int i = 0; i < 21; i++) {
      LocalDate day = start.plusDays(i);
      LocalDateTime saved = day.atTime(16, 0);
      jdbc.update(
          "INSERT INTO trading_calendar(trade_date,exchange,is_open,is_synthetic,source,created_at,updated_at) VALUES(?,'SSE',1,0,'BAOSTOCK',?,?)",
          day,
          saved,
          saved);
      revision(stock, day, BigDecimal.valueOf(100 + i), saved, saved, "original-stock-" + i);
      revision(index, day, BigDecimal.valueOf(200 + i * .5), saved, saved, "original-index-" + i);
      if (i == 0) {
        revision(
            stock,
            day,
            new BigDecimal("999"),
            day.plusDays(1).atTime(16, 0),
            day.plusDays(1).atTime(16, 0),
            "later-correction");
      }
    }
    jdbc.update(
        "INSERT INTO signal_revision(instrument_id,trade_date,rule_code,rule_version,severity,status,evidence_json,parameters_json,content_hash,recorded_at,is_synthetic) VALUES(?,?,'RULE_A','V1','LOW','TRIGGERED',?,?,'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',?,0)",
        stock,
        start,
        "{\"values\":{},\"ruleParameters\":{\"threshold\":0.2}}",
        "{\"threshold\":0.2}",
        start.atTime(16, 10));
    jdbc.update(
        "INSERT INTO signal_revision(instrument_id,trade_date,rule_code,rule_version,severity,status,evidence_json,parameters_json,content_hash,recorded_at,is_synthetic) VALUES(?,?,'RULE_A','V1','LOW','NOT_TRIGGERED','{}','{}','bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',?,0)",
        stock,
        start,
        start.plusDays(1).atTime(16, 10));
    var range = DateRangeRequest.builder().start(start).end(end).build();
    var replay = market.replayBars("300476.SZ", range);
    assertEquals(21, replay.size());
    assertEquals(0, new BigDecimal("100").compareTo(replay.getFirst().getClose()));
    assertNotNull(replay.getFirst().getRevisionId());
    var versions = signals.replayRecords("300476.SZ", range);
    assertEquals(1, versions.size());
    assertEquals("TRIGGERED", versions.getFirst().getStatus());
    assertNotNull(versions.getFirst().getRevisionId());
    var request =
        SignalStatisticsRequest.builder()
            .symbol("300476.SZ")
            .start(start)
            .end(start)
            .evaluationDate(end)
            .availabilityMode(AvailabilityMode.POINT_IN_TIME)
            .build();
    var observed = backtest.statistics(request);
    assertEquals(1, observed.statistics().get("20D").get("RULE_A").sampleCount());
    assertEquals(.2, observed.statistics().get("20D").get("RULE_A").meanReturn(), 1e-12);
    var experiment = experiments.save(request);
    assertNotNull(experiment.result());
    assertTrue(experiments.reproduce(experiment.id()).resultMatches());
    jdbc.update(
        "INSERT INTO daily_bar(instrument_id,trade_date,open,high,low,close,volume,amount,source,fetched_at,batch_id,is_synthetic) VALUES(?,?,999,999,999,999,100,99900,'BAOSTOCK',?,'current',0)",
        stock,
        start,
        LocalDateTime.now());
    jdbc.update(
        "INSERT INTO adjustment_factor(instrument_id,trade_date,factor,source,is_synthetic) VALUES(?,?,99,'BAOSTOCK',0)",
        stock,
        start);
    assertTrue(experiments.reproduce(experiment.id()).resultMatches());
    assertEquals(experiment.resultHash(), experiments.reproduce(experiment.id()).actualHash());
    assertEquals(
        0,
        new BigDecimal("100")
            .compareTo(market.replayBars("300476.SZ", range).getFirst().getClose()));
    // 仅在测试库篡改保存值，读取和复算都必须拒绝。
    jdbc.update(
        "UPDATE validation_experiment SET snapshot_json=JSON_SET(snapshot_json,'$.input.minimumSamples',777) WHERE id=?",
        experiment.id());
    assertThrows(IllegalStateException.class, () -> experiments.get(experiment.id()));
    assertThrows(IllegalStateException.class, () -> experiments.reproduce(experiment.id()));
  }

  @Test
  void unknownOriginalRecordedTimeIsNeverBackfilledFromVendorTimestamp() {
    LocalDate date = LocalDate.of(1979, 1, 2);
    long instrument = market.instrument("300476.SZ").getId();
    revision(instrument, date, new BigDecimal("10"), date.atTime(16, 0), null, "unknown-recorded");
    assertTrue(
        market
            .replayBars("300476.SZ", DateRangeRequest.builder().start(date).end(date).build())
            .isEmpty());
  }

  private void revision(
      long instrument,
      LocalDate date,
      BigDecimal close,
      LocalDateTime fetched,
      LocalDateTime recorded,
      String batch) {
    var row = new MarketBarDO();
    row.setInstrumentId(instrument);
    row.setTradeDate(date);
    row.setClose(close);
    row.setOpen(close);
    row.setHigh(close);
    row.setLow(close);
    row.setVolume(BigDecimal.TEN);
    row.setAmount(close.multiply(BigDecimal.TEN));
    row.setFactor(BigDecimal.ONE);
    row.setSource("BAOSTOCK");
    row.setFetchedAt(fetched);
    row.setSynthetic(false);
    row.setBatchId(batch);
    jdbc.update(
        "INSERT INTO market_data_revision(instrument_id,trade_date,source,is_synthetic,batch_id,fetched_at,content_hash,bar_json,recorded_at,algorithm_version) VALUES(?,?,'BAOSTOCK',0,?,?,?,?,?,'RAW_V2')",
        instrument,
        date,
        batch,
        fetched,
        Jsons.hash(Jsons.write(row)),
        com.alphascope.common.util.RevisionJson.encode(Jsons.write(row)),
        recorded);
  }
}

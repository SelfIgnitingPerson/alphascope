package com.alphascope.fundamental.service;

import com.alphascope.fundamental.vo.ForecastView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 独立采集并按实际首次观测时刻归档公开EPS预测；行情失败不阻断该采集。
 *
 * @author chenxuefeng
 */
public interface ForecastService {

  /**
   * 采集并按实际首次观测时刻保存公开盈利预测；演示模式不采集，已有快照保留不可变时点。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   */
  void sync(String symbol);

  /** 独立采集全部启用自选的盈利预测，不依赖行情阶段是否成功或当日是否开市。 */
  void syncWatchlist();

  /**
   * 只读查询启用自选在指定日期结束前已观测的预测，截止时点不晚于实际当前时刻。
   *
   * @param asOf 查询业务日期，上海时区
   * @return 各自选预测及历史差额列表；没有启用自选时为空列表
   */
  List<ForecastView> list(LocalDate asOf);

  /**
   * 只读查询报告业务日 16:10 前已观测的预测，不倒填之后才采集的快照。
   *
   * @param date 研究或记录对应的业务日期，上海时区
   * @return 供报告冻结的各自选盈利预测证据
   */
  List<ForecastView> forReport(LocalDate date);

  /**
   * 组装单只证券已保存预测、同年度可比差额和证据边界，不访问上游。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param name 证券中文名称，用于研究结果展示
   * @param cutoff 公开证据可采用的截止时刻，上海时区
   * @return 可用预测或明确的未观测/不适用状态，不伪造旧快照
   */
  ForecastView view(String symbol, String name, LocalDateTime cutoff);
}

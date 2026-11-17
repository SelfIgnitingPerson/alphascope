package com.alphascope.market.service;

import com.alphascope.market.dto.LeaderScoreEvidence;
import com.alphascope.market.vo.MarketObservationView;
import java.time.LocalDate;
import java.util.Map;

/**
 * 全市场批量截面与独立因子证据分开持久化；网络请求不占数据库事务，采集中断后可续用缓存。
 *
 * @author chenxuefeng
 */
public interface MarketObservationService {

  /**
   * 读取影响沪深独立观察结果的覆盖率、量能和广度阈值，供证据冻结使用。
   *
   * @return 参数名到实际阈值的只读研究参数快照
   */
  Map<String, Double> parameters();

  /**
   * 使用已保存或补采的真实截面、日历、基准及复权事件形成独立沪深观察；缺失分项不补造。
   *
   * @param date 研究或记录对应的业务日期，上海时区
   */
  void sync(LocalDate date);

  /**
   * 仅用已保存截面与已核验因子形成观察，不在私人报告中启动全市场网络补齐。
   *
   * @param date 业务日期
   */
  void calculateStored(LocalDate date);

  /**
   * 只读返回不晚于业务日的最近已保存观察及采集状态，不触发上游回补。
   *
   * @param date 研究或记录对应的业务日期，上海时区
   * @return 已保存观察或明确的未检查、不适用、不可用状态
   */
  MarketObservationView view(LocalDate date);

  /**
   * 只读返回指定业务日已冻结的领先样本计算，不用较早快照代替本日。
   *
   * @param date 业务日期
   * @return 已保存证据；旧观察没有该字段时为空
   */
  LeaderScoreEvidence leaders(LocalDate date);
}

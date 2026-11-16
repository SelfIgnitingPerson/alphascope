package com.alphascope.signal.mapper;

import com.alphascope.common.dto.InstrumentHistoryQuery;
import com.alphascope.signal.entity.SignalEvent;
import com.alphascope.signal.entity.SignalRevision;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 按标的、业务日期、规则版本及真实/演示模式保存逐项评估。
 *
 * @author chenxuefeng
 */
@Mapper
public interface SignalMapper {

  /**
   * 按证券、日期区间和数据模式读取已保存规则评估，按日期倒序、规则代码排序。
   *
   * @param query 统一证券代码、闭区间起止日期及真实/演示模式
   * @return 最多 1000 条评估；无记录时返回空列表，未触发和证据不足由各记录的状态区分
   */
  List<SignalEvent> list(InstrumentHistoryQuery query);

  /**
   * 保存单个证券、日期、规则版本及模式的评估，已存在时更新等级、证据和状态。
   *
   * @param row 规则代码、版本、触发状态、风险等级、证据 JSON 及真实/演示标志
   */
  void save(SignalEvent row);

  /**
   * 读取完整同模式历史记录供研究验证，调用方限制查询范围，不对结果静默截断。
   *
   * @param query 证券代码、日期闭区间及服务端模式
   * @return 按日期、规则和主键升序的全部匹配记录
   */
  List<SignalEvent> evaluationRecords(InstrumentHistoryQuery query);

  /**
   * 锁定证券行，使同一标的的规则当前值和修订序列一致提交。
   *
   * @param instrumentId 标的主键
   * @return 锁定标的主键，不存在时为null
   */
  Long lockInstrument(long instrumentId);

  /**
   * 读取当前用户同一规则最后一次不可变修订的内容指纹；调用方持有证券行锁。
   *
   * @param row 证券、日期、规则版本及真实演示范围
   * @return 最近指纹，尚无修订时为null
   */
  String latestRevisionHash(SignalRevision row);

  /**
   * 在已锁定证券且指纹变化时追加不可变修订；不以跨用户的旧行去重。
   *
   * @param revision 完整规则证据、实际参数与指纹
   */
  void archive(SignalRevision revision);

  /**
   * 读取各交易日结束前最后保存的规则修订，不使用后来覆盖的当前值。
   *
   * @param query 标的、日期闭区间和模式
   * @return 按业务日期和规则排列的已知时点修订
   */
  List<SignalRevision> replay(InstrumentHistoryQuery query);
}

package com.alphascope.signal.service;

import com.alphascope.common.dto.DateRangeRequest;
import com.alphascope.common.dto.OptionalDateRangeRequest;
import com.alphascope.signal.dto.RuleCatalog;
import com.alphascope.signal.entity.SignalEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 整理价格、主题、财报及可选持仓事实后执行纯规则，保存缺失输入和不适用原因。
 *
 * @author chenxuefeng
 */
public interface SignalService {

  /**
   * 读取当前规则完整清单、版本和适用性，供产物完整性核验及实验冻结。
   *
   * @return 不从数据库结果反推的当前规则定义
   */
  RuleCatalog catalog();

  /**
   * 只读查询指定证券、日期区间和当前数据模式的已保存规则评估。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param request 可选日期区间；省略时为截止日期之前一个月
   * @return 按日期倒序排列的规则记录；没有记录时为空列表
   */
  List<SignalEvent> list(String symbol, OptionalDateRangeRequest request);

  /**
   * 读取风险规则实际采用的阈值字典，供输入指纹及报告证据冻结使用。
   *
   * @return 规则参数名与阈值的只读快照
   */
  Map<String, Double> parameters();

  /**
   * 整理行情、指标、财报、同主题连续证据及可选人工持仓事实，计算并保存规则状态和缺失诊断。
   *
   * @param symbol 统一证券代码，例如 300476.SZ 或 000300.SH
   * @param date 研究或记录对应的业务日期，上海时区
   */
  void calculate(String symbol, LocalDate date);

  /**
   * 读取范围受控的完整研究记录，不复用页面1000条上限。
   *
   * @param symbol 统一证券代码
   * @param request 最长十年的必填日期闭区间
   * @return 按日期和规则升序的全部同模式记录，包含保存及修订时刻
   */
  List<SignalEvent> evaluationRecords(String symbol, DateRangeRequest request);

  /**
   * 重建各业务日结束前已入库的规则版本，不以当前记录替代修订。
   *
   * @param symbol 统一证券代码
   * @param request 日期闭区间
   * @return 保留不可变修订ID和实际阈值的规则证据
   */
  List<SignalEvent> replayRecords(String symbol, DateRangeRequest request);
}

package com.alphascope.backtest.service;

import com.alphascope.backtest.dto.ValidationCommand;
import com.alphascope.backtest.dto.ValidationDataset;

/**
 * 用短期一致读事务捕获验证资料，随后释放连接再执行计算。
 *
 * @author chenxuefeng
 */
public interface ValidationDataService {
  /**
   * 只读取已保存事实，不请求供应商或改写业务信号。
   *
   * @param command 冻结条件及规则
   * @return 完整的内存数据集和逐证券缺项
   */
  ValidationDataset capture(ValidationCommand command);
}

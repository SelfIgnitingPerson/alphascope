package com.alphascope.operations.service;

import com.alphascope.operations.vo.ProductStatusView;
import com.alphascope.operations.vo.WorkspaceStatusView;

/**
 * 技术健康之外的产品运行就绪状态。
 *
 * @author chenxuefeng
 */
public interface ProductStatusService {
  /**
   * 查看当前账号的数据时效与资源。
   *
   * @return 只读工作区状态
   */
  WorkspaceStatusView workspace();

  /**
   * 查看来源登记与发布缺项。
   *
   * @return 管理员产品状态
   */
  ProductStatusView product();

  /** 持久记录本次检查与恢复，不向外发送消息。 */
  void observe();
}

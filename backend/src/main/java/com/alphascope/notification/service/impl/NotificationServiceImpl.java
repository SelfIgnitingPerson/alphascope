package com.alphascope.notification.service.impl;

import com.alphascope.common.util.Jsons;
import com.alphascope.notification.mapper.NotificationMapper;
import com.alphascope.notification.service.NotificationService;
import org.springframework.stereotype.Service;

/**
 * NotificationService 的业务实现。
 *
 * <p>MVP本地通知审计，不发送外部邮件/消息。模拟报告禁止通知。
 *
 * @author chenxuefeng
 */
@Service
public class NotificationServiceImpl implements NotificationService {
  private final NotificationMapper mapper;

  public NotificationServiceImpl(NotificationMapper mapper) {
    this.mapper = mapper;
  }

  /** {@inheritDoc} */
  @Override
  public void notifyLocal(long reportId, boolean synthetic, String businessKey) {
    if (!synthetic) {
      mapper.save(reportId, Jsons.hash("local"), businessKey + ":LOCAL");
    }
  }
}

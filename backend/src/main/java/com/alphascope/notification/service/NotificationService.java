package com.alphascope.notification.service;

/**
 * MVP本地通知审计，不发送外部邮件/消息。模拟报告禁止通知。
 *
 * @author chenxuefeng
 */
public interface NotificationService {

  /**
   * 为真实报告记录本地通知审计，相同业务幂等键不重复写入；演示报告不记录，不向外发送消息。
   *
   * @param reportId 已生成报告的内部主键
   * @param synthetic 是否为演示报告；true 时不记录真实报告通知
   * @param businessKey 用于本地通知去重的业务幂等标识
   */
  void notifyLocal(long reportId, boolean synthetic, String businessKey);
}

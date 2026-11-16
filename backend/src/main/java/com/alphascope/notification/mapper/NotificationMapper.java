package com.alphascope.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 本地通知去重与审计写入，当前不发送邮件或即时通信消息。
 *
 * @author chenxuefeng
 */
@Mapper
public interface NotificationMapper {

  /**
   * 写入本地通知审计记录，相同幂等键重复提交时忽略新增，不向外发送消息。
   *
   * @param id 已生成报告的主键，关联 analysis_report.id
   * @param hash 本地通知接收对象的去重指纹，不保存明文身份信息
   * @param key 由业务任务和本地通道组成的通知幂等键
   * @return 插入行数；1 为首次记录，0 为相同幂等键已存在
   */
  int save(@Param("id") long id, @Param("hash") String hash, @Param("key") String key);
}

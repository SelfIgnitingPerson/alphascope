package com.alphascope.operations.mapper;

import com.alphascope.operations.dto.AlertWrite;
import com.alphascope.operations.entity.ServiceAlert;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 当前用户告警的持久生命周期，重复发现不反复新增记录。
 *
 * @author chenxuefeng
 */
@Mapper
public interface AlertMapper {
  /**
   * 更新已发现事项或重新打开已恢复事项。
   *
   * @param command 受控摘要及时间
   */
  void observe(AlertWrite command);

  /**
   * 将此次未再观察到的旧事项恢复。
   *
   * @param observedAt 此次完整检查的起始时间
   */
  void resolveBefore(LocalDateTime observedAt);

  /**
   * 查看当前未恢复事项。
   *
   * @return 当前用户的告警记录
   */
  List<ServiceAlert> active();
}

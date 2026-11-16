package com.alphascope.scheduler.mapper;

import com.alphascope.scheduler.dto.JobInputState;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 只用不可变修订号和业务内容版本作为重算依据，不使用采集重试时间。
 *
 * @author chenxuefeng
 */
@Mapper
public interface JobInputMapper {

  /**
   * 读取业务时点下不可变行情修订、可见财报及其他证据的版本状态，用于计算任务指纹。
   *
   * @param date 对应的业务日期，按上海时区解释
   * @param synthetic true 查询演示数据，false 查询真实数据，两种模式不得混用
   * @return 固定结构的证据版本状态；尚无对应证据的字段保持 SQL 查询定义的空值或计数
   */
  JobInputState state(@Param("date") LocalDate date, @Param("synthetic") boolean synthetic);
}

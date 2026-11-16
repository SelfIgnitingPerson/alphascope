package com.alphascope.backtest.mapper;

import com.alphascope.backtest.dto.ExperimentPageQuery;
import com.alphascope.backtest.entity.ValidationExperiment;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 不可覆盖的历史验证实验持久化，仅新增和读取，无更新或删除入口。
 *
 * @author chenxuefeng
 */
@Mapper
public interface ExperimentMapper {
  /**
   * 保存完整冻结实验，回填自增主键。
   *
   * @param experiment 输入、结果、语义指纹及方法元数据
   */
  void insert(ValidationExperiment experiment);

  /**
   * 读取某次实验的完整冻结内容。
   *
   * @param id 实验主键
   * @return 实验，未找到为null
   */
  ValidationExperiment get(long id);

  /**
   * 分页读取实验元数据，不加载大快照。
   *
   * @param request 已校验分页对象
   * @return 主键倒序的元数据行
   */
  List<ValidationExperiment> page(ExperimentPageQuery request);

  /**
   * 读取已保存实验总数。
   *
   * @return 全部不可变实验数量
   */
  long count();
}

package com.alphascope.intelligence.mapper;

import com.alphascope.intelligence.dto.ForwardResultQuery;
import com.alphascope.intelligence.entity.ForwardResult;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 记录窗口首次真实完成的评估，读页面不改变结果。
 *
 * @author chenxuefeng
 */
@Mapper
public interface ForwardResultMapper {
  /**
   * 幂等追加成熟结果，唯一键保护旧版本。
   *
   * @param row 完整输入和结果
   * @return 新增行数
   */
  int save(ForwardResult row);

  /**
   * 读取截止时间结果摘要，不加载大块原始输入。
   *
   * @param query 截止时间与本批观察编号
   * @return 窗口结果
   */
  List<ForwardResult> summaries(ForwardResultQuery query);

  /**
   * 核查当前用户结果数量，配合正文预算限制。
   *
   * @return 已保存窗口数
   */
  long count();

  /**
   * 读取当前身份完整复算证据。
   *
   * @param id 已保存结果编号
   * @return 原始结果或null
   */
  ForwardResult get(long id);
}

package com.alphascope.operations.mapper;

import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;

/**
 * 仅追溯共享不可变价格批次，不读取其他用户私人研究。
 *
 * @author chenxuefeng
 */
@Mapper
public interface SourceEvidenceMapper {
  /**
   * 按实际原始批次追溯来源。
   *
   * @param batches 去重且有界的原始批次
   * @return 实际来源
   */
  List<String> batchSources(Set<String> batches);
}

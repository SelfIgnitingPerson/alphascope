package com.alphascope.operations.mapper;

import com.alphascope.operations.entity.SourceLicense;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公开数据来源使用范围的不可变登记。
 *
 * @author chenxuefeng
 */
@Mapper
public interface SourceLicenseMapper {
  /**
   * 保存一份人工核对的许可修订。
   *
   * @param row 来源、范围、依据与时限
   */
  void save(SourceLicense row);

  /**
   * 读取每个来源最新登记，不回退到被后续登记替代的旧许可。
   *
   * @return 最新修订列表
   */
  List<SourceLicense> latest();
}

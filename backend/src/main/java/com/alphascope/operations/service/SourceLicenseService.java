package com.alphascope.operations.service;

import com.alphascope.operations.dto.SourceLicenseInput;
import com.alphascope.operations.vo.SourceLicenseView;
import java.util.List;

/**
 * 来源使用范围检查；未登记不能在受控公网模式中显示、导出或采集。
 *
 * @author chenxuefeng
 */
public interface SourceLicenseService {
  /**
   * 管理员新增许可登记。
   *
   * @param input 依据与明确范围
   */
  void declare(SourceLicenseInput input);

  /**
   * 查看实际启用来源的许可状态。
   *
   * @return 每个来源的真实登记状态
   */
  List<SourceLicenseView> status();

  /**
   * 返回当前运行模式是否强制检查来源许可，与实际准入规则一致。
   *
   * @return 公网模式或显式开启许可检查时为true
   */
  boolean isEnforced();

  /**
   * 检查指定使用范围。
   *
   * @param source 来源代码
   * @param permission DISPLAY、COLLECT、EXPORT或DERIVED_REPORT
   */
  void require(String source, String permission);

  /** 检查当前配置公共数据的展示准入，不检查用户身份。 */
  void requireDisplay();

  /**
   * 检查当前启用来源的指定使用范围，不能将展示许可默认为导出许可。
   *
   * @param permission 明确的使用范围代码
   */
  void requireAll(String permission);
}

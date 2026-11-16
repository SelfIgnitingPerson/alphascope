package com.alphascope.industry.service;

import java.time.LocalDate;
import java.util.Set;

/**
 * 导入已核对原始披露的固定样本；不把当前概念股名单伪装成历史官方成分。
 *
 * @author chenxuefeng
 */
public interface ThemeCohortService {

  /**
   * 返回当前已核验披露样本定义的稳定内容指纹，用于任务输入识别。
   *
   * @return 完整样本目录定义的 SHA-256 指纹
   */
  String catalogHash();

  /**
   * 只导入业务日 16:10 前实际可见的披露研究样本及其成员；每版在短事务内保存，冲突时拒绝覆盖。
   *
   * @param date 研究或记录对应的业务日期，上海时区
   */
  void prepare(LocalDate date);

  /**
   * 读取业务日可见披露样本的完整证券代码集合，不自动加入用户自选。
   *
   * @param date 研究或记录对应的业务日期，上海时区
   * @return 去重后的样本证券代码；演示模式或没有可见样本时为空集合
   */
  Set<String> symbols(LocalDate date);
}

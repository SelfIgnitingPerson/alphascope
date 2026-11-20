package com.alphascope.portfolio.mapper;

import com.alphascope.portfolio.dto.PortfolioHistoryQuery;
import com.alphascope.portfolio.entity.PortfolioRevision;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 私人登记历史，租户由统一SQL边界注入，只有追加和读取。
 *
 * @author chenxuefeng
 */
@Mapper
public interface PortfolioHistoryMapper {
  /**
   * 幂等追加真实登记版本。
   *
   * @param row 服务端捕获记录
   */
  void save(PortfolioRevision row);

  /**
   * 读取截止时点每个标的和账户的最近有效版本。
   *
   * @param query 双时点条件
   * @return 不可变登记
   */
  List<PortfolioRevision> at(PortfolioHistoryQuery query);

  /**
   * 列出曾有真实历史的证券，避免从当前表倒填过去。
   *
   * @return 记录身份键
   */
  List<String> recordedSymbols();
}

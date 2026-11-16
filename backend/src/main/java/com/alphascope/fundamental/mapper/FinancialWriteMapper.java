package com.alphascope.fundamental.mapper;

import com.alphascope.fundamental.entity.FinancialReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 保存手工累计财报，沿用明确的公开来源和版本序号。
 *
 * @author chenxuefeng
 */
@Mapper
public interface FinancialWriteMapper {

  /**
   * 在当前事务中锁定证券主数据行，串行化同一标的的财报版本写入。
   *
   * <p>调用方须在有效数据库事务内执行，并在同一事务内完成版本查重及新增；行锁随事务提交或回滚释放。
   *
   * @param id 证券主键，关联 instrument.id
   * @return 被锁定证券的主键；标的不存在时返回 null
   */
  Long lockInstrument(long id);

  /**
   * 新增带公开来源及明确版本号的手工累计财报，不覆盖历史版本。
   *
   * <p>由调用方事务持有证券行锁；财务空值表示证据缺失，不能替换为 0。
   *
   * @param row 已校验的证券主键、累计报告期、版本、财务值及公开来源
   * @throws org.springframework.dao.DuplicateKeyException 同证券、同报告期、同口径和同版本的记录已经存在
   */
  void insert(FinancialReport row);
}

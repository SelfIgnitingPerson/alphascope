package com.alphascope.fundamental.mapper;

import com.alphascope.fundamental.provider.EastmoneyFinancialProvider;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 自动财报的版本查重、事务锁和不可变原文写入；不覆盖同内容历史版本。
 *
 * @author chenxuefeng
 */
@Mapper
public interface FinancialSyncMapper {

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
   * 按证券和内容指纹检查已保存财报，避免相同原文重复入库。
   *
   * @param id 证券主键，关联 instrument.id
   * @param hash 原始内容的 SHA-256 指纹，用于不可变证据查重
   * @return 匹配记录数；0 表示尚未保存该内容
   */
  int exists(@Param("id") long id, @Param("hash") String hash);

  /**
   * 新增供应商累计财报，按同证券、同报告期和同口径分配下一版本号。
   *
   * <p>调用方先在同一事务中锁定证券并检查内容指纹；保留原始披露、可用时点、修订信息和原文，不覆盖已有版本。
   *
   * @param id 证券主键，关联 instrument.id
   * @param statement 已解析且含原文、来源和内容指纹的累计财报
   * @param fetched 本次实际采集时刻，上海时区
   */
  void insert(
      @Param("id") long id,
      @Param("s") EastmoneyFinancialProvider.Statement statement,
      @Param("fetched") LocalDateTime fetched);
}

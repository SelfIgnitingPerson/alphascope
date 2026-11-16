package com.alphascope.industry.vo;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * 当前产业核心企业研究目录；展示业务地位与来源，不作为历史成分或评分输入。
 *
 * @author chenxuefeng
 */
public record ThemeLeadersView(
    /** 所属主题代码。 */
    String themeCode,
    /** 当前核心企业组名称。 */
    String title,
    /** 市场常用组合称呼；不是正式排名。 */
    String marketAlias,
    /** 本产品实际整理核对日期，不倒填为历史有效日期。 */
    LocalDate reviewedAt,
    /** 入选与展示顺序的可读解释。 */
    String selectionBasis,
    /** 跨细分比较与资料时点的边界。 */
    String limitation,
    /** 按研究优先序排列的核心和细分企业，不等于按涨幅排序。 */
    List<Member> members) {
  /** 按明确展示序复制名单，避免调用方修改共享目录或把数据库返回序当排名。 */
  public ThemeLeadersView {
    members = members.stream().sorted(Comparator.comparingInt(Member::displayOrder)).toList();
  }

  /**
   * 一家核心企业的业务位置和支持资料；不携带交易建议。
   *
   * @author chenxuefeng
   */
  public record Member(
      /** 展示优先序，不是未经证实的行业第几名。 */
      int displayOrder,
      /** A股统一证券代码。 */
      String symbol,
      /** 公司简称。 */
      String name,
      /** 核心龙头或细分代表的研究分类。 */
      String tier,
      /** 公司在该链条中的具体业务。 */
      String segment,
      /** 能够由来源支持的行业位置，不将光器件错排为光模块第三。 */
      String standing,
      /** 已披露的业务事实；梯队属于基于事实的研究判断。 */
      String rationale,
      /** 同口径正式排名；没有可核对名次时为空。 */
      String formalRanking,
      /** 原始报告或研究机构来源。 */
      List<Source> sources) {
    /** 来源在读取后不可变，不能由页面查询改写。 */
    public Member {
      sources = List.copyOf(sources);
    }
  }

  /**
   * 页面直接可核对的原始公开来源。
   *
   * @author chenxuefeng
   */
  public record Source(
      /** 来源标题。 */
      String title,
      /** 原始公开资料的HTTPS地址。 */
      String url) {}
}

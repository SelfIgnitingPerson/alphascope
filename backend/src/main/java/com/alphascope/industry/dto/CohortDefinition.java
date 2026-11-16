package com.alphascope.industry.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开披露研究样本的完整定义，保留来源、指纹及实际可用时点。
 *
 * @author chenxuefeng
 */
public record CohortDefinition(
    /** 稳定业务代码，需结合所属结构解释。 */
    String code,

    /** 主题稳定代码，例如PCB或COOLING。 */
    String themeCode,

    /** 供研究页面展示的中文标题。 */
    String title,

    /** 研究覆盖范围；固定样本、沪深A股与完整全A不能相互替代。 */
    String scope,

    /** 材料公开发布时间；仅知日期时按供应商的保守可用时点处理。 */
    LocalDateTime publishedAt,

    /** 该证据最早可用于研究的时刻；不能倒填为未观测到的历史时间。 */
    LocalDateTime availableAt,

    /** 可核验的公开原始来源URL。 */
    String sourceUrl,

    /** 支持该研究样本的原始文档标题。 */
    String sourceTitle,

    /** 支持该样本成员名单的原始PDF页码。 */
    String sourcePages,

    /** 已核验原始PDF字节的SHA-256指纹。 */
    String pdfSha256,

    /** 支持样本定义的原始披露摘录，不能扩展为未披露事实。 */
    String excerpt,

    /** 固定研究样本的选择依据、成员定义及覆盖边界。 */
    String definition,

    /** 实际核对原始来源和指纹的时间，不作为原文首次公开时间。 */
    LocalDateTime verifiedAt,

    /** 该不可变研究样本定义的完整成员，不等于整个行业成分。 */
    List<CohortMember> members) {}

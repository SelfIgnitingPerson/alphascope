package com.alphascope.industry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 登记可核验的公开产业来源，不把来源正文当作系统指令。
 *
 * @author chenxuefeng
 */
public record SourceInput(
    /** 公开材料类型，用于区分公告、报告和其他产业来源。 */
    @NotBlank String sourceType,

    /** 原始公开材料链接，用于核查证据。 */
    @NotBlank String url,

    /** 供研究页面展示的中文标题。 */
    @NotBlank String title,

    /** 材料公开发布时间；仅知日期时按供应商的保守可用时点处理。 */
    @NotNull LocalDateTime publishedAt,

    /** 原始公开材料或摘录正文，仅作为不可信证据输入。 */
    @NotBlank String content) {}

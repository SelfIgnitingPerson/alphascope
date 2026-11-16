package com.alphascope.operations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

/**
 * 管理员登记的来源使用范围，不将人工登记声称为供应商或法律认证。
 *
 * @author chenxuefeng
 * @param source 实际来源代码
 * @param permissions 已取得的使用范围
 * @param evidenceUrl 授权依据的HTTPS链接，不由服务器自动抓取
 * @param reference 合同或许可条款定位，不填写密钥
 * @param validFrom 许可开始日期
 * @param validUntil 许可截止日期
 */
public record SourceLicenseInput(
    @NotBlank @Pattern(regexp = "[A-Z_]{2,40}") String source,
    @NotEmpty Set<String> permissions,
    @NotBlank @Size(max = 1000) String evidenceUrl,
    @NotBlank @Size(max = 200) String reference,
    @NotNull LocalDate validFrom,
    @NotNull LocalDate validUntil) {}

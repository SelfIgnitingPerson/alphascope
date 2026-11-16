package com.alphascope.operations.vo;

import java.time.LocalDate;
import java.util.Set;

/**
 * 许可登记状态，始终注明人工登记不等于独立授权审查。
 *
 * @author chenxuefeng
 * @param source 来源代码
 * @param status MISSING、EXPIRED、NOT_YET_VALID或DECLARED_BY_OPERATOR
 * @param permissions 声明的范围
 * @param validUntil 截止日期
 * @param evidenceUrl 依据链接
 * @param reference 条款或合同定位
 */
public record SourceLicenseView(
    String source,
    String status,
    Set<String> permissions,
    LocalDate validUntil,
    String evidenceUrl,
    String reference) {}

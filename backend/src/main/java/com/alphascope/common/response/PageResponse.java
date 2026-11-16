package com.alphascope.common.response;

import java.util.List;

/**
 * 所有分页列表统一返回结果、总量和页码。
 *
 * @author chenxuefeng
 */
public record PageResponse<T>(
    /** 当前页或当前响应包含的业务记录列表。 */
    List<T> items,

    /** 符合查询条件的全部记录数，不是当前页长度。 */
    long total,

    /** 当前页码，从1开始。 */
    int page,

    /** 每页返回条数；接口对最大值另有校验。 */
    int size) {}

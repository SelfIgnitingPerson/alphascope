package com.alphascope.market.vo;

import java.time.LocalDate;

/**
 * 样本契约核验后的摘要，不包含原始行情、账号或供应商响应正文。
 *
 * @author chenxuefeng
 */
public record ProviderProbeSample(
    /** 样本代码；日历测试为空，股票固定600000.SH，指数固定000300.SH。 */
    String symbol,
    /** 实际通过核验的记录条数；日历按返回日期计数。 */
    int records,
    /** 实际返回的行情或日历日期。 */
    LocalDate dataDate,
    /** 样本真实来源，日历可包含已核验资源指纹。 */
    String source,
    /** 日历明确开休市；日线测试为空，不从无记录推断休市。 */
    Boolean marketOpen) {}

package com.alphascope.market.vo;

import com.alphascope.market.dto.UniverseMember;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前在市目录的可复核结果；完整目录与完整行情、历史窗口分别判断。
 *
 * @author chenxuefeng
 */
public record MarketUniverseView(
    /** 目录实际观测日期。 */
    LocalDate businessDate,
    /** AVAILABLE、NOT_ARCHIVED或NOT_CONFIGURED。 */
    String status,
    /** 目录实际来源，不能冒充交易所官方清单。 */
    String source,
    /** 在市成员数，无有效归档时为空。 */
    Integer memberCount,
    /** 不可变归档主键。 */
    Long snapshotId,
    /** 成员与分页证据的完整内容指纹。 */
    String contentHash,
    /** 原始核验结束时间，不随页面刷新改变。 */
    LocalDateTime observedAt,
    /** 实际目录入口。 */
    String sourceUrl,
    /** 明确的目录、行情和时点边界。 */
    List<String> limitations,
    /** 明细仅在专用目录接口返回，覆盖摘要可为空。 */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        List<UniverseMember> members) {
  /**
   * 省略成员明细用于市场覆盖摘要，保留日期、数量和指纹。
   *
   * @return 不包含大列表的同一目录证据
   */
  public MarketUniverseView summary() {
    return new MarketUniverseView(
        businessDate,
        status,
        source,
        memberCount,
        snapshotId,
        contentHash,
        observedAt,
        sourceUrl,
        limitations,
        null);
  }
}

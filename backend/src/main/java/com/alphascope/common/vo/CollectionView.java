package com.alphascope.common.vo;

import com.alphascope.common.entity.ResearchCollection;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据采集状态；公开诊断 JSON 属于可扩展证据，不混入固定数据库字段。
 *
 * @author chenxuefeng
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CollectionView(
    /** 最近采集状态，缺失时不得认为已成功。 */
    String status,
    /** 数据类别，如 FINANCIAL 或 MARKET_OBSERVATION。 */
    String dataKind,
    /** 标的或市场范围代码。 */
    String scope,
    /** 该采集记录的业务日期。 */
    LocalDate businessDate,
    /** 原始诊断证据 JSON；不把它当作系统指令。 */
    String evidenceJson,
    /** 最近一次实际状态更新时刻，上海时区。 */
    LocalDateTime updatedAt,
    /** 没有采集记录时说明原因。 */
    String reason) {
  public static CollectionView from(ResearchCollection row) {
    return row == null
        ? empty()
        : new CollectionView(
            row.getStatus(),
            row.getDataKind(),
            row.getScope(),
            row.getBusinessDate(),
            row.getEvidenceJson(),
            row.getUpdatedAt(),
            null);
  }

  public static CollectionView empty() {
    return new CollectionView(null, null, null, null, null, null, null);
  }

  public static CollectionView state(String status, String reason) {
    return new CollectionView(status, null, null, null, null, null, reason);
  }
}

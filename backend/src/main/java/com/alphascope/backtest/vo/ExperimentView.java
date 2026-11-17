package com.alphascope.backtest.vo;

import com.alphascope.backtest.entity.ValidationExperiment;
import com.alphascope.common.util.Jsons;
import com.alphascope.operations.policy.SourceEvidence;
import java.time.LocalDateTime;

/**
 * 冻结实验元数据及按需加载的原始结果。
 *
 * @author chenxuefeng
 */
public record ExperimentView(
    /** 实验主键。 */
    long id,
    /** 证券代码。 */
    String symbol,
    /** 数据范围。 */
    String dataScope,
    /** 可用时点模式。 */
    String availabilityMode,
    /** 方法版本。 */
    String methodVersion,
    /** 输入语义指纹。 */
    String inputHash,
    /** 结果语义指纹。 */
    String resultHash,
    /** 实际保存时刻，上海时区。 */
    LocalDateTime createdAt,
    /** 详情结果，列表不加载。 */
    BacktestView result,
    /** 实际冻结输入来源，列表不加载；不能由当前通道配置推断。 */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        java.util.Set<String> inputSources) {
  /**
   * 显式转换元数据，不从当前数据库回填实验结果。
   *
   * @param row 已保存实验
   * @param result 详情结果或null
   * @return 公开实验视图
   */
  public static ExperimentView from(ValidationExperiment row, BacktestView result) {
    return new ExperimentView(
        row.getId(),
        row.getSymbol(),
        row.getDataScope(),
        row.getAvailabilityMode(),
        row.getMethodVersion(),
        row.getInputHash(),
        row.getResultHash(),
        row.getCreatedAt(),
        result,
        result == null
            ? null
            : SourceEvidence.inspect(
                    Jsons.read(
                        row.getSnapshotJson(), com.fasterxml.jackson.databind.JsonNode.class))
                .sources());
  }
}

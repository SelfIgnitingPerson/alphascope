package com.alphascope.backtest.dto;

import com.alphascope.signal.dto.RuleCatalog;
import java.time.Instant;
import java.util.Map;

/**
 * 不可变实验的完整输入容器；历史阈值保留在各信号证据中，当前配置单独标记。
 *
 * @author chenxuefeng
 */
public record ValidationSnapshot(
    /** 计算所需冻结输入，含修订ID。 */
    SignalEvaluationInput input,
    /** 捕获时当前规则清单，不冒充历史规则版本。 */
    RuleCatalog activeCatalog,
    /** 捕获时当前阈值；新规则修订另存其真实历史阈值。 */
    Map<String, Double> activeParameters,
    /** 实际捕获时刻。 */
    Instant capturedAt,
    /** 当前支持的计算方法。 */
    String methodVersion) {}

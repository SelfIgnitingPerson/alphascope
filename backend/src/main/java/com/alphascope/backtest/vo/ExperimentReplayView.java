package com.alphascope.backtest.vo;

/**
 * 完全使用冻结输入复算的核验结果，不读取当前行情或规则配置。
 *
 * @author chenxuefeng
 */
public record ExperimentReplayView(
    /** 实验主键。 */
    long experimentId,
    /** 冻结输入指纹是否一致。 */
    boolean inputVerified,
    /** 复算结果与原结果是否相同。 */
    boolean resultMatches,
    /** 原有结果指纹。 */
    String expectedHash,
    /** 本次复算结果指纹。 */
    String actualHash,
    /** 完整复算结果。 */
    BacktestView result) {}

package com.alphascope.intelligence.vo;

import com.alphascope.intelligence.dto.ForwardInput;
import java.time.LocalDateTime;

/**
 * 本人冻结窗口可复算证据，不向外部直接暴露数据库实体。
 *
 * @author chenxuefeng
 * @param id 已保存结果编号
 * @param recordedAt 首次评估时刻
 * @param input 完整冻结输入
 * @param outcome 完整结果
 * @param contentHash 输入和结果联合指纹
 */
public record ForwardEvidenceView(
    long id,
    LocalDateTime recordedAt,
    ForwardInput input,
    ForwardOutcome outcome,
    String contentHash) {}

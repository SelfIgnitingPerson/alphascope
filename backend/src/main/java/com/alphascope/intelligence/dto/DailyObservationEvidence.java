package com.alphascope.intelligence.dto;

import com.alphascope.intelligence.vo.HoldingResearchView;

/**
 * 实际形成的研究与执行假设一起冻结，不能用后来重算覆盖。
 *
 * @author chenxuefeng
 * @param research 当时完整研究
 * @param protocol 同时冻结的前瞻协议
 */
public record DailyObservationEvidence(HoldingResearchView research, ForwardProtocol protocol) {}

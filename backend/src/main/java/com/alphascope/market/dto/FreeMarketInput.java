package com.alphascope.market.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * 免费截面的原始证据批次；目录、行情和停复牌原文分别保留，不能混淆实际来源。
 *
 * @author chenxuefeng
 */
@Builder
public record FreeMarketInput(
    /** 实际收盘业务日，不接受用今天目录构造过去分母。 */
    LocalDate date,
    /** 新浪当前A股目录，已核对其独立总数及分页。 */
    List<JsonNode> roster,
    /** 东方财富行情目录，可能包含历史退市行，只匹配当前目录成员。 */
    List<JsonNode> quotes,
    /** 东方财富带起止时刻的停复牌原始记录。 */
    List<JsonNode> suspensions,
    /** 各页原文、目录总数、来源URL与实际采集时刻。 */
    String upstreamJson) {}

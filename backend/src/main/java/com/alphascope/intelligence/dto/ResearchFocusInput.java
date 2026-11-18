package com.alphascope.intelligence.dto;

import com.alphascope.intelligence.vo.HoldingResearchView;
import java.time.LocalDate;
import lombok.Builder;

/**
 * 两期自选研究的可比性输入，不含可由客户端传入的用户身份。
 *
 * @author chenxuefeng
 * @param current 所选日现存研究
 * @param previous 上一证券交易日的现存研究，不足时为空
 * @param previousDate 从完整日历核对的上期日期，不足时为空
 */
@Builder
public record ResearchFocusInput(
    HoldingResearchView current, HoldingResearchView previous, LocalDate previousDate) {}

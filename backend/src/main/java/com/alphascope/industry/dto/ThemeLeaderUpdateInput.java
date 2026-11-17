package com.alphascope.industry.dto;

import com.alphascope.industry.vo.ThemeLeadersView;
import jakarta.validation.constraints.NotNull;

/**
 * 显式修改当前核心目录，不修改历史披露成分。
 *
 * @author chenxuefeng
 * @param expectedRevision 页面读取的当前修订主键，初始源码目录为0
 * @param directory 新目录，核对日期必须为上海当前日期
 */
public record ThemeLeaderUpdateInput(long expectedRevision, @NotNull ThemeLeadersView directory) {}

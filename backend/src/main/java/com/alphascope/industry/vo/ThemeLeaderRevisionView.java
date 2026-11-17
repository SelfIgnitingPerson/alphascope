package com.alphascope.industry.vo;

/**
 * 管理员维护的当前修订和复核时效。
 *
 * @author chenxuefeng
 * @param revision 不可变修订主键，0代表源码初始目录
 * @param needsReview 距离核对日超过90天时提醒，不自动改变排名
 * @param directory 当前核心目录
 */
public record ThemeLeaderRevisionView(
    long revision, boolean needsReview, ThemeLeadersView directory) {}

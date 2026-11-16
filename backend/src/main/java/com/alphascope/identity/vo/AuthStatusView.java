package com.alphascope.identity.vo;

/**
 * 无需认证的登录页配置，不公开任何密钥或其他用户资料。
 *
 * @author chenxuefeng
 * @param mode LOCAL或PUBLIC
 * @param registrationEnabled 是否接受邀请注册
 * @param positionRiskEnabled 当前实例是否提供个人持仓功能
 */
public record AuthStatusView(
    String mode, boolean registrationEnabled, boolean positionRiskEnabled) {}

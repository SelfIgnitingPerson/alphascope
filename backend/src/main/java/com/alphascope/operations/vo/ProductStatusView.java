package com.alphascope.operations.vo;

import java.util.List;

/**
 * 管理员产品运行资料；登记不等于实际商业授权或历史效果认证。
 *
 * @author chenxuefeng
 * @param workspace 管理员自身的研究范围状态
 * @param licenses 实际来源许可登记
 * @param isolation 当前隔离协议
 * @param baselineSha256 运行JAR的数据库基线指纹
 * @param backupEnabled 是否启用自动备份
 * @param sourceLicenseEnforced 当前是否强制检查来源许可，与服务端实际准入规则一致
 * @param runtimeIssues 影响当前研究的运行问题，与发布待办分开
 * @param blockers 尚未满足的发布条件；不表示本机运行故障
 */
public record ProductStatusView(
    WorkspaceStatusView workspace,
    List<SourceLicenseView> licenses,
    String isolation,
    String baselineSha256,
    boolean backupEnabled,
    boolean sourceLicenseEnforced,
    List<String> runtimeIssues,
    List<String> blockers) {}

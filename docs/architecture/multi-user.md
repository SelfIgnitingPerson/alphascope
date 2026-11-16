# 多用户访问与数据隔离

当前隔离协议为TENANT_V1，仍使用Controller → Service接口 → ServiceImpl → Mapper/XML的模块化单体。每个账号对应独立研究空间；共享行情不重复按用户保存。账号与数据不能通过客户端传入tenantId切换。

## 两种运行方式

LOCAL是个人开发模式，只接受直接回环连接，拒绝携带Forwarded/X-Forwarded-For的代理请求，使用保留空间1。它不能作为公网鉴权方式。PUBLIC要求真实会话、明确HTTPS来源与Secure Cookie；生产配置固定PUBLIC，不允许通过反向代理IP获得本机权限。

PUBLIC登录使用数据库会话，令牌仅通过HttpOnly、Secure、SameSite=Strict Cookie发送。写入同时校验来源和CSRF请求头。密码使用带随机盐的PBKDF2，数据库只保存会话令牌摘要；登录失败按账号限制，密码计算还有全局并发上限。会话固定过期，退出、管理员停用或重置密码会撤销旧会话。邀请注册默认关闭，开启时需要单独的邀请口令。

普通用户只管理自己的自选、研究和任务。管理员管理账号、共享基础资料、来源许可与平台运行状态；管理员角色不等于自动跨用户查看私人报告。无权访问的私人记录按不存在处理。

## 数据分类

| 分类 | 表或范围 | 处理方式 |
| --- | --- | --- |
| 私有，17张 | watchlist、portfolio_account、portfolio_position、indicator_snapshot、signal_event、signal_revision、theme_score、market_score、analysis_report、report_input_snapshot、report_source、notification_log、job_execution、job_stage、validation_experiment、research_validation_task、service_alert | tenant_id参与查询、插入及自然唯一键；账户主键按空间与账户号组合 |
| 共享，20张 | instrument、adjustment_factor、daily_bar、daily_bar_audit、instrument_day_status、market_data_revision、market_factor_snapshot、trading_calendar、market_breadth_snapshot、market_observation_snapshot、earnings_forecast_snapshot、financial_report、valuation_snapshot、research_collection、theme、theme_research_cohort、theme_research_member、source_document、industry_metric、instrument_theme | 保存实际来源、日期和修订；共享人工写入仅管理员 |
| 控制，4张 | app_user、auth_session、auth_event、source_license | 仅通过身份、账号支持和许可服务访问，不能作为通用数据列表开放 |

MyBatis租户插件在SQL语法树层补充私有表过滤；未知表默认拒绝，PUBLIC未绑定身份的私人SQL拒绝执行。业务Mapper不能自行忽略隔离。唯一例外是PlatformCapacityMapper的显式平台数字汇总，只返回数量/字节数，不返回其他用户的内容；集成测试核验平台与个人计数差异。

JobWorker、ReportScheduler、验证队列、心跳和独立超时监控均恢复任务所属用户后工作，结束后释放上下文。前端切换账号会重新挂载工作台，过期会话移除旧私人页面。仅隐藏菜单不承担权限判断，后端独立校验。

## 新账号流程

登录或邀请注册 → 空白自选 → 搜索有效A股 → 选择并登记 → 可选首次日报初始化 → 任务进度 → 自己的报告。目录服务重新核对代码与市场；无法查询上游时保留已登记证券供选择，并显示实际状态。

当前开发库原有私人数据显式归属空间1，不复制给新用户。空库安装在PUBLIC首次启动时去除空间1的示例自选；已有开发库不重放种子或删除原自选。保留账号初始为PENDING，无默认可登录密码；首次PUBLIC管理员需在本机配置中提供，不能直接把私人开发库对外开放。

## 授权与容量

来源许可按来源及COLLECT、DISPLAY、EXPORT、DERIVED_REPORT分别登记有效区间和凭证。PUBLIC强制准入；管理员登记只表示DECLARED_BY_OPERATOR，不代表系统已经核验合同或自动取得授权。缺少许可允许登录、配置和查看自己的任务元数据，实际收集、研究展示和导出仍拒绝。

单用户及全平台分别限制活动任务和证据容量，自选与账号也有上限。新增前在短事务中核对，拒绝超额而不删除冻结历史。认证审计按保留期分批清理。可选运维Webhook只向显式允许的HTTPS主机发送通用状态，不携带自选、行情或许可凭据；未配置不发送。

## 验收与发布

隔离MySQL集成测试覆盖跨用户读写、同自然键、后台领取、角色、CSRF、授权有效期与配额；独立HTTPS浏览器流程覆盖注册、登录、自选初始化、双账号及退出。验收结果见[当前验收](../verification/README.md)。这不替代正式部署后的渗透和持续容量测试。

生产同源JAR、网关与TLS配置见[部署入口](../../deploy/README.md)。首次实际发布后冻结V1，后续结构采用增量迁移；开发期直接同步不是生产升级方案。回滚只能使用兼容TENANT_V1的二进制。

# 文档目录

本目录的架构、接口、数据字典和运行手册描述**当前版本 0.1.0（REPORT_V11 / 单份数据库基线）**。源码、启动方式及文档只维护这一套实现；[版本与验收](verification/README.md)集中说明发布状态及已验证边界。

## 按任务查阅

| 任务           | 文档                                                       | 内容边界                                   |
| -------------- | ---------------------------------------------------------- | ------------------------------------------ |
| 第一次运行     | [项目首页](../README.md)                                   | 环境要求、启动步骤、服务地址               |
| 配置环境       | [配置说明](runbook/configuration.md)                       | 配置文件职责、变量、默认值、可选功能       |
| 阅读工程       | [项目约定](architecture/project-conventions.md)            | 目录、分层、依赖、注释与变更规范           |
| 学习产品操作   | 工作台“帮助中心 → 使用说明”（`/#/help`）                   | 产品目的、六步上手与模块串联               |
| 阅读状态与术语 | 工作台“帮助中心 → 术语与状态”（`/#/terms`）                | 规则状态、风险级别及可搜索的股票术语       |
| 理解业务       | [架构与口径](architecture/overview.md)                     | 数据流、指标、报告、任务及能力边界         |
| 多用户与权限   | [多用户设计](architecture/multi-user.md)                   | 账号、私人SQL、后台归属、授权和容量        |
| 生产交付       | [生产入口](../deploy/README.md)                            | 同源JAR、TLS网关、PUBLIC配置与升级约束     |
| 修改后端       | [后端导航](../backend/README.md)                           | 模块和关键代码入口                         |
| 修改前端       | [前端导航](../frontend/README.md)                          | 页面、数据流、样式和测试                   |
| 构建和验收     | [构建与测试](runbook/build-and-test.md)                    | POM 关系、版本管理、测试、产物与 CI        |
| 使用数据库 SQL | [数据库入口](../database/README.md)                        | 唯一初始化SQL、空库安装和已有库处理        |
| 查询表结构     | [现行数据字典](database/schema.md)                         | 41 张表的用途、关键字段和约束              |
| 查看市场与预测 | [沪深观察与机构预测](runbook/market-observation.md)        | 分项口径、实际观测时点、缓存及访问限制     |
| 理解五维评分   | [五维规则研究](runbook/research-scoring.md)                | 公式、完整分母、代理范围和总分准入         |
| 验证历史规则   | [历史研究验证](runbook/historical-validation.md)           | 成熟样本、时点、基准和留出区间             |
| 理解研究建议   | [同业联动与建议](runbook/theme-research.md)                | 样本来源、收益统计、核查条件和量子类比边界 |
| 接口联调       | [REST API](api/endpoints.md)                               | 查询、分页、写操作、202 异步契约           |
| 检查行情       | [数据源手册](runbook/data-providers.md)                    | 通道能力、缓存、复权、缺口与诊断           |
| 检查运行状态   | 工作台“运行检查”及[运行维护](runbook/local-development.md) | 行情日期、备份证明、第二存储状态           |
| 维护本机服务   | [运行维护](runbook/local-development.md)                   | 备份、恢复、升级和故障排查                 |
| 使用开发脚本   | [脚本目录](../scripts/README.md)                           | 命令、前提和是否写业务库                   |
| 查看当前版本   | [版本与验收](verification/README.md)                       | 当前基线、验收结果与限制                   |
| 判断产品化条件 | [产品化检查](verification/product-readiness.md)            | 当前产品实现、本机验收及外部待验收条件     |

近两个月量价、压力区间、止损参考与条件式减仓研究见[量价方法](runbook/price-volume.md)。旧报告保留冻结内容，新方法通过新修订展示。

## 维护规则

- 一个主题只在一份现行文档中完整说明，其他入口使用链接。
- 接口以 Controller / DTO / VO 和运行中的 OpenAPI 为核对依据。
- 数据库定义只维护 [V1\_\_baseline.sql](../backend/src/main/resources/db/migration/V1__baseline.sql)，不保留阶段升级链或第二份汇总DDL。
- 后端版本以[根 POM](../pom.xml)及其 Spring Boot 父配置为准；前端版本以 `package.json`、`package-lock.json` 为准。
- 验收数量和任务编号集中放在版本说明，运行手册不积累阶段变更记录。
- 日志、截图和可选数据库备份放在 `.runtime/`，不写入当前说明或源码包。

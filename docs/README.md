# 文档目录

文档只保留产品使用、开发、数据口径和部署维护所需内容。首次使用可直接打开工作台“帮助中心 → 使用说明”；股票术语和状态解释在“术语与状态”中查询。

## 使用与运行

| 需要做什么 | 入口 |
| --- | --- |
| 安装、启动和了解目录 | [项目首页](../README.md) |
| 配置环境变量 | [配置说明](runbook/configuration.md) |
| 使用本机MySQL | [本机MySQL](runbook/local-mysql.md) |
| 用Docker运行应用 | [本机Docker](../deploy/local-docker.md) |
| 部署多用户服务 | [生产部署](../deploy/README.md) |
| 维护、备份和排查故障 | [运行维护](runbook/local-development.md) |
| 检查行情来源和错误 | [数据源手册](runbook/data-providers.md) |

## 开发与数据库

| 需要了解什么 | 入口 |
| --- | --- |
| 模块、代码入口和页面结构 | [后端导航](../backend/README.md) · [前端导航](../frontend/README.md) |
| 开发规范与业务架构 | [项目约定](architecture/project-conventions.md) · [架构与口径](architecture/overview.md) |
| 账号、权限与数据隔离 | [多用户设计](architecture/multi-user.md) |
| 数据库初始化和字段含义 | [数据库入口](../database/README.md) · [数据字典](database/schema.md) |
| 接口和分页协议 | [REST API](api/endpoints.md) |
| 构建、测试和脚本 | [构建与测试](runbook/build-and-test.md) · [脚本说明](../scripts/README.md) |

## 研究方法

| 功能 | 说明 |
| --- | --- |
| 市场观察和机构预测 | [市场观察](runbook/market-observation.md) · [五维评分](runbook/research-scoring.md) |
| 个股短、中、长期研究 | [持有与买卖研究](runbook/holding-research.md) · [量价方法](runbook/price-volume.md) |
| 产业与同业联动 | [主题研究](runbook/theme-research.md) |
| 保存计划并追加复核 | [研究计划](runbook/research-plans.md) |
| 检验历史规则与复算 | [历史验证](runbook/historical-validation.md) |

一个主题只维护一份现行说明；数据库只维护一份[V1基线](../backend/src/main/resources/db/migration/V1__baseline.sql)。文档描述功能和操作方法，测试输出、日志和截图不进入产品文档或源码包。

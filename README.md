# AlphaScope · A 股研究工作台

用于市场洞察与自选股票研究：先核对市场与产业背景，再按短期、中期、长期分别制定有证据的买入、加仓、减仓和退出条件，保存个人研究计划，随后追加复核，并用冻结报告与历史验证持续检验。支持本机个人模式及多用户 PUBLIC 模式。

首次使用从侧栏“帮助中心 → 使用说明”开始，按六步流程操作。长期估值和经营论点尚未核实时不生成合理买价；行情条件和个人适用前提分别展示。

**版本：0.1.0。**

技术栈：Java 21 / Spring Boot / MyBatis / Flyway / MySQL 8；Vue 3 / TypeScript / Vite。

## 本机启动

准备 Java 21、Node 22 或 24+，以及已启动的本机 MySQL 8，在项目根目录执行。日常运行不需要 Docker：

1. 按[本机MySQL说明](docs/runbook/local-mysql.md)创建空库与专用账号；已有库不重复初始化。
2. 首次运行：`cp .env.example .env`，填写MySQL地址、端口、库名和专用账号密码。已有配置保留。
3. 首次空库先执行 `bash scripts/tools.sh install-schema` 安装表结构，再用 `bash scripts/run-backend.sh` 启动后端；已有库不重复初始化。
4. 另开终端执行 `npm --prefix frontend ci` 和 `npm --prefix frontend run dev`。

打开[工作台](http://127.0.0.1:5173/)，搜索并添加自选证券，选择首次初始化，再到任务中心生成日报。

[健康检查](http://127.0.0.1:8080/actuator/health) · [本机接口文档](http://127.0.0.1:8080/swagger-ui.html) · [完整配置](docs/runbook/configuration.md)

也可以把应用放进Docker，继续连接上述本机MySQL：停止原Java与Vite后执行`bash scripts/run-docker.sh up`，仍访问`http://127.0.0.1:5173/`。首次构建后无需单独启动前后端，详见[本机Docker启动](deploy/local-docker.md)。

## 工程结构

```text
alphascope/
├── backend/             Java 业务、测试、Mapper XML、唯一 SQL 基线
├── frontend/            Vue 页面、组件、业务 API、样式与测试
├── database/            数据库使用入口，不重复保存 SQL
├── docs/                架构、接口、数据字典与运行手册
├── scripts/             启动、校验、维护与源码打包
├── deploy/              本机应用 Compose、生产 Compose、网关与环境模板
├── pom.xml              Maven 聚合入口和公共版本管理
├── docker-compose.yml   可选开发数据库（3307，不影响本机3306）
├── Dockerfile           同源生产 JAR 镜像
└── .gitlab-ci.yml        GitLab 测试、审计与交付流水线
```

本机配置 `.env`、运行资料 `.runtime/`、交付包 `artifacts/`、依赖及编译目录均不属于源码。不要把这些文件复制到业务目录。

## 阅读入口

| 目的                   | 入口                                                                        |
| ---------------------- | --------------------------------------------------------------------------- |
| 了解全貌与阅读顺序     | [文档目录](docs/README.md)                                                  |
| 阅读后端与业务调用链   | [后端导航](backend/README.md)                                               |
| 阅读前端与页面数据流   | [前端导航](frontend/README.md)                                              |
| 查询开发规范           | [项目约定](docs/architecture/project-conventions.md)                        |
| 初始化数据库或查询字段 | [数据库入口](database/README.md) · [数据字典](docs/database/schema.md)      |
| 联调接口               | [REST API](docs/api/endpoints.md)                                           |
| 理解真实数据与缺项     | [数据源手册](docs/runbook/data-providers.md)                                |
| 构建、测试和生产交付   | [构建与测试](docs/runbook/build-and-test.md) · [生产部署](deploy/README.md) |

## 常用命令

| 操作               | 命令                                     |
| ------------------ | ---------------------------------------- |
| 结构、文档链接检查 | `node scripts/quality/check-project.mjs` |
| 日常联合校验       | `bash scripts/verify.sh`                 |
| 只读运行诊断       | `bash scripts/tools.sh check`            |
| 生成唯一源码包     | `bash scripts/tools.sh package`          |

维护命令是否写库、完整回归和生产构建步骤统一见[脚本说明](scripts/README.md)和[构建与测试](docs/runbook/build-and-test.md)。

## 数据与产品边界

真实模式失败后不会回退模拟。免费当前截面采用新浪在市目录与东方财富日期行情、明确停牌交叉核对；每日保存证据，历史名单不倒填。交易日历、收盘补跑和有限重试见[配置手册](docs/runbook/configuration.md#连续日线更新)。

默认研究自选，不评估个人持仓，不自动交易。缺少完整全A证明、历史均线广度等条件时，总评级保持 `UNKNOWN`；规则效果仍需历史验证。

PUBLIC 模式包含登录会话、用户资料隔离和逐来源用途准入。数据源须按实际取得的采集、展示、派生和导出权限配置，部署步骤见[生产部署](deploy/README.md)。

压力区间、结构止损参考与条件式建仓、加仓、减仓方案见[两月量价方法](docs/runbook/price-volume.md)，由新报告修订冻结，旧报告不补算。

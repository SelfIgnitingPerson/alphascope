# 配置说明

## 配置放在哪里

| 文件                                                                            | 职责                                  | 是否保存本机密钥           |
| ------------------------------------------------------------------------------- | ------------------------------------- | -------------------------- |
| [根目录 .env.example](../../.env.example)                                       | 可提交的配置模板                      | 否，只有占位符             |
| 根目录 `.env`                                                                   | 本机变量，由启动/开发工具脚本读取     | 可以；不提交、不进入源码包 |
| [application.yml](../../backend/src/main/resources/application.yml)             | 后端默认值及环境变量映射              | 否                         |
| [docker-compose.yml](../../docker-compose.yml)                                  | 可选开发容器、3307端口、数据卷                | 引用 `.env`                |
| [frontend/.npmrc](../../frontend/.npmrc)                                        | 锁定依赖安装的 peer 解析方式          | 否                         |

Compose 自动读取根目录 `.env`，普通终端和直接执行的 `java` / Maven 不会自动读取它。推荐使用 `bash scripts/run-backend.sh` 和 `scripts/tools.sh`，脚本会加载配置。`.env` 使用脚本可读取的 `KEY=value` 格式；值含空格时加引号。

时间序列化统一在 `application.yml` 设置上海时区及ISO日期格式，无需额外 profile。

## 常用变量

| 变量                      | 模板值 / 默认行为                 | 说明                                                   |
| ------------------------- | --------------------------------- | ------------------------------------------------------ |
| `JAVA_HOME`               | 未设置时检测已安装 JDK            | 必须为 Java 21；不把开发者机器路径写入源码             |
| `MYSQL_HOST` | `127.0.0.1` | MySQL主机，默认本机TCP连接 |
| `MYSQL_PORT` | `3306` | MySQL端口；可选容器默认3307需显式切换 |
| `MYSQL_DATABASE` | `alphascope` | 业务数据库 |
| `MYSQL_USER` | `alphascope` | 应用专用账号，不使用root运行 |
| `MYSQL_PASSWORD`          | 模板占位符，首次必须替换          | 应用数据库账号密码                                     |
| `MYSQL_ROOT_PASSWORD`     | 默认不配置，仅可选容器填写          | 仅可选容器需要的管理员密码，日常本机运行无需配置                                   |
| `MARKET_PROVIDER`         | 模板 `multi`；后端未配置时 `demo` | `multi`、`baostock`、`sina`、`tushare` 或明确的 `demo` |
| `BAOSTOCK_DAILY_BUDGET` | `40000` | BaoStock本机上海自然日请求预算，1—45000；含登录、分页、重试，重启不重置 |
| `MARKET_CHANNELS`         | `exchange-calendar,baostock,sina,eastmoney,tushare`           | 多通道顺序；无 Token 时跳过 Tushare                    |
| `TUSHARE_TOKEN`           | 空                                | 可选，接口能力取决于账号权限                           |
| `MARKET_CACHE_MINUTES` | `1440` | 普通缓存TTL；已结束日期的完整真实历史可校验复用，`0`强制重采 |
| `MARKET_COOLDOWN_SECONDS` | 后端默认 `30`                     | 按供应商及能力分别冷却                                         |
| `AI_CHAT_MODEL`           | `none`                            | 禁用模型；设为 `openai` 时启用模型接入                 |
| `OPENAI_API_KEY`          | 空                                | 启用模型时填写                                         |
| `OPENAI_BASE_URL`         | `https://api.openai.com`          | 模型服务地址                                           |
| `OPENAI_MODEL`            | `gpt-4o-mini`                     | 已配置服务中可用的模型名称                             |
| `SCHEDULER_ENABLED`       | `false`                           | 是否启用定时提交日报、重试和周报                       |
| `POSITION_RISK_ENABLED` | `false` | 默认自选研究；仅显式设为 true 才评估手工持仓集中度 |
| `FINANCIAL_SYNC_ENABLED` | `true` | 真实模式自动采集免费财报，演示模式不访问真实财报接口 |

默认数据库与专用账号均为 `alphascope`，可通过上述变量调整。`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 完整属性优先；维护CLI与应用使用相同优先级。正常启动只连接配置中的数据库，不启动Docker。建库及切换见[本机MySQL](local-mysql.md)。修改环境变量不会自动修改已有账号密码。

## 后台队列与定时触发的区别

后台队列消费默认启用，页面提交的任务会执行；`SCHEDULER_ENABLED=false` 只关闭日历时间触发，不关闭手动任务。

| 高级 Spring 属性                       | 默认   | 用途                                                       |
| -------------------------------------- | ------ | ---------------------------------------------------------- |
| `alphascope.jobs.worker-enabled`       | `true` | 是否启动后台消费与心跳；集成测试会关闭它以确定性地执行任务 |
| `alphascope.scheduler.enabled` | false | 收盘定时提交及启动/休眠恢复后的补跑统一开关 |

高级属性可用 Spring 启动参数或标准环境变量设置，例如 `ALPHASCOPE_JOBS_WORKER_ENABLED`。这些是现有 Spring 配置项，不需要新增一套配置文件。

## 可选功能

- 免费行情和通道限制：[数据源手册](data-providers.md)。单独启用新浪不能补足指数成交额。
- 未启用模型时仍生成包含实际个股事实的规则版报告；缺证据不会自动产生全市场评级。

环境检查、构建和测试见[构建与测试](build-and-test.md)，端口、备份和排错见[运行维护](local-development.md)。

## 任务时限、历史验证与备份

| 环境变量 | 默认值 | 含义 |
| --- | --- | --- |
| `JOB_STAGE_TIMEOUT` | `2h` | 每阶段硬截止，允许1秒至24小时；与心跳独立 |
| `BACKTEST_MINIMUM_SAMPLES` | `20` | 每条规则、每个窗口低于此值显示小样本；不是方法有效性门槛 |
| `BACKUP_ENABLED` | `false` | 显式开启应用内定时备份及隔离恢复验证 |
| `BACKUP_CRON` | `0 0 2 * * *` | 上海时区、Spring六段Cron；在.env中须给含空格的值加引号 |
| `BACKUP_RETENTION_DAYS` | `14` | 至少7天，始终保留最近两份已恢复验证备份 |
| `ALPHASCOPE_LOG_DIR` | `.runtime/logs` | 滚动业务日志目录 |

可用 Spring 属性 `alphascope.jobs.stage-timeouts.MARKET_OBSERVATION=4h` 覆盖特定阶段，键须为 `PipelineStage` 中的有效代码。集中配置由 `AlphaScopeProperties` 启动校验；非法数据模式、组合中混入DEMO、负缓存、错误阶段名、无效时限或Cron均使启动失败，不静默采用另一套值。

密钥不进入该配置对象的toString。自动备份要求本机 Docker 已运行且存在 `mysql:8.0.40` 镜像；应用关闭时不会执行。错过计划会在启动60秒后、随后每小时检查并补一次，当天失败不反复补做。实际操作见[运行维护](local-development.md)。

## 独立执行器与第二存储

任务消费、心跳、截止监控、备份和普通计划提交分别使用单独的单线程调度器；业务仍是单消费者，避免为了并行而冲击行情上游。数据库连接取得超时10秒、连接校验5秒，生产JDBC连接/读取超时10/30秒，MyBatis默认语句超时30秒。截止监控先对到期任务发出本地中断，再记录数据库意图；仍需等待底层I/O或事务完成收尾，不承诺实时强制杀线程。

`BACKUP_SECONDARY_DIRECTORY`可指定已存在的第二磁盘或挂载目录；程序只在其中维护alphascope-backups子目录，复制压缩文件、清单和恢复验证标记，核对字节指纹后原子提交。路径为空则运维页显示未配置。同文件系统副本明确显示局限，不称作异地容灾。模板默认空，本机按用户要求未配置且BACKUP_ENABLED=false。

前端默认允许localhost/127.0.0.1的5173来源；自定义端口可设置Spring属性alphascope.web.allowed-origins或标准环境变量ALPHASCOPE_WEB_ALLOWED_ORIGINS，不能为了测试开放任意来源。浏览器集成测试自动分配隔离端口并只允许该确切来源。

## 后台验证配置

alphascope.validation配置maximum-symbols（默认10）、maximum-active（20）、maximum-signals（50000）、maximum-evidence-bytes（33554432）、timeout-seconds（900）、stale-seconds（120）。启动校验合法范围；放大上限不会弥补缺失数据。验证与行情消费者分别调度，统一受alphascope.jobs.worker-enabled控制；关闭消费者时排队请求不会执行。

验证的进度回调只更新本机上下文，由独立监控线程持久化，不在一致读事务里写队列状态。完整证据受字节上限控制，不能因超过大小静默截断。

## PUBLIC身份、配额和授权

生产使用deploy/production.env.example、application-production.yml及同源TLS网关；不能将根目录LOCAL配置直接暴露。PUBLIC固定Secure Cookie，只接受明确HTTPS来源。首次管理员密码至少12字符，邀请注册开启时邀请口令至少24字符；均由本机配置提供，不发到聊天或提交源码。PUBLIC未提供有效管理员初始配置时拒绝启动，不创建默认密码。

| 属性或变量 | 默认 | 用途 |
| --- | --- | --- |
| ACCESS_MODE | LOCAL | LOCAL允许代理转发，未登录使用保留管理员空间；PUBLIC使用会话和SQL隔离 |
| PUBLIC_ORIGINS | 开发时本机5173 | 完整来源列表，PUBLIC必须HTTPS且不能通配 |
| ADMIN_USERNAME / ADMIN_PASSWORD | 空 | 首次PUBLIC管理员配置，已有账号不会因更换环境值重置 |
| REGISTRATION_ENABLED / REGISTRATION_INVITATION | false / 空 | 邀请注册，不允许匿名自行获得管理员角色 |
| alphascope.access.session-seconds | 28800 | 会话固定有效8小时 |
| alphascope.access.maximum-sessions | 5 | 单账号未过期会话上限 |
| alphascope.access.maximum-watchlist | 30 | 单用户自选上限 |
| alphascope.access.maximum-accounts | 1000 | 账号总上限 |
| alphascope.product.maximum-jobs | 20 | 单用户活动日报/周报上限 |
| alphascope.product.maximum-platform-jobs | 200 | 平台活动日报/周报上限 |
| alphascope.product.maximum-platform-validations | 100 | 平台活动研究验证上限 |
| alphascope.product.maximum-evidence-bytes | 1073741824 | 每用户证据预算1GiB |
| alphascope.product.maximum-platform-evidence-bytes | 10737418240 | 平台证据预算10GiB |
| alphascope.product.enforce-source-license | false，PUBLIC强制 | 逐来源用途和有效期准入 |
| alphascope.product.monitor-enabled | false，生产true | 按用户检查任务和新鲜度，保存告警状态 |
| alphascope.product.authentication-retention-days | 30 | 认证审计分批清理保留期，不删除研究证据 |

高级Spring属性可按标准环境变量形式设置，例如ALPHASCOPE_PRODUCT_MAXIMUM_JOBS。PUBLIC身份来源统一使用alphascope.access.origins；旧开发CORS配置不作为PUBLIC许可列表。

可选alphascope.product.alert-webhook-url、alert-webhook-token和alert-webhook-hosts须同时按需要配置；地址限HTTPS和明确允许主机，空地址不发送。发送内容仅空间数字标识、通用故障代码与时间，不含证券、报告或许可凭据。当前未配置也未发送，状态页仍可读取持久告警提示。

MySQL连接初始化执行SET time_zone='+08:00'，使数据库默认时间与Java业务时区一致；否则认证限流和有效期比较可能出现跨时区偏差。来源授权登记在数据库，不通过一个布尔环境变量伪造已获得供应商许可。

## 连续日线更新

MARKET_CHANNELS建议为exchange-calendar,baostock,sina,eastmoney,tushare。exchange-calendar仅提供有官方来源的2024—2026年日历，资源位于backend/src/main/resources/market/exchange-calendar.json；原文URL、发布日期、复核日及SHA256可追溯。新年度须复核交易所通知后更新，未知年度会让路由尝试其他日历来源，不能静默按星期补齐。

SCHEDULER_ENABLED=true统一开启每日16:10、周五18:00及启动60秒后每30分钟的补跑。盘中选择前一已收盘交易日，节假日选择最近开市日；窗口采集补齐历史行情，补跑不批量伪造休眠期间已生成的日报。空白自选不提交自动任务。原独立catchup-enabled入口已合并，不再维护第二份启动补跑逻辑。

每用户、类型、日期、数据源范围最多4次自动尝试，手动历史也占自动预算，失败结束至少30分钟后重试。成功、跳过、运行中和用户取消不会被自动重建；预算保存在任务历史中，重启不清零。明确的手动重试不受该自动预算限制。服务/电脑关闭时无法运行，下一次启动才补交最近收盘日；持续运行仍是按时采集的前提。

## 免费截面的归档与访问控制

组合中启用eastmoney即可同时提供指数与收盘研究截面；研究截面还依赖SINA目录，因此PUBLIC要分别具备SINA与EASTMONEY的COLLECT许可。该功能不需要Tushare Token或Python环境。自动日报继续使用同一SCHEDULER_ENABLED入口，成功原始截面按日期保存并复用；当前免费源不回溯过去目录。

403/429会将一小时冷却终点保存在.runtime/eastmoney-market-cooldown.txt。不要删除标记、切换主机或增加并发规避访问限制。未归档历史显示NOT_ARCHIVED，未声明的复权能力显示UNSUPPORTED；自然积累完整交易日后才满足量能窗口，日期/状态缺失不填0。详细口径见[免费截面](data-providers.md#免费全市场截面与自然归档)。


本机CLI入口由ALPHASCOPE_API_BASE指定，默认http://127.0.0.1:5173，支持Docker或前端同源代理。只启动后端时显式设http://127.0.0.1:8080。该配置只用于本机工具，拒绝远程主机、URL凭据、附带查询与路径前缀；不改变服务监听地址、PUBLIC认证或CSRF。

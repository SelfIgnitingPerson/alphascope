# 开发脚本

所有命令从项目根目录执行。脚本统一读取 `.env` 并检查 Java 21；前端命令和完整测试前提见[构建与测试](../docs/runbook/build-and-test.md)。

## 常用入口

| 命令                                                            | 用途                                             | 是否写业务库                               |
| --------------------------------------------------------------- | ------------------------------------------------ | ------------------------------------------ |
| `bash scripts/run-backend.sh`                                   | 启动后端及 Flyway                                | 是，迁移与正常业务写入                     |
| `bash scripts/run-docker.sh up`                                 | Docker应用连接本机MySQL，5173同源入口             | 是，正常启动与业务写入；不创建MySQL容器    |
| `bash scripts/verify.sh`                                        | 格式、Java普通测试、前端测试/构建；默认不依赖Docker | 默认普通检查；启用容器测试时只写隔离库     |
| `bash scripts/tools.sh check`                                   | 检查本机 API 与已入库真实行情                    | 否                                         |
| `bash scripts/tools.sh probe baostock 2026-10-30`               | 直接请求供应商；可换 sina 或调整日期             | 否                                         |
| `bash scripts/tools.sh verify-real 2026-10-30`                  | 提交日报，验证真实数据、历史长度及幂等           | **是**                                     |
| `bash scripts/tools.sh replay 2026-10-29 2026-10-30`            | 按明确日期提交历史任务                           | **是**                                     |
| `bash scripts/tools.sh verify-theme-sources`                    | 下载两份固定原始披露，验证PDF完整性和SHA-256     | 否，只读网络，不写业务库                   |
| `bash scripts/tools.sh backup`                                  | 一致性备份并实际隔离恢复验证                     | 否，只读业务库，写受限备份及专用容器       |
| `bash scripts/tools.sh verify-backup`                           | 再次实际恢复验证最新托管备份                     | 否，写专用隔离容器                         |
| `bash scripts/tools.sh upgrade-schema`                          | 备份验证后显式同步当前受控新增结构               | **是，仅受控DDL，不更新业务数据**          |
| `bash scripts/tools.sh validate-research 2024-10-14 2026-10-30` | 提交后台验证、轮询并导出完整研究证据             | 是，仅验证队列与证据，不改行情/信号/报告   |
| `bash scripts/tools.sh reproduce-research-validation`           | 从冻结输入离线复算，不联网                       | 否                                         |
| `bash scripts/tools.sh sync-theme-research YYYY-MM-DD`         | 登记披露样本并补齐成员行情、指标和主题观察       | 是，仅共享主题数据，不改自选或生成报告     |
| `bash scripts/tools.sh package`                                 | 打包源码、文档和构建资料                         | 否，只写 `artifacts/alphascope-source.zip` |

`check` 需要后端已启动且具备真实数据；`probe` 只需供应商网络，不依赖数据库。verify-real / replay 接收任务后轮询终态，最多等待十分钟；等待超时不会取消后台任务。

`VERIFY_DOCKER_CONFIG=true bash scripts/verify.sh`显式增加Compose配置检查；完整隔离数据库与浏览器测试仍需要Docker。本机业务连接见[本机MySQL](../docs/runbook/local-mysql.md)。

## 内部实现

| 位置                                                                                 | 职责                                           |
| ------------------------------------------------------------------------------------ | ---------------------------------------------- |
| [tools.sh](tools.sh)                                                                 | 校验工具参数，调用 Java `ProjectTools`         |
| [lib/environment.sh](lib/environment.sh)                                             | 加载 `.env`、定位根目录、检测 JDK              |
| [ThemeResearchTool.java](../backend/src/main/java/com/alphascope/tools/ThemeResearchTool.java) | LOCAL真实模式主题维护、日期与活动任务检查     |
| [ProjectTools.java](../backend/src/main/java/com/alphascope/tools/ProjectTools.java) | 命令分发、诊断与来源核验                       |

源码包排除密钥、备份、运行日志和依赖/编译目录。唯一SQL及已有开发库同步规则见[数据库入口](../database/README.md)。

`bash scripts/tools.sh warm-market-history YYYY-MM-DD`：维护用单会话历史缓存回补，会写业务库。只在无活动研究任务且上游未拒绝访问时执行；不会启动额外队列或Web服务。访问受限时先停止，不能靠反复执行恢复上游权限。

`bash scripts/tools.sh upgrade-schema-direct`用于显式选择不创建备份的开发库同步；核对BASELINE/1、无活动任务及原字段指纹后执行受控DDL。是否启用自动备份由当前环境配置决定。

研究验证CLI与页面使用同一持久后台队列，支持all/technical/market范围，完整链路协议RESEARCH_AUDIT_V2；窗口、容量、取消和证据下载见[历史验证](../docs/runbook/historical-validation.md)。

## 发布质量工具

- `node scripts/quality/check-project.mjs`：只读核对唯一 SQL、源码临时残留和 Markdown 本地链接，日常校验和 GitLab CI 共用。

- `bash scripts/quality/security-audit.sh`：生成SBOM，执行npm及OSV依赖审计；需要网络和Docker，失败返回非零，不写业务库。
- `node scripts/quality/release-manifest.mjs`：对现有生产JAR、SQL、锁文件和Dockerfile取SHA256，可从SBOM生成许可证清单；不读取.env，不部署或推送。
- `.gitlab-ci.yml`：受信任alphascope Runner执行完整测试、审计和打包；

维护CLI默认连接个人本机服务，不携带PUBLIC会话；PUBLIC操作请通过登录后的产品界面和有会话/CSRF的API完成。

日线更新由SCHEDULER_ENABLED统一开启，启动补跑、收盘提交及有限重试复用ScheduledUpdateService和持久队列。日历的官方公告和覆盖年份随唯一资源market/exchange-calendar.json维护，更新后需验证节假日、调休周末及未核验年份拒绝行为。

源码打包由 `tools/SourceArchive` 单独维护白名单和排除规则，遍历前剪枝依赖/编译目录，不跟随符号链接；先完成临时包再替换唯一源码 ZIP。`ProjectTools` 负责命令分发，`ResearchValidationTool` 负责冻结研究导出与复算，数据库维护继续委托 `DatabaseMaintenance`。

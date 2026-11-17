# 数据库入口

当前只维护[唯一V1基线](../backend/src/main/resources/db/migration/V1__baseline.sql)：46张业务/控制表、444个中文字段注释，含外键、索引及开发基础证券/主题种子。Flyway历史表另计；不含真实行情、持仓或示例报告。逐表字段见[数据字典](../docs/database/schema.md)，隔离分类见[多用户设计](../docs/architecture/multi-user.md)。

默认使用本机MySQL 8，日常运行不依赖Docker。建库、专用账号、连接配置和已有数据迁移见[本机MySQL](../docs/runbook/local-mysql.md)。

## 空库安装

配置空数据库后，先由一次性管理员连接运行 `bash scripts/tools.sh install-schema`，由Flyway执行唯一V1并登记版本1，再启动普通账号服务。管理员连接只安装DDL和预算触发器，不进入日常后端，也不修改MySQL全局权限。管理员配置可使用 `MYSQL_MIGRATION_USER` / `MYSQL_MIGRATION_PASSWORD` 或本机已有 `LOCAL_MYSQL_ADMIN_USER` / `LOCAL_MYSQL_ADMIN_PASSWORD`，只写本机配置。不要在已有表的库重复导入，不另存一份汇总DDL。app_user保留空间1为PENDING，不提供默认可登录密码。PUBLIC首次管理员由本机环境配置建立，新用户始终空白自选；公共基础数据共享，20张私人表以tenant_id隔离。

## 已有开发库

当前开发库登记BASELINE/1。开发期修改基线不自动清库、重放种子或修改校验和，必须明确核对后同步受控结构。旧私人数据归属空间1，不复制到其他账号；未知recorded_at仍为NULL，旧报告冻结内容不重算。

需要不创建备份地同步开发库时，开发工具`bash scripts/tools.sh upgrade-schema-direct`会校验项目数据库、BASELINE/1和无活动任务，核对原字段内存指纹，只补受控结构、租户字段及对应索引。它不导出业务库，不关闭Flyway校验，不作为生产自动升级入口。真实旧单用户结构升级另有隔离MySQL测试。

## 正式发布后的约束

首次正式发布后冻结V1，后续使用不可变增量迁移。发布前用上一正式结构验收升级，只回滚至隔离协议和结构兼容的二进制。当前单份V1是尚未定版阶段的约定，不能通过修改已发布SQL校验和管理客户数据库。

算法通过ADJ_V2 / RULE_V2 / TECHNICAL_V2显式区分；新增表和可复算证据不代表研究方法已证明有效。

## 写入保护与执行历史

`storage_budget` 按平台和用户记录逻辑正文占用及任务预留；任务执行历史由 `job_attempt_history` 保存。表的完整职责见数据字典。

全部27张含JSON或长正文的表由81个数据库触发器统一维护平台及所属用户用量，活动任务另有预留。超额写入整个事务回滚；终态释放预留，不自动删除旧证据。首次维护重建一次台账，日常查询不扫描全部正文。字段、触发器和字典一致性由 `node scripts/quality/schema-docs.mjs` 检查；修改基线后使用 `--write` 同步生成，再运行迁移测试。

任务重试沿用job_execution.id；重试前完整状态和阶段归档到job_attempt_history并校验指纹，再清空当前阶段重新执行。新表承担历史审计，不在主列表制造新任务。旧版已存在的retry_of关联仍可查看，已冻结报告和通知关联不迁号、不删除。

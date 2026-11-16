# 数据库入口

当前只维护[唯一V1基线](../backend/src/main/resources/db/migration/V1__baseline.sql)：41张业务/控制表、406个中文字段注释，含外键、索引及开发基础证券/主题种子。Flyway历史表另计；不含真实行情、持仓或示例报告。逐表字段见[数据字典](../docs/database/schema.md)，隔离分类见[多用户设计](../docs/architecture/multi-user.md)。

默认使用本机MySQL 8，日常运行不依赖Docker。建库、专用账号、连接配置和已有数据迁移见[本机MySQL](../docs/runbook/local-mysql.md)。

## 空库安装

配置数据库后启动服务，由Flyway执行唯一V1并登记版本1。不要在已有表的库重复导入，不另存一份汇总DDL。app_user保留空间1为PENDING，不提供默认可登录密码。PUBLIC首次管理员由本机环境配置建立，新用户始终空白自选；公共基础数据共享，17张私人表以tenant_id隔离。

## 已有开发库

当前开发库登记BASELINE/1。开发期修改基线不自动清库、重放种子或修改校验和，必须明确核对后同步受控结构。旧私人数据归属空间1，不复制到其他账号；未知recorded_at仍为NULL，旧报告冻结内容不重算。

用户已选择直接修改且不新建备份，开发工具`bash scripts/tools.sh upgrade-schema-direct`会校验项目数据库、BASELINE/1和无活动任务，核对原字段内存指纹，只补受控结构、租户字段及对应索引。它不导出业务库，不关闭Flyway校验，不作为生产自动升级入口。真实旧单用户结构升级另有隔离MySQL测试。

## 正式发布后的约束

首次正式发布后冻结V1，后续使用不可变增量迁移。发布前用上一正式结构验收升级，只回滚至隔离协议和结构兼容的二进制。当前单份V1是尚未定版阶段的约定，不能通过修改已发布SQL校验和管理客户数据库。

算法通过ADJ_V2 / RULE_V2 / TECHNICAL_V2显式区分；新增表和可复算证据不代表研究方法已证明有效。具体运行结果见[当前验收](../docs/verification/README.md)。

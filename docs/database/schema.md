# 当前数据库数据字典

以唯一 [V1开发基线](../../backend/src/main/resources/db/migration/V1__baseline.sql) 为准。金额为人民币元、数量为股、比例为小数，业务时间为上海时区。

当前共51张业务与控制表、494个中文字段注释，Flyway历史表另计。私人表的tenant_id由认证身份或后台执行范围注入，不接受客户端指定；共享数据边界见[多用户设计](../architecture/multi-user.md)。

## instrument

证券标的主数据：股票和基准指数

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| symbol | varchar(16) NOT NULL | 带交易所后缀的标的代码，例如300476.SZ、000300.SH |
| name | varchar(100) NOT NULL | 标的中文名称 |
| exchange | varchar(8) NOT NULL | 交易所标识：SH上海，SZ深圳 |
| type | varchar(16) NOT NULL | 标的类型：STOCK股票，INDEX指数 |
| listed_at | date DEFAULT NULL | 已知上市日期；NULL表示尚未登记，不据此伪造上市前行情 |
| delisted_at | date DEFAULT NULL | 已明确退市日期，缺证据时为空，不从行情缺口推断 |
| lifecycle_source | varchar(64) DEFAULT NULL | 生命周期元数据实际来源，人工补录保留公开依据 |
| lifecycle_observed_at | datetime(6) DEFAULT NULL | 生命周期资料实际核验时刻，上海时区 |
| lifecycle_evidence_json | longtext | 上市及退市日期的原始响应、公开链接和人工定位 |
| lifecycle_hash | char(64) DEFAULT NULL | 生命周期原文精确文本SHA256 |
| active | tinyint(1) NOT NULL DEFAULT '1' | 标的启用标志：1启用，0停用 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |

## adjustment_factor

同源复权因子：与日线按标的、日期、来源和模式匹配

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| instrument_id | bigint NOT NULL | 标的主键，关联instrument.id |
| trade_date | date NOT NULL | 交易日期，按上海时区记录 |
| factor | decimal(28,16) NOT NULL | 正数复权因子；分析价=原价×当日因子÷分析日因子，不能跨来源拼接 |
| source | varchar(64) NOT NULL | 实际数据供应商或人工来源标识 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |
| is_synthetic | tinyint(1) NOT NULL DEFAULT '0' | 演示数据标志：0真实数据，1演示数据；按模式隔离 |

## analysis_report

研究报告及不可覆盖的业务修订

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| report_type | varchar(16) NOT NULL | 报告类型：DAILY收盘日报，WEEKLY每周周报 |
| business_date | date NOT NULL | 本次研究或采集的业务日期，按上海时区记录 |
| revision | int NOT NULL | 同类型、同业务日的报告修订序号，从1递增 |
| status | varchar(32) NOT NULL | 报告生成状态，当前成功入库为SUCCESS |
| model | varchar(100) DEFAULT NULL | 生成模型名称；RULE_FALLBACK表示使用规则摘要 |
| prompt_version | varchar(32) DEFAULT NULL | 生成报告采用的提示词或规则摘要结构标识 |
| input_hash | char(64) DEFAULT NULL | 计算或生成时完整输入的SHA-256指纹 |
| token_usage | int DEFAULT NULL | 模型调用消耗的Token数量；未记录时为NULL |
| content_json | json DEFAULT NULL | 结构化报告正文JSON，区分事实、风险、解释和待验证事项 |
| content_md | longtext | 保存的Markdown摘要正文 |
| data_as_of | datetime(6) NOT NULL | 报告采用行情的业务截止时刻，通常为交易日15:00，上海时区 |
| is_synthetic | tinyint(1) NOT NULL DEFAULT '0' | 演示数据标志：0真实数据，1演示数据；按模式隔离 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |
| collected_at | datetime(6) DEFAULT NULL | 报告采用的输入证据中最新实际采集时刻；旧记录可为NULL |
| algorithm_version | varchar(32) NOT NULL DEFAULT 'LEGACY' | 计算算法标识，用于追溯当时采用的计算口径 |
| data_scope | varchar(191) NOT NULL DEFAULT 'LEGACY' | 运行数据范围标识，如DEMO或REAL:BAOSTOCK,SINA；不等于单条数据实际来源 |
| job_id | bigint DEFAULT NULL | 生成该报告的任务ID；旧记录或直接生成时可为NULL |

## daily_bar

日线行情最新视图：保留各来源和模式的原始价格

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| instrument_id | bigint NOT NULL | 标的主键，关联instrument.id |
| trade_date | date NOT NULL | 交易日期，按上海时区记录 |
| open | decimal(18,4) NOT NULL | 未复权开盘价，人民币元 |
| high | decimal(18,4) NOT NULL | 未复权最高价，人民币元 |
| low | decimal(18,4) NOT NULL | 未复权最低价，人民币元 |
| close | decimal(18,4) NOT NULL | 未复权收盘价，人民币元 |
| volume | decimal(24,4) NOT NULL | 成交量，单位股；不得混用手 |
| amount | decimal(24,4) NOT NULL | 成交金额，单位人民币元 |
| source | varchar(64) NOT NULL | 实际数据供应商或人工来源标识 |
| fetched_at | datetime(6) NOT NULL | 原始数据实际采集时间，上海时区；复用缓存不改写 |
| batch_id | varchar(64) NOT NULL | 本次行情写入批次标识，用于追溯来源记录 |
| is_synthetic | tinyint(1) NOT NULL DEFAULT '0' | 演示数据标志：0真实数据，1演示数据；按模式隔离 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |

## daily_bar_audit

行情或因子变化审计：保留变更前后完整证据

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| instrument_id | bigint NOT NULL | 标的主键，关联instrument.id |
| trade_date | date NOT NULL | 交易日期，按上海时区记录 |
| batch_id | varchar(64) NOT NULL | 本次行情写入批次标识，用于追溯来源记录 |
| old_value | json NOT NULL | 变更前的行情和复权因子JSON |
| new_value | json NOT NULL | 变更后的行情和复权因子JSON |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |

## earnings_forecast_snapshot

公开盈利预测观测快照：按首次采集时刻可用

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| instrument_id | bigint NOT NULL | 标的主键，关联instrument.id |
| observation_date | date NOT NULL | 实际观测日期，上海时区；不冒充机构预测发布日期 |
| available_at | datetime(6) NOT NULL | 该预测快照实际首次观测时刻，上海时区；不倒填旧报告 |
| source | varchar(32) NOT NULL | 实际数据供应商或人工来源标识 |
| source_url | varchar(512) NOT NULL | 原始公开来源URL，用于核验和追溯 |
| content_hash | char(64) NOT NULL | 内容SHA-256指纹，用于去重和定位不可变证据 |
| metrics_json | json NOT NULL | 公开汇总预测指标JSON，含年度EPS及评级机构数；EPS单位元/股 |
| raw_json | json NOT NULL | 供应商原始响应JSON，用于核验来源及保留当次证据 |
| fetched_at | datetime(6) NOT NULL | 原始数据实际采集时间，上海时区；复用缓存不改写 |

## financial_report

已披露累计财务报表：保留报告期、版本和可用时点

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| instrument_id | bigint NOT NULL | 标的主键，关联instrument.id |
| period_end | date NOT NULL | 累计财报对应的报告期末日期 |
| report_type | varchar(32) NOT NULL | 累计口径：Q1_CUMULATIVE一季、H1_CUMULATIVE半年、Q3_CUMULATIVE三季、ANNUAL全年 |
| published_at | datetime(6) NOT NULL | 原始材料公开发布时间，上海时区 |
| version | int NOT NULL | 同标的、报告期和报告类型的内容版本序号，只新增不覆盖 |
| revenue | decimal(24,4) DEFAULT NULL | 累计营业收入，人民币元；NULL表示缺失 |
| net_profit | decimal(24,4) DEFAULT NULL | 累计归属于母公司股东的净利润，人民币元；NULL表示缺失 |
| operating_cashflow | decimal(24,4) DEFAULT NULL | 累计经营活动产生的现金流量净额，人民币元；NULL表示缺失 |
| gross_margin | decimal(18,8) DEFAULT NULL | 累计毛利率，小数存储，如0.30表示30%；NULL表示缺失 |
| revenue_yoy | decimal(18,8) DEFAULT NULL | 累计营业收入同比增速，小数存储；NULL表示缺失 |
| profit_yoy | decimal(18,8) DEFAULT NULL | 累计归母净利润同比增速，小数存储；NULL表示缺失 |
| source | varchar(128) DEFAULT NULL | 实际数据供应商或人工来源标识 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |
| available_at | datetime(6) DEFAULT NULL | 本版本最早可用于研究的时刻；旧手工记录NULL时沿用published_at |
| publication_precision | varchar(16) NOT NULL DEFAULT 'TIMESTAMP' | 公告时间精度：TIMESTAMP精确时刻，DAY仅日期；日期精度保守次日起可用 |
| vendor_updated_at | datetime(6) DEFAULT NULL | 供应商标示的修订时间；不是已核验的原始公告修订时间 |
| fetched_at | datetime(6) DEFAULT NULL | 原始数据实际采集时间，上海时区；复用缓存不改写 |
| content_hash | char(64) DEFAULT NULL | 内容SHA-256指纹，用于去重和定位不可变证据 |
| source_url | text | 原始公开来源URL，用于核验和追溯 |
| raw_json | json DEFAULT NULL | 供应商原始响应JSON，用于核验来源及保留当次证据 |
| raw_payload | longtext DEFAULT NULL | 服务端封装的报表证据精确文本，配合content_hash验证；旧行NULL不补造原始字节 |

## indicator_snapshot

技术指标快照：统一复权锚点并保留数据完整性

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| instrument_id | bigint NOT NULL | 标的主键，关联instrument.id |
| trade_date | date NOT NULL | 交易日期，按上海时区记录 |
| algorithm_version | varchar(32) NOT NULL | 计算算法标识，用于追溯当时采用的计算口径 |
| ma5 | decimal(18,4) DEFAULT NULL | 最近5个有K线交易日复权收盘均价，人民币元；含当日，不足为空 |
| ma10 | decimal(18,4) DEFAULT NULL | 最近10个有K线交易日复权收盘均价，人民币元；含当日，不足为空 |
| ma20 | decimal(18,4) DEFAULT NULL | 最近20个有K线交易日复权收盘均价，人民币元；含当日，不足为空 |
| ma60 | decimal(18,4) DEFAULT NULL | 最近60个有K线交易日复权收盘均价，人民币元；含当日，不足为空 |
| atr14 | decimal(18,8) DEFAULT NULL | 14期Wilder平均真实波幅，人民币元；历史不足时为空 |
| rs20 | decimal(18,8) DEFAULT NULL | 20期相对沪深300的收益比值减1，小数存储；日期无法对齐时为空 |
| vol_ratio | decimal(18,8) DEFAULT NULL | 当日成交量除以前20个有K线交易日均量，单位倍；均量不含当日 |
| data_status | varchar(32) NOT NULL | 指标完整性：COMPLETE完整，INSUFFICIENT_HISTORY历史不足 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |
| is_synthetic | tinyint(1) NOT NULL DEFAULT '0' | 演示数据标志：0真实数据，1演示数据；按模式隔离 |
| input_hash | char(64) DEFAULT NULL | 计算或生成时完整输入的SHA-256指纹 |
| source_batch | varchar(64) DEFAULT NULL | 指标输入序列最后一条行情的批次标识 |

## theme

产业链研究主题字典

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| code | varchar(32) NOT NULL | 唯一主题代码，例如PCB、COOLING、CPO |
| name | varchar(100) NOT NULL | 主题中文名称 |
| description | text | 主题业务范围及研究说明 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |

## source_document

公开原始来源文档：保存链接、正文和内容指纹

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| source_type | varchar(32) NOT NULL | 来源材料类型标识，例如公告、报告或产业材料 |
| url | text | 原始公开材料URL |
| title | varchar(500) NOT NULL | 来源文档标题 |
| published_at | datetime(6) NOT NULL | 原始材料公开发布时间，上海时区 |
| fetched_at | datetime(6) NOT NULL | 原始数据实际采集时间，上海时区；复用缓存不改写 |
| content_hash | char(64) NOT NULL | 内容SHA-256指纹，用于去重和定位不可变证据 |
| content | longtext | 保留的原文或摘录文本；外部内容不具有系统指令权限 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |

## industry_metric

有公开来源依据的产业指标记录

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| theme_id | bigint NOT NULL | 主题主键，关联theme.id |
| metric_code | varchar(64) NOT NULL | 产业指标代码，需结合unit解释业务口径 |
| as_of_date | date NOT NULL | 人工登记或指标对应的业务日期，非数据库更新时间 |
| published_at | datetime(6) NOT NULL | 原始材料公开发布时间，上海时区 |
| value | decimal(24,8) DEFAULT NULL | 产业指标数值，单位由unit指定；NULL表示缺失 |
| unit | varchar(32) NOT NULL | 指标计量单位，例如元、吨或小数比例；不能跨单位直接比较 |
| source_document_id | bigint NOT NULL | 来源文档主键，关联source_document.id |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |

## instrument_day_status

供应商证实的标的停牌证据：未知缺口不能写成停牌

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| instrument_id | bigint NOT NULL | 标的主键，关联instrument.id |
| trade_date | date NOT NULL | 交易日期，按上海时区记录 |
| source | varchar(64) NOT NULL | 实际数据供应商或人工来源标识 |
| is_synthetic | tinyint(1) NOT NULL | 演示数据标志：0真实数据，1演示数据；按模式隔离 |
| status | varchar(32) NOT NULL | 当前证实状态为SUSPENDED停牌；不根据缺失K线推断 |
| batch_id | varchar(64) NOT NULL | 本次行情写入批次标识，用于追溯来源记录 |

## instrument_theme

人工登记的标的与主题历史有效归属

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| instrument_id | bigint NOT NULL | 标的主键，关联instrument.id |
| theme_id | bigint NOT NULL | 主题主键，关联theme.id |
| effective_from | date NOT NULL | 归属或样本生效日期，含当日；不得倒填为更早时点 |
| effective_to | date DEFAULT NULL | 归属或样本失效日期，含当日；NULL表示未设结束日期 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |

## job_execution

持久研究任务队列与各次执行尝试

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| job_name | varchar(64) NOT NULL | 任务类型：DAILY日报，WEEKLY周报，MARKET_HISTORY市场历史补齐，MARKET_UNIVERSE北交所目录补齐，MARKET_FULL三所证据核验 |
| business_date | date NOT NULL | 本次研究或采集的业务日期，按上海时区记录 |
| attempt | int NOT NULL | 同任务类型和业务日期的尝试序号，从1递增 |
| status | varchar(32) NOT NULL | 任务执行状态：等待、运行、成功、跳过、失败、中断、取消请求、超时请求、已取消、已超时；数据质量独立记录 |
| started_at | datetime(6) DEFAULT NULL | 本次尝试创建或开始执行的时间，上海时区 |
| ended_at | datetime(6) DEFAULT NULL | 本次尝试结束时间，上海时区；未结束时为空 |
| error | text | 本次执行失败、中断或跳过的原因说明 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |
| data_scope | varchar(191) NOT NULL DEFAULT 'LEGACY' | 运行数据范围标识，如DEMO或REAL:BAOSTOCK,SINA；不等于单条数据实际来源 |
| input_hash | char(64) DEFAULT NULL | 计算或生成时完整输入的SHA-256指纹 |
| owner_id | varchar(64) DEFAULT NULL | 当前消费任务的工作进程标识，用于互斥和失联恢复 |
| heartbeat_at | datetime(6) DEFAULT NULL | 工作进程最近心跳时间，上海时区 |
| retry_of | bigint DEFAULT NULL | 被重试的原任务ID；首次提交时为空 |
| data_status | varchar(32) DEFAULT NULL | 任务数据质量：AVAILABLE完整、PARTIAL部分、UNAVAILABLE缺失、NOT_APPLICABLE不适用、UNKNOWN未核验；旧记录为空 |
| reservation_bytes | bigint NOT NULL DEFAULT 0 | 活动任务保守预留的证据字节数，终态由预算触发器释放 |

## job_stage

研究任务各阶段执行状态与错误审计

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| job_id | bigint NOT NULL | 任务主键，关联job_execution.id |
| stage_name | varchar(64) NOT NULL | 流水线阶段代码，如CALENDAR、MARKET_SYNC、REPORT |
| status | varchar(32) NOT NULL | 阶段执行状态：RUNNING、SUCCESS、FAILED、INTERRUPTED、CANCELLED或TIMED_OUT；数据质量独立记录 |
| started_at | datetime(6) DEFAULT NULL | 本阶段开始时间，上海时区 |
| ended_at | datetime(6) DEFAULT NULL | 本阶段结束时间，上海时区；未结束时为空 |
| error | text | 本阶段失败或中断的具体原因 |
| data_status | varchar(32) DEFAULT NULL | 独立于阶段执行状态的数据质量；运行中或旧记录为空 |
| details_json | json DEFAULT NULL | 阶段核验、实际进度、硬截止和恢复来源JSON；旧记录为空 |

## job_attempt_history

同任务重试的不可变执行快照，主列表沿用原编号

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户空间，由服务端注入 |
| id | bigint NOT NULL AUTO_INCREMENT | 历史执行快照主键 |
| job_id | bigint NOT NULL | 稳定的任务主键，关联job_execution.id |
| attempt | int NOT NULL | 该快照对应的原执行序号 |
| execution_json | longtext NOT NULL | 重试前的状态、原因、时刻、来源与阶段快照，不随新执行覆盖 |
| content_hash | char(64) NOT NULL | 执行快照原文SHA256，用于读取时校验 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 快照实际保存时间，上海时区 |

## market_breadth_snapshot

沪深A股当日成交额与涨跌家数的不可变截面

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| trade_date | date NOT NULL | 交易日期，按上海时区记录 |
| universe_scope | varchar(32) NOT NULL | 证券覆盖范围标识，当前SH_SZ_A仅沪深A股，不含北交所 |
| source | varchar(64) NOT NULL | 实际数据供应商或人工来源标识 |
| content_hash | char(64) NOT NULL | 内容SHA-256指纹，用于去重和定位不可变证据 |
| metrics_json | json NOT NULL | 已核对市场指标JSON，含成交额、涨跌家数、覆盖率和状态；金额单位元 |
| raw_json | json NOT NULL | 本次证券清单与全量日线原文JSON，用于复核覆盖分母和缺口 |
| fetched_at | datetime(6) NOT NULL | 原始数据实际采集时间，上海时区；复用缓存不改写 |

## market_universe_snapshot

不可变的供应商在市证券清单及完整分页证据，不代替历史行情

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 不可变目录归档主键 |
| business_date | date NOT NULL | 实际目录观测的上海日期，不由当前名单倒填历史 |
| universe_scope | varchar(32) NOT NULL | 目录范围；BSE_A为北交所在市A股 |
| source | varchar(64) NOT NULL | 实际目录来源；SINA为新浪在市目录，不冒充交易所官方 |
| content_hash | char(64) NOT NULL | 成员文本、换行和证据文本组合的SHA256 |
| members_json | longtext NOT NULL | 已核验代码与名称的精确JSON文本 |
| evidence_json | longtext NOT NULL | 全部分页、前后计数、首屏确认及真实观测区间的精确JSON文本 |
| observed_at | datetime(6) NOT NULL | 原始核验结束时刻，上海时区；读取缓存不更新 |

## market_data_revision

不可变行情及复权因子修订：来源或模式切换不覆盖

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| instrument_id | bigint NOT NULL | 标的主键，关联instrument.id |
| trade_date | date NOT NULL | 交易日期，按上海时区记录 |
| source | varchar(64) NOT NULL | 实际数据供应商或人工来源标识 |
| is_synthetic | tinyint(1) NOT NULL | 演示数据标志：0真实数据，1演示数据；按模式隔离 |
| batch_id | varchar(64) NOT NULL | 本次行情写入批次标识，用于追溯来源记录 |
| fetched_at | datetime(6) NOT NULL | 原始数据实际采集时间，上海时区；复用缓存不改写 |
| content_hash | char(64) NOT NULL | 内容SHA-256指纹，用于去重和定位不可变证据 |
| bar_json | json NOT NULL | 本批次原价OHLC、成交量额、同源因子及采集元数据的冻结JSON |
| algorithm_version | varchar(32) NOT NULL DEFAULT 'RAW_V1' | 计算算法标识，用于追溯当时采用的计算口径 |
| recorded_at | datetime(6) DEFAULT NULL | 该不可变修订实际入库时刻；旧记录未知为NULL，不倒填为供应商采集时间 |

## market_factor_snapshot

市场广度计算所用的真实复权事件版本

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| symbol | varchar(16) NOT NULL | 供应商证券代码，如sh.600000或sz.300476，非页面统一后缀格式 |
| source | varchar(32) NOT NULL | 实际数据供应商或人工来源标识 |
| verified_through | date NOT NULL | 已核验复权事件覆盖到的交易日期，不是事件首次披露日 |
| content_hash | char(64) NOT NULL | 内容SHA-256指纹，用于去重和定位不可变证据 |
| raw_json | json NOT NULL | 供应商复权事件原文JSON；相同内容保留首次采集证据 |
| fetched_at | datetime(6) NOT NULL | 原始数据实际采集时间，上海时区；复用缓存不改写 |
| last_verified_at | datetime(6) NOT NULL | 最近一次确认该内容可覆盖verified_through的时间，上海时区 |

## market_observation_snapshot

独立沪深市场分项观察及其冻结证据

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| trade_date | date NOT NULL | 交易日期，按上海时区记录 |
| universe_scope | varchar(32) NOT NULL | 观察覆盖范围，当前SH_SZ_A；不冒充包含北交所的全A |
| model_version | varchar(48) NOT NULL | 评分或观察模型标识，用于区分计算口径 |
| content_hash | char(64) NOT NULL | 内容SHA-256指纹，用于去重和定位不可变证据 |
| analysis_json | json NOT NULL | 分项分析JSON，含趋势、量能、均线广度、覆盖率和缺失原因 |
| evidence_json | json NOT NULL | 观察输入证据JSON，含日历、基准、参数、截面及因子引用 |
| fetched_at | datetime(6) NOT NULL | 原始数据实际采集时间，上海时区；复用缓存不改写 |

## market_score

全市场五维研究评级：维度缺失保持未知

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| trade_date | date NOT NULL | 交易日期，按上海时区记录 |
| model_version | varchar(32) NOT NULL | 评分或观察模型标识，用于区分计算口径 |
| score | decimal(8,4) DEFAULT NULL | 全市场综合评分，范围0至100；证据不足时为NULL，不能按0分解释 |
| state | varchar(32) NOT NULL DEFAULT 'UNKNOWN' | 市场状态：UNKNOWN未知、STRONG强、POSITIVE偏强、SIDEWAYS震荡、WEAK偏弱、RISK_OFF低迷 |
| evidence_json | json NOT NULL | 计算或采集证据JSON，保存输入依据、参数、覆盖率及缺失原因 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |
| is_synthetic | tinyint(1) NOT NULL DEFAULT '0' | 演示数据标志：0真实数据，1演示数据；按模式隔离 |

## notification_log

本地通知审计及去重记录：当前不发送外部消息

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| report_id | bigint NOT NULL | 报告主键，关联analysis_report.id |
| channel | varchar(32) NOT NULL | 通知渠道，当前固定LOCAL本机审计 |
| recipient_hash | char(64) NOT NULL | 接收对象标识的SHA-256指纹；当前为本机对象 |
| status | varchar(32) NOT NULL | 审计状态，当前SENT表示已记录本地通知，不表示已发送外部消息 |
| sent_at | datetime(6) DEFAULT NULL | 本地通知审计写入时间，上海时区 |
| idempotency_key | varchar(191) NOT NULL | 通知幂等键，防止同一业务通知重复记录 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |

## portfolio_account

人工登记的研究账户资产总额：未知时不推算集中度

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL | 单账户固定主键1，不自增 |
| total_assets | decimal(24,4) DEFAULT NULL | 人工登记的投资资产总额，人民币元；NULL表示未知，已填须大于0 |
| risk_budget | decimal(24,4) DEFAULT NULL | 用户自行设定的组合风险预算，人民币元；NULL未知，不是系统推荐额度 |
| cash_balance | decimal(24,4) DEFAULT NULL | 用户明确登记的现金余额，不从总资产减股票市值猜测 |
| positions_complete | tinyint(1) DEFAULT NULL | 用户是否确认已登记全部持仓，NULL未知，不由空表推断空仓 |
| liquidity_reserve | decimal(24,4) DEFAULT NULL | 为近期用途预留的人民币金额；NULL未知，不按零推定可投资 |
| funds_needed_on | date DEFAULT NULL | 用户登记的预计用款日，不用于推测交易期限 |
| funds_purpose | varchar(300) DEFAULT NULL | 用户登记的资金用途，仅当前身份可见，不用于自动下单 |
| as_of_date | date NOT NULL | 人工登记或指标对应的业务日期，非数据库更新时间 |

## portfolio_position

人工登记的当前持仓：不是券商流水或自动交易记录

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| instrument_id | bigint NOT NULL | 标的主键，关联instrument.id |
| quantity | decimal(24,4) NOT NULL | 当前持仓数量，单位股；必须非负 |
| cost_price | decimal(18,4) NOT NULL | 人工登记的每股成本价，人民币元；必须非负 |
| as_of_date | date NOT NULL | 人工登记或指标对应的业务日期，非数据库更新时间 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |
| risk_group | varchar(256) DEFAULT NULL | 人工相关风险标签，逗号分隔可重叠，不冒充官方行业分类 |

## report_input_snapshot

生成报告时冻结的完整输入证据

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| report_id | bigint NOT NULL | 报告主键，关联analysis_report.id |
| input_json | json NOT NULL | 本报告采用的行情、指标、规则、财报、主题、预测及分析结果JSON |

## report_source

报告与来源材料或数据记录的引用关系

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| report_id | bigint NOT NULL | 报告主键，关联analysis_report.id |
| source_document_id | bigint DEFAULT NULL | 引用的来源文档ID；可与data_reference二选一或同时提供 |
| data_reference | varchar(255) DEFAULT NULL | 来源批次或数据记录引用字符串；与source_document_id至少填写一项 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |

## research_collection

可选研究数据最近采集结果：不替代不可变原始证据

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| data_kind | varchar(32) NOT NULL | 采集数据类别，如财报、市场截面、预测或MARKET_WINDOW行情窗口 |
| scope | varchar(64) NOT NULL | 采集对象代码或范围标识，例如标的代码或SH_SZ_A |
| business_date | date NOT NULL | 本次研究或采集的业务日期，按上海时区记录 |
| status | varchar(32) NOT NULL | 最近采集状态或复用方式，如SUCCESS、UNAVAILABLE、FRESH_CACHE、STORED_HISTORY、FETCHED |
| evidence_json | json NOT NULL | 计算或采集证据JSON，保存输入依据、参数、覆盖率及缺失原因 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |

## signal_event

逐标的规则评估结果与可复核证据

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| instrument_id | bigint NOT NULL | 标的主键，关联instrument.id |
| trade_date | date NOT NULL | 交易日期，按上海时区记录 |
| rule_code | varchar(64) NOT NULL | 风险规则代码，如TREND_BREAK_20、PROFIT_SLOWDOWN |
| rule_version | varchar(32) NOT NULL | 规则及阈值配置标识，用于追溯判断口径 |
| severity | varchar(16) NOT NULL | 风险严重程度：HIGH高、MEDIUM中、LOW低 |
| evidence_json | json NOT NULL | 计算或采集证据JSON，保存输入依据、参数、覆盖率及缺失原因 |
| status | varchar(32) NOT NULL | 评估状态：TRIGGERED触发、NOT_TRIGGERED未触发、INSUFFICIENT_DATA不足、NOT_APPLICABLE不适用；演示见is_synthetic |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |
| is_synthetic | tinyint(1) NOT NULL DEFAULT '0' | 演示数据标志：0真实数据，1演示数据；按模式隔离 |

## theme_research_cohort

有原始披露依据的固定同业研究样本：不等于全行业成分

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| theme_id | bigint NOT NULL | 主题主键，关联theme.id |
| code | varchar(96) NOT NULL | 不可变研究样本唯一代码；来源或成员改变须新建样本标识 |
| scope | varchar(48) NOT NULL | 样本覆盖范围标识，当前为DISCLOSED_RESEARCH_SAMPLE披露研究样本 |
| title | varchar(255) NOT NULL | 研究样本中文名称 |
| source_document_id | bigint NOT NULL | 来源文档主键，关联source_document.id |
| available_at | datetime(6) NOT NULL | 该样本公开依据最早可用于研究的时刻，上海时区 |
| effective_from | date NOT NULL | 归属或样本生效日期，含当日；不得倒填为更早时点 |
| effective_to | date DEFAULT NULL | 归属或样本失效日期，含当日；NULL表示未设结束日期 |
| expected_members | int NOT NULL | 披露定义的完整成员数量，至少2只；用于核对样本完整性 |
| content_hash | char(64) NOT NULL | 内容SHA-256指纹，用于去重和定位不可变证据 |
| definition_json | json NOT NULL | 样本完整定义JSON，含原始链接、页码、日期、PDF指纹、边界及全部成员 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |

## theme_research_member

固定研究样本的完整成员关系：不自动加入自选

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| cohort_id | bigint NOT NULL | 研究样本主键，关联theme_research_cohort.id |
| instrument_id | bigint NOT NULL | 标的主键，关联instrument.id |

## theme_score

主题或研究样本的等权收益、广度和覆盖率

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| theme_id | bigint NOT NULL | 主题主键，关联theme.id |
| trade_date | date NOT NULL | 交易日期，按上海时区记录 |
| model_version | varchar(32) NOT NULL | 评分或观察模型标识，用于区分计算口径 |
| score | decimal(8,4) DEFAULT NULL | 主题强弱评分，范围0至100；覆盖不足或演示模式时为空 |
| rs20 | decimal(18,8) DEFAULT NULL | 有效成员20期相对沪深300强度的等权均值，小数存储 |
| breadth | decimal(18,8) DEFAULT NULL | 有效成员中收盘价高于MA20的比例，范围0至1；无有效成员时为空 |
| coverage | decimal(18,8) DEFAULT NULL | 有效计算成员数除以当日完整成员数，范围0至1 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |
| evidence_json | json DEFAULT NULL | 计算或采集证据JSON，保存输入依据、参数、覆盖率及缺失原因 |
| day_return | decimal(18,8) DEFAULT NULL | 历史有效成员当日同源复权收益的等权均值，小数存储 |
| is_synthetic | tinyint(1) NOT NULL DEFAULT '0' | 演示数据标志：0真实数据，1演示数据；按模式隔离 |

## trading_calendar

供应商提供的交易所开闭市日历：不按星期伪造休市

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| trade_date | date NOT NULL | 交易日期，按上海时区记录 |
| exchange | varchar(8) NOT NULL | 日历交易所标识，当前使用SSE |
| is_open | tinyint(1) NOT NULL | 是否开市：1开市，0休市；以供应商日历为准 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |
| is_synthetic | tinyint(1) NOT NULL DEFAULT '0' | 演示数据标志：0真实数据，1演示数据；按模式隔离 |
| source | varchar(64) NOT NULL DEFAULT 'LEGACY' | 本条交易日历的实际来源标识 |

## watchlist

当前自选研究范围与关注优先级

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL AUTO_INCREMENT | 记录主键，自增生成 |
| instrument_id | bigint NOT NULL | 标的主键，关联instrument.id |
| priority | int NOT NULL DEFAULT '0' | 关注优先级，数值越大排序越靠前 |
| enabled | tinyint(1) NOT NULL DEFAULT '1' | 是否纳入自选研究：1启用，0停用 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 记录创建时间，上海时区 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |

## signal_revision

不可变规则修订：保存真实入库时刻及当次阈值

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL AUTO_INCREMENT | 不可变规则修订主键 |
| instrument_id | bigint NOT NULL | 标的主键，关联instrument.id |
| trade_date | date NOT NULL | 规则所属交易日期，上海时区 |
| rule_code | varchar(64) NOT NULL | 稳定规则代码 |
| rule_version | varchar(32) NOT NULL | 规则版本标识 |
| severity | varchar(16) NOT NULL | 风险等级HIGH、MEDIUM或LOW |
| status | varchar(32) NOT NULL | 本次真实计算状态，包括触发、未触发、不足或不适用 |
| evidence_json | json NOT NULL | 本次规则输入和证据的不可变JSON |
| parameters_json | json NOT NULL | 计算当时采用的完整阈值JSON，不能用当前配置替代 |
| content_hash | char(64) NOT NULL | 规则状态、证据及参数共同计算的SHA256 |
| recorded_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 实际入库时刻，上海时区；不允许调用方倒填 |
| is_synthetic | tinyint(1) NOT NULL | 演示标志：0真实、1演示，严格隔离 |

## validation_experiment

不可覆盖的历史验证实验及可复算输入

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL AUTO_INCREMENT | 历史验证实验主键 |
| symbol | varchar(16) NOT NULL | 实验证券代码，不依赖后续自选或主数据变化 |
| data_scope | varchar(191) NOT NULL | 保存实验时的数据源及模式范围 |
| availability_mode | varchar(32) NOT NULL | RETROSPECTIVE回顾或POINT_IN_TIME严格时点 |
| method_version | varchar(64) NOT NULL | 冻结的方法标识，仅支持匹配实现的复算 |
| request_json | longtext NOT NULL | 实验请求日期、版本和分割参数的完整冻结JSON |
| snapshot_json | longtext NOT NULL | 原价、同源因子、修订ID、信号、日历及实际阈值的冻结JSON文本，保留精确数值 |
| result_json | longtext NOT NULL | 实验生成时的完整结果JSON文本，保留原数值且不覆盖 |
| input_hash | char(64) NOT NULL | 完整冻结输入的SHA256 |
| result_hash | char(64) NOT NULL | 冻结结果的SHA256 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 实际保存时刻，上海时区 |

## research_validation_task

只读研究验证任务：持久队列、取消、进度和完整证据

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户的私人研究范围，不接受客户端指定 |
| id | bigint NOT NULL AUTO_INCREMENT | 验证任务主键，自增生成 |
| status | varchar(32) NOT NULL DEFAULT 'PENDING' | PENDING排队、RUNNING执行、CANCEL_REQUESTED请求取消、TIMEOUT_REQUESTED请求超时、SUCCESS完成、CANCELLED取消、FAILED失败、TIMED_OUT超时、INTERRUPTED失联 |
| scope | varchar(16) NOT NULL | 验证类型：ALL全部、TECHNICAL技术规则、MARKET完整市场 |
| data_scope | varchar(256) NOT NULL | 任务接收时的数据源与真实模式指纹，不含密钥 |
| active_key | char(64) DEFAULT NULL | 相同活动请求的SHA256去重键，终态清空以允许明确重跑 |
| request_json | longtext NOT NULL | 任务接收时冻结的日期、证券、验证范围、规则版本及阈值 |
| owner_token | char(36) DEFAULT NULL | 当前执行进程的随机归属令牌，最终写入必须匹配 |
| completed_units | int NOT NULL DEFAULT '0' | 当前阶段实际完成数量 |
| total_units | int NOT NULL DEFAULT '0' | 当前阶段总量，0表示尚未确定 |
| message | varchar(500) DEFAULT NULL | 当前处理动作或失败摘要，不包含密钥或完整原文 |
| summary_json | longtext DEFAULT NULL | 轻量验证摘要，不含原始价格、逐日证据或大信号数组 |
| evidence_json | longtext DEFAULT NULL | 完整冻结研究证据，仅显式下载；最大容量由配置限制 |
| evidence_sha256 | char(64) DEFAULT NULL | 完整证据文本SHA256，下载和读取时核对 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 接收任务时间，上海时区 |
| started_at | datetime(6) DEFAULT NULL | 实际开始执行时间，上海时区 |
| finished_at | datetime(6) DEFAULT NULL | 最终完成、取消、超时或失败时间，上海时区 |
| heartbeat_at | datetime(6) DEFAULT NULL | 执行进程最近心跳，上海时区 |
| deadline_at | datetime(6) DEFAULT NULL | 执行硬截止时间，上海时区，不随心跳延长 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 记录最后更新时间，上海时区 |
| reservation_bytes | bigint NOT NULL DEFAULT 0 | 活动任务保守预留的证据字节数，终态由预算触发器释放 |

## app_user

平台账号与启用状态，不作为私人业务查询的租户输入

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 账号主键，也是私人研究范围主键 |
| username | varchar(64) NOT NULL | 规范化登录名称，唯一且不区分大小写 |
| password_hash | varchar(256) DEFAULT NULL | PBKDF2算法、迭代参数、独立盐和摘要，不存明文 |
| role | varchar(16) NOT NULL DEFAULT 'USER' | 权限角色USER或ADMIN |
| status | varchar(16) NOT NULL DEFAULT 'ACTIVE' | ACTIVE启用，DISABLED停用，PENDING待初始化 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 账号创建时间，上海时区 |

## auth_session

可撤销的服务端会话，Cookie中保存随机令牌

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 会话内部主键 |
| user_id | bigint NOT NULL | 认证账号主键 |
| token_hash | char(64) NOT NULL | 随机会话令牌SHA256摘要 |
| csrf_hash | char(64) NOT NULL | 浏览器防伪令牌SHA256摘要 |
| expires_at | datetime(6) NOT NULL | 固定到期时间，上海时区 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 会话创建时间，上海时区 |

## auth_event

不含密码或令牌的认证审计与失败限流证据

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 认证审计主键 |
| account_hash | char(64) NOT NULL | 规范化登录名SHA256，不存凭据 |
| actor_id | bigint DEFAULT NULL | 已确认操作身份；匿名失败记录为空，不接受客户端指定 |
| success | tinyint(1) NOT NULL | 认证是否成功 |
| reason | varchar(32) NOT NULL | 受控认证事件代码 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 事件发生时间，上海时区 |

## source_license

数据来源使用范围登记，人工声明不等于供应商或法律认证

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 许可登记修订主键，只新增不覆盖 |
| source | varchar(40) NOT NULL | 实际数据来源代码 |
| permissions_json | varchar(500) NOT NULL | 人工确认的展示采集导出及派生报告范围JSON |
| evidence_url | varchar(1000) NOT NULL | 授权或条款依据HTTPS地址，不保存密钥 |
| reference | varchar(200) NOT NULL | 合同或许可定位，不视作独立法律认证 |
| valid_from | date NOT NULL | 许可开始日期 |
| valid_until | date NOT NULL | 许可截止日期 |
| reviewer_id | bigint NOT NULL | 实际登记管理员账号 |
| content_hash | char(64) NOT NULL | 登记内容SHA256 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 真实登记时刻，上海时区 |

## service_alert

当前账号运行问题的持久发现及恢复生命周期

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 运行告警主键 |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属账号的私人运行范围 |
| issue_key | char(64) NOT NULL | 稳定事项SHA256，用于重复发现去重 |
| message | varchar(1000) NOT NULL | 明确的可操作事项，不含密钥 |
| first_seen_at | datetime(6) NOT NULL | 本次异常首次出现时刻，上海时区 |
| last_seen_at | datetime(6) NOT NULL | 最后观察到该事项的时刻 |
| resolved_at | datetime(6) DEFAULT NULL | 完整检查确认恢复的时刻，未恢复为空 |

## research_plan

用户冻结研究计划

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 个人研究计划主键 |
| tenant_id | bigint NOT NULL DEFAULT 1 | 所属用户，由会话隔离自动写入 |
| symbol | varchar(16) NOT NULL | 证券代码，含交易所后缀 |
| horizon | varchar(16) NOT NULL | 研究周期SHORT、MEDIUM、LONG |
| action_code | varchar(64) NOT NULL | 冻结行动方案代码 |
| snapshot_json | longtext NOT NULL | 原始研究精确JSON文本，不随新行情覆盖 |
| snapshot_hash | char(64) NOT NULL | 原始研究文本SHA256 |
| research_date | date GENERATED ALWAYS AS (CASE WHEN JSON_TYPE(JSON_EXTRACT(snapshot_json,'$.research.asOf'))='STRING' THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(snapshot_json,'$.research.asOf')) AS DATE) ELSE NULL END) STORED | 冻结研究日，由原始证据派生并建立日期索引 |
| acknowledged_review_hash | char(64) DEFAULT NULL | 用户已阅读复核的精确指纹，新证据不自动确认 |
| archived | tinyint(1) NOT NULL DEFAULT 0 | 用户是否已归档，归档不删除证据 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 计划实际保存时间，上海时区 |

## research_plan_review

个人计划不可变复核记录

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 追加复核主键 |
| tenant_id | bigint NOT NULL DEFAULT 1 | 所属用户，由会话隔离自动写入 |
| plan_id | bigint NOT NULL | 所属原始计划主键 |
| review_json | longtext NOT NULL | 本次复核精确JSON，追加而不覆盖 |
| review_hash | char(64) NOT NULL | 本次复核文本SHA256 |
| review_date | date GENERATED ALWAYS AS (CASE WHEN JSON_TYPE(JSON_EXTRACT(review_json,'$.date'))='STRING' THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(review_json,'$.date')) AS DATE) ELSE NULL END) STORED | 复核业务日，由不可变复核证据派生 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 复核保存时间，上海时区 |

## theme_leader_revision

当前核心企业目录不可变修订

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 不可变目录修订主键 |
| theme_code | varchar(64) NOT NULL | 已登记主题代码 |
| directory_json | longtext NOT NULL | 核心目录精确JSON，不修改原披露成分 |
| content_hash | char(64) NOT NULL | 目录文本SHA256 |
| reviewer_id | bigint NOT NULL | 实际维护管理员主键 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 目录保存时间，上海时区 |

## storage_budget

证据正文的增量计量与写入预算，不保存研究内容

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| scope_id | bigint NOT NULL | 0为平台总量，-1为默认个人额度，正数为用户空间 |
| used_bytes | bigint NOT NULL DEFAULT 0 | 全部受计量证据正文的数据库OCTET_LENGTH总量，不是物理磁盘占用 |
| reserved_bytes | bigint NOT NULL DEFAULT 0 | 活动日报、共享采集及验证任务的保守预留，终态原子释放 |
| limit_bytes | bigint NOT NULL | 该范围证据使用与预留的总上限，单位字节 |
| updated_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 计量或额度最后变更时刻，上海时区 |

## corporate_action

带公开公告的送转分红证据，不从价格因子反推

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 公开公司行动证据主键 |
| symbol | varchar(16) NOT NULL | 含交易所后缀证券代码 |
| business_date | date NOT NULL | 除权除息业务日期 |
| evidence_json | longtext NOT NULL | 公告日期、送转分红及同源因子的完整证据JSON |
| evidence_hash | char(64) NOT NULL | 不可变证据SHA256 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 实际登记时间，上海时区 |

## investment_thesis

用户明确确认的长期经营估值假设，不是系统目标价

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 用户长期估值情景主键 |
| tenant_id | bigint NOT NULL DEFAULT 1 | 所属用户，由已认证身份注入 |
| symbol | varchar(16) NOT NULL | 含交易所后缀证券代码 |
| business_date | date NOT NULL | 研究情景依据截止日 |
| evidence_json | longtext NOT NULL | EPS和PE区间、安全边际、依据及失效条件JSON |
| evidence_hash | char(64) NOT NULL | 不可变情景SHA256 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 用户实际保存情景的时间，上海时区 |

## portfolio_revision

人工持仓与账户不可变登记历史

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 登记版本主键 |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户，由服务端身份注入 |
| record_kind | varchar(16) NOT NULL | POSITION持仓或ACCOUNT账户 |
| symbol | varchar(16) NOT NULL | 持仓证券代码；账户固定ACCOUNT |
| business_date | date NOT NULL | 用户声明的业务日期，不能替代实际记录时点 |
| evidence_json | longtext NOT NULL | 服务端捕获的类型化人工登记内容 |
| evidence_hash | char(64) NOT NULL | 登记内容SHA256，用于核验和幂等 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 首次真实登记时刻，禁止回填 |

## research_observation

实际每日判断与冻结协议的私人观察记录

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 每日观察修订主键 |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属研究用户，由服务端注入 |
| symbol | varchar(16) NOT NULL | 冻结研究对应的证券代码 |
| business_date | date NOT NULL | 所选研究日；实际形成日以created_at为准 |
| capture_kind | varchar(16) NOT NULL | LIVE本日形成或RETROSPECTIVE事后回看 |
| evidence_json | longtext NOT NULL | 完整判断、协议与当时市场证据 |
| evidence_hash | char(64) NOT NULL | 不可变观察内容SHA256 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 实际保存时间，不倒填为过去研究日 |

## forward_result

冻结前瞻窗口执行结果与可复算输入

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| id | bigint NOT NULL AUTO_INCREMENT | 前瞻窗口结果主键 |
| tenant_id | bigint NOT NULL DEFAULT '1' | 所属用户，由服务端身份注入 |
| observation_id | bigint NOT NULL | 原始每日判断编号，不接受外部用户编号 |
| action_code | varchar(40) NOT NULL | 冻结研究周期及行动代码 |
| window_days | int NOT NULL | 按权威市场日历计数的5、10或20交易日窗口 |
| metrics_json | longtext NOT NULL | 冻结执行结果、费用、基准与排除原因 |
| input_json | longtext NOT NULL | 原判断指纹、协议、同源价格及日历的完整复算输入 |
| content_hash | char(64) NOT NULL | 完整输入和结果的SHA256 |
| metrics_hash | char(64) NOT NULL | 轻量结果正文SHA256，读取摘要也逐项核验 |
| created_at | datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 首次实际完成评估时刻，不倒填 |

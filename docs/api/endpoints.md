# REST API

服务地址默认 `http://127.0.0.1:8080`，业务前缀为 `/api/v1`。本页按当前 Controller / DTO / VO 整理；运行中的完整参数与字段可在 [Swagger UI](http://127.0.0.1:8080/swagger-ui.html) 或 [OpenAPI JSON](http://127.0.0.1:8080/v3/api-docs) 核对。

## 通用约定

| 项目      | 约定                                                                    |
| --------- | ----------------------------------------------------------------------- |
| 日期      | ISO 日期 `YYYY-MM-DD`；业务时区为上海                                   |
| 金额/数量 | 人民币元 / 股；比例使用小数                                             |
| 分页      | `page=1`、`size=20`，size 上限 100；`data` 为 `{items,total,page,size}` |
| 分页端点  | `/instruments`、`/reports`、`/admin/jobs`                               |
| 其他查询  | 各端点的 `data` 为数组或明确类型的专用对象                              |
| 写操作    | LOCAL仅直接回环；PUBLIC会话、Origin及CSRF；普通写入按用户每秒一次       |
| 错误      | 与成功相同的 `{code,message,data,requestId}`；失败 `data=null`          |
| 模式      | 当前真实/演示模式过滤主要列表；历史报告按 ID 读取时仍保留其模式标识     |

全部业务接口统一返回 `Result<T>`：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": { "id": 42, "status": "PENDING" },
  "requestId": "服务端追踪编号"
}
```

- 查询时 `data` 是端点声明的 VO 或分页对象；无业务返回值的成功写入为 `data=null`。
- 失败时 `data=null`，`code` 为 `INVALID_ARGUMENT`、`NOT_FOUND`、`CONFLICT`、`LOCAL_ONLY`、`RATE_LIMITED` 或 `INTERNAL_ERROR` 等稳定枚举值，保留真实的 400/401/403/404/405/409/429/500 HTTP 状态。
- 任务提交使用 HTTP 202 和 `Result<JobView>`；202 只表示成功接收，不表示研究已经完成。
- 每个响应的 `requestId` 与 `X-Request-Id` 相同。前端 `api<T>` 统一校验外壳、处理业务失败并返回 `data`；页面不自行重复解包。
- 下方表格的响应说明均指 `data`。分页例如 `result.data.items`，任务编号例如 `result.data.id`。
- `/actuator/health`、`/v3/api-docs` 等框架端点保留自身协议。

## 行情、指标与市场

| 方法 | 路径                                | 参数               | 响应说明                                                                          |
| ---- | ----------------------------------- | ------------------ | --------------------------------------------------------------------------------- |
| GET  | `/instruments`                      | q、page、size      | 标的分页                                                                          |
| GET  | `/instruments/{symbol}`             | 股票或指数代码     | 主数据业务字段                                                                    |
| GET  | `/instruments/{symbol}/bars`        | start、end         | items 和价格/量/额单位；每条有 factor、source、batch_id、fetched_at、is_synthetic |
| GET  | `/instruments/{symbol}/indicators`  | start、end         | 指标序列、数据状态、算法版本和输入哈希                                            |
| GET  | `/instruments/{symbol}/financials`  | asOf               | 截至当时已披露的财报                                                              |
| GET  | `/instruments/{symbol}/signals`     | start、end         | 规则状态与证据；默认最近一个月                                                    |
| GET  | `/market/universe` | date | 当日北交所供应商目录、来源、采集时刻及归档指纹；只读 |
| GET  | `/market/overview`                  | date               | 当前业务日期的评级与覆盖状态                                                      |
| GET  | `/market/scores`                    | start、end         | 市场评分历史                                                                      |
| GET  | `/market/providers/baostock-policy` | 无                 | 只读本机预算和访问保护；不访问供应商、不解除保护，损坏计数不填零                  |
| GET  | `/market/providers`                 | 无                 | 通道观测状态、配置范围、日历来源和入库来源；不触发采集                            |
| POST | `/admin/market/providers/test` | channel、capability、date | 管理员显式测试单通道基础能力；20秒截止、全局一个、同通道60秒限频；不落库、不降级 |
| GET  | `/backtest/signals`                 | symbol、start、end | 已保存信号后 5/10/20 交易日的基本统计                                             |

`symbol` 例如 `300476.SZ`、`000300.SH`。缺失全 A 数据时市场为 `UNKNOWN`；统计不是包含成本、滑点和可成交性的交易回测。

## 主题、自选与资产

| 方法   | 路径                            | 参数或 JSON 请求体                    |
| ------ | ------------------------------- | ------------------------------------- |
| GET    | `/themes`                       | 无                                    |
| GET    | `/themes/{code}/strength`       | start、end                            |
| GET    | `/themes/{code}/metrics`        | asOf                                  |
| GET    | `/watchlist`                    | 无，返回数组                          |
| POST   | `/watchlist`                    | symbol、priority                      |
| DELETE | `/watchlist/{symbol}`           | 路径参数 symbol                       |
| GET    | `/portfolio/positions`          | 返回 items、account、currency         |
| PUT    | `/portfolio/positions/{symbol}` | quantity、costPrice、asOfDate         |
| PUT    | `/portfolio/account`            | totalAssets、asOfDate；未知资产可为空 |

## 报告查询与版本

| 方法 | 路径                              | 用途                                                         |
| ---- | --------------------------------- | ------------------------------------------------------------ |
| GET  | `/reports`                        | type、start、end、page、size、latestOnly                     |
| GET  | `/reports/{id}`                   | 正文、时间、范围、算法、来源、input_snapshot 及可读 analysis |
| GET  | `/reports/{id}/versions`          | 同业务日期、同类型、同模式的修订列表                         |
| GET  | `/reports/{id}/compare/{otherId}` | 内容字段变化和 inputChanged；跨日期/类型/模式返回参数错误    |

`type` 为 `DAILY` 或 `WEEKLY`；省略日期时默认最近三个月。`latestOnly=true` 默认同日只返回最新版，`false` 返回所有修订。前端提供开始日期，支持查询更早报告。

```text
GET /api/v1/reports?type=DAILY&start=2026-01-01&end=2026-10-30&page=1&size=20&latestOnly=true
```

报告的 `data_as_of` 是采用行情的业务时刻，`collected_at` 是采集时刻，`created_at` 是生成时刻。财报采用截至业务日 16:10、且不晚于实际当前时刻的披露证据。旧报告未曾冻结的字段不伪造补齐。

北交所目录通过 `POST /api/v1/admin/jobs/market-universe?date=YYYY-MM-DD` 显式采集，仅管理员可用，202表示已入队。允许盘中核验；仅当前上海日期可采集新名单，过去日期只复用真实归档。任务不采集日线、不改自选和报告。详见[数据源手册](../runbook/data-providers.md#北交所当前在市目录)。

## 任务提交、停止与重试

| 方法 | 路径                     | 参数 / 用途                                                      |
| ---- | ------------------------ | ---------------------------------------------------------------- |
| GET  | `/admin/jobs`            | page、size；执行记录分页                                         |
| GET  | `/admin/jobs/{id}`       | 任务状态、心跳、阶段与错误                                       |
| POST | `/admin/jobs/daily`      | date 必填；force 默认 false                                      |
| POST | `/admin/jobs/weekly`     | date 必填；分析当周最后已开市日期；force 默认 false              |
| POST | `/admin/jobs/{id}/retry` | 重试失败、中断、取消或超时；沿用原编号并归档原执行 |

| POST | `/admin/jobs/{id}/cancel` | 持久化取消意图；排队任务直接取消，运行任务等待收尾 |

任务提交过程：

1. 写入持久队列并返回 HTTP 202，状态可能已经从 PENDING 转为 RUNNING。
2. 使用 `data.id` 轮询任务详情；关闭页面不取消后台执行。
3. SUCCESS 表示执行完成，须另看 data_status；SKIPPED 表示跳过，失败、取消或超时可在核对原因后重试。
4. 相同范围、相同输入复用已有结果；输入变化允许重算；force=true 明确要求新修订。已有活动任务不重复排队。

心跳每 5 秒更新；两分钟失联且工作锁已释放才标记中断。完整状态定义见[数据字典](../database/schema.md#任务状态)。

## 本地数据维护

| 方法 | 路径                       | JSON 请求体                                                         |
| ---- | -------------------------- | ------------------------------------------------------------------- |
| POST | `/admin/instruments`       | symbol、name、exchange、type、listedAt                              |
| POST | `/admin/theme-memberships` | symbol、themeCode、effectiveFrom、effectiveTo                       |
| POST | `/admin/financials`        | symbol、periodEnd、reportType、publishedAt、version 及财务字段      |
| POST | `/admin/sources`           | sourceType、url、title、publishedAt、content；返回来源 id           |
| POST | `/admin/industry-metrics`  | themeCode、metricCode、asOfDate、publishedAt、value、unit、sourceId |

写入财报和产业数值前核对真实来源、单位和披露时间；历史成分必须填写生效区间。详细字段限制以运行中的 OpenAPI 为准。

报告详情的 `analysis` 仅使用该修订冻结输入。有冻结分析时直接返回生成时保存的结果，旧版使用其已有输入解析；无行情快照时明确返回 `LEGACY_NO_MARKET_SNAPSHOT`，不从当前库回填历史。

新增 `analysis.themes[]` 包含每只自选的研究样本来源与完整成员、`recentDays` 三日收益/广度、`window20/window60` 收益相关/控制市场后的相关/区间收益差/覆盖分母；`analysis.advice[]` 包含结论、优先级、观察事实、待验证解释、具体核查和调整判断条件。输入快照同步保存 `themeContexts`、`ruleParameters`、`analysis`。原字段保持兼容，旧版没有这些数据时不伪造补齐。统计边界见[同业研究手册](../runbook/theme-research.md)。

## 沪深观察与机构预测

`GET /api/v1/market/overview?date=YYYY-MM-DD` 新增observation，含真实业务日期、趋势、5/20日量能、MA20/60广度、分母和来源采集状态；返回最近不晚于所选日期的已保存观察。

`GET /api/v1/expectations?asOf=YYYY-MM-DD` 只读返回启用自选截至该日已保存的EPS预测、评级机构数和同年快照对比。实际截止不晚于当前时刻；历史报告采用16:10截止，不向过去注入当前预测。

报告analysis新增marketObservation与forecasts。原接口和旧快照保留；新字段为空时不从当前库补造历史。源状态ACCESS_RESTRICTED表示收到上游明确拒绝后持久停止请求。详细口径见[运行手册](../runbook/market-observation.md)。

## 历史行情版本

新报告的 `input_snapshot.marketAcquisition` 与 `analysis.marketAcquisition` 保存各标的 `symbol`、`source`、`mode`、`validatedFrom/validatedThrough`、`rows`、`oldestFetchedAt/newestFetchedAt` 和 `note`。`FRESH_CACHE` 表示有效期内缓存，`STORED_HISTORY` 表示通过完整性及数值校验的已存真实历史，`FETCHED` 表示本次采集。原采集时间不被报告生成时间替换；历史版本不保证包含供应商最新修订。旧报告缺少该字段时不从当前库补填。

任务指纹包含缓存策略与当前流水线/报告算法标识（集中定义于 `ResearchVersion.java`）。失败重试沿用原任务编号，原状态、原因和阶段保存在 `job_attempt_history`，不清除历史；缓存不足时错误中列出具体标的、请求区间和已保存/应有交易日数量。数据库使用当前单份初始化基线。

## GET 请求条件对象

日期与分页查询现在由请求 DTO 绑定，原 URL、查询参数名称及 Result 响应保持一致。接口仍按查询字符串调用，例如：

```text
GET /api/v1/instruments/300476.SZ/indicators?start=2026-10-01&end=2026-10-30
```

- 指标、日线和评分要求 `start`、`end`；使用 ISO 日期，起始日不得晚于结束日。后续收益统计另外要求 `symbol`。
- 规则、主题和报告可省略日期；Service 分别采用原先最近一个月或三个月的默认范围，截止日默认为上海当前日期。
- 分页保持 `page=1`、`size=20`，最多100条；省略或空值仍采用默认值。报告保持 `type=DAILY`、`latestOnly=true`，显式 `false` 仍有效。
- 路径中的 `symbol` 或主题 `code` 独立绑定；查询字符串中的同名字段不能替代路径标识。客户端不能通过请求对象切换真实/演示模式或实际行情来源。
- 缺少必填字段、日期格式/顺序错误、非法分页或报告类型返回 HTTP400 与统一 `INVALID_ARGUMENT`，不会因对象绑定失败返回500。

公开参数仍由 OpenAPI 展开显示，内部日期顺序校验属性不作为查询参数暴露。

## 历史验证和任务质量响应

`GET /backtest/signals` 除 `symbol/start/end` 外支持：`evaluationDate`（窗口观测截至日，默认end，不能晚于当前上海日期）、`splitDate`（较晚留出区间首日，默认按日期约70%处分割）、`availabilityMode=RETROSPECTIVE|POINT_IN_TIME`、可选 `ruleVersion`。信号区间最多十年；同规则混有多个版本且未指定版本时排除，避免混算。

响应增加 `availabilityMode/evaluationDate/splitDate/validationWindows/minimumSamples/methodVersion`。每个5/10/20日统计有触发数、成熟样本数、排除原因、基准样本数、均值/中位数/超额收益；没有成熟样本不能标记OBSERVED，缺失统计保留null。模式和方法详见[历史研究验证](../runbook/historical-validation.md)。

任务与阶段增加独立 `data_status`，阶段增加 `details`，包含 `quality`、实际 `progress`、UTC硬截止 `deadline`、`retryOf` 和 `recoveryMode`。业务时间仍为上海时区；截止及进度Instant含Z。旧记录未曾核验时为null，不能当作完整。

取消请求先持久化为CANCEL_REQUESTED；截止为TIMEOUT_REQUESTED，完成清理后分别为CANCELLED和TIMED_OUT。最先保存的停止原因优先，完成提交校验owner和当前状态，不能覆盖停止请求。前端收到取消响应后仍轮询，直到终态。无法撤销已提交的独立业务事务或已归档报告。

失败重试在原记录重新入队并核验已存输入，可以复用通过完整性和时效校验的数据，不根据旧阶段SUCCESS盲目跳过。通道页的 `capabilities` 区分五种能力及最近观测；声明支持不代表账号已获得权限。

## 五维规则、冻结实验与运维

| 方法 | 路径                                       | 用途                                                                  |
| ---- | ------------------------------------------ | --------------------------------------------------------------------- |
| GET  | /market/research-score?date=YYYY-MM-DD     | 只读运行MARKET_RULES_V3，返回公式、原范围分数、代理状态及完整总分准入 |
| POST | /admin/backtest-experiments                | JSON为SignalStatisticsRequest，显式捕获不可覆盖实验；模拟模式拒绝     |
| GET  | /backtest/experiments?page=1&size=20       | 分页元数据，不加载大输入                                              |
| GET  | /backtest/experiments/{id}                 | 核验输入/结果指纹后返回原冻结结果                                     |
| POST | /admin/backtest-experiments/{id}/reproduce | 只用冻结输入重算，返回inputVerified/resultMatches及两个结果指纹       |
| GET  | /admin/operations                          | 只读业务日期、行情时效、备份证明和第二存储状态                        |
| POST | /admin/operations/backup                   | 请求独立后台备份，返回QUEUED或ALREADY_RUNNING；不重复排队             |

实验采用SAVED_SIGNAL_VALIDATION_V3，保存修订ID与实际参数。规则版本可包含@参数指纹，防止同名版本不同阈值混算。模型不匹配和内容完整性异常不会静默使用当前数据。保存和复算受当前身份、配额及每秒一次限制；备份仅管理员可请求。

运维字段里的AVAILABLE表示该项核验通过，不等于收益或上游实时连通。自动备份默认关闭；第二存储未配置时明确返回NOT_CONFIGURED，不伪装已具备异地副本。

## 后台研究验证

| 方法 | 路径                                                | 用途                                                      |
| ---- | --------------------------------------------------- | --------------------------------------------------------- |
| POST | /api/v1/admin/backtest-validation-tasks             | 请求体start、end、scope及可选symbols；HTTP202返回持久任务 |
| GET  | /api/v1/backtest/validation-tasks                   | 轻量元数据分页page/size                                   |
| GET  | /api/v1/backtest/validation-tasks/{id}              | 实际进度和已完成摘要，不含原始大证据                      |
| POST | /api/v1/admin/backtest-validation-tasks/{id}/cancel | 持久取消意图，已完成状态保持不变                          |
| GET  | /api/v1/backtest/validation-tasks/{id}/evidence     | 成功证据JSON附件；先核对SHA256，文件响应不包Result        |
| GET  | /api/v1/market/coverage?date=YYYY-MM-DD             | 同日清单、交易所实测数量和全A证明缺项，不采集             |

scope为ALL、TECHNICAL或MARKET。最多十年且不含未来日期，证券来自已启用自选；缺省名单在提交时冻结。仅真实模式可提交，既有记录可在其他模式读取。重复活动条件返回原任务，完成后可明确重跑。容量与完整复算见[历史验证](../runbook/historical-validation.md)。

同步GET /api/v1/backtest/research-validation已移除，刷新和GET不启动重计算。普通接口使用Result，完整证据下载为明确文件响应。

## 身份与权限

PUBLIC全部业务接口需要会话，auth/status、login和register为入口例外。登录/注册仍检查允许的Origin；其他写入需带X-CSRF-Token（值来自ALPHASCOPE_CSRF Cookie）和会话Cookie。会话Cookie为HttpOnly/Secure/SameSite=Strict。禁止URL或JSON传递tenantId切换空间。LOCAL接口文档可用，PUBLIC的Swagger/OpenAPI返回404。

| 方法 | 路径                        | 用途                                                                    |
| ---- | --------------------------- | ----------------------------------------------------------------------- |
| GET  | /auth/status                | 不含密钥的模式、邀请注册和持仓功能开关                                  |
| POST | /auth/login                 | username、password；签发Cookie，返回UserView                            |
| POST | /auth/register              | username、password、invitation；开启注册且匹配邀请方可创建普通用户      |
| GET  | /auth/me                    | 当前公开身份                                                            |
| POST | /auth/logout                | 撤销会话并清Cookie，仍需CSRF                                            |
| GET  | /admin/users                | 管理员账号分页；不返回密码或令牌                                        |
| PUT  | /admin/users/{id}           | 管理员重置普通用户密码或启停账号，撤销旧会话                            |
| GET  | /instruments/discover?q=... | 本地目录及真实候选、来源状态；不修改数据库                              |
| POST | /watchlist/discover         | symbol、initialize、date；重新核验候选，添加自选并可选排队初始化        |
| GET  | /workspace/status           | 自己的数据时效、任务与容量提示                                          |
| GET  | /admin/product/status       | 管理员许可、账号和运行配置摘要                                          |
| POST | /admin/product/licenses     | 来源、permissions、凭证、有效日期；仅登记操作方声明，不自动授予合同权限 |

`GET /api/v1/admin/product/status`的`sourceLicenseEnforced`返回当前实际许可检查开关：PUBLIC始终为true，LOCAL按显式配置决定。页面据此提示是否需要处理登记，不从运行模式猜测。`licenses[].status`是登记状态，不代表上游连接或数据可用性；接口不自动创建许可记录。

/admin/jobs、/admin/backtest-experiments和/admin/backtest-validation-tasks保留既有URL，但普通用户只能操作自己的记录；其他管理入口需要ADMIN。他人的报告、任务、验证和实验返回404。PUBLIC缺DISPLAY或EXPORT权限时对应研究展示/下载返回授权错误，后台收集与报告另检查COLLECT/DERIVED_REPORT。

未登录401，角色/CSRF/来源许可不足403，配额或限频429；服务异常带requestId。取消和退出不受普通写入的一秒节流，但仍必须通过身份与CSRF检查。CLI工具仅供本机维护，不是PUBLIC的无认证入口。

免费研究源增加MARKET_SNAPSHOT能力，PUBLIC同时检查报价与独立目录的COLLECT权限。capabilities状态ACCESS_COOLDOWN表示上游拒绝/限流后持久冷却，retryAt为实际终点；NOT_ARCHIVED表示该源不提供未保存的历史目录。GET仍只读；报告按原冻结输入展示，采集后需生成新修订才能引用新截面。

## 冻结量价研究

新生成报告的 `analysis.priceVolume` 按证券返回近两个自然月的量价指标、上方压力区间、原支撑、止损参考、减仓观察状态、证据与缺项。`input_snapshot.priceVolumeMethod` 和每只证券的 `expectedTradingDates` 一起冻结。旧修订缺少方法标识时不追加该字段，不能通过GET补算。字段与固定公式见[量价方法](../runbook/price-volume.md)。

新量价方法增加 `strategies`：策略代码、ALL/ANY逻辑、状态、候选价带、原参考、止损距离、逐项实测/要求/三态结果、失效条件和个人适用前提一起冻结。旧修订该字段为空且不补写。新建/加仓条件不能由“未减仓”倒推，详见[条件式策略](../runbook/price-volume.md#条件式建仓加仓和减仓)。

真实同日收盘任务在上海15:30前返回 `DATA_UNAVAILABLE`（HTTP 409），不会入队，也不会替换为上一交易日；15:30后仍需供应商日线实际齐备。行情采集失败公开设计的证券、窗口、缓存条数及通道状态，其他内部异常仍通过日志定位。

通道及能力响应新增可选 `diagnostic`（code/title/reason/impact/action），任务详情的阶段 `details.diagnostic` 保存当次诊断；任务顶层 `diagnostic` 解释已保存错误，不触发探测。旧字段保留，旧阶段没有诊断时为空，不伪造具体网络原因。

通道响应另含 `inactiveChannels`（channel/state/reason/action）：未列入当前路由为DISABLED，组合列入但缺Tushare Token为NOT_CONFIGURED，不推断上游权限。每项能力的 `lastFailure` 仅在实际失败时更新，冷却/查询/重启不补造时间；成功后的旧失败时间只表示曾经失败，不代表仍然失败。BaoStock预算响应的 `accessCode/accessObservedAt` 是原始保护证据，不能解读为刚刚联网确认。排障顺序见[行情通道手册](../runbook/data-providers.md#按通道定位和恢复)。

## 当前多周期研究

`GET /api/v1/instruments/{symbol}/holding-research?asOf=YYYY-MM-DD`：统一Result返回HOLDING_RESEARCH_V1。路径代码与日期查询对象分别绑定，日期必填且不得超出上海当前日期。读取已有同源行情、已公开累计财报和所选日期风险规则；不触发上游采集、持仓写入或报告生成。PUBLIC仍执行来源派生研究许可准入。

响应包含asOf、实际quoteDate、来源、已验证收盘、SHORT/MEDIUM/LONG三个周期、复用的两月量价研究和方法说明。每个周期分别列出行动状态、观察价带、准确用途的参考价、结构退出参考、ALL/ANY条件、实测值、三态met、失效条件及个人前提。缺估值时长期买入价为空；资料不足、停牌、陈旧和演示不输出可执行当前价位。此接口是当前保存版本回顾，不能冒充历史冻结报告。

## 供应商错误证据

可选 `diagnostic.providerError` 含 `provider/code/message/meaning/messageState`：供应商数字码与系统分类码分开；`meaning`是官方SDK代码的本地释义，`message`仅为实际捕获并命中安全模板的原文。`messageState` 为UPSTREAM_SAFE_TEXT、NOT_RECORDED或WITHHELD。旧记录原文缺失保持null，未知数字码仍保留，不退化为统一UNAVAILABLE。单能力保存当次受控诊断；任务聚合提示保留数字码和中文含义。

`/market/providers/baostock-policy` 新增 `accessError`，使用同一供应商证据结构解释已保存的拒绝；查询不会改写原始时间、补造官网限制次数或解除保护。详情见[错误码说明](../runbook/data-providers.md#供应商数字错误码)。

### 当前产业核心企业目录

`GET /themes/leaders`返回全部当前核心企业组，`GET /themes/{code}/leaders`返回单个主题，均使用统一Result。字段包括themeCode、title、marketAlias、reviewedAt、selectionBasis、limitation和members；每个成员保留displayOrder、symbol、name、tier、segment、standing、rationale、formalRanking及sources。displayOrder是研究展示序，formalRanking必须带年份和范围。接口只读静态研究目录、不联网、不写自选、不改变历史样本或评分；不能当作历史时点可得的名单。


## 个人计划、目录维护与历史补齐

| 方法 | 路径（/api/v1） | 行为 |
| --- | --- | --- |
| GET | /plans | 个人最近100份冻结计划，仅读取 |
| POST | /plans | symbol、date、horizon、actionCode；服务端捕获实际研究及原始因子，不接受价位或租户字段 |
| GET | /plans/{id}/reviews | 所属用户最近20次已保存复核，不重算 |
| POST | /plans/{id}/reviews | date；显式追加，不覆盖原风险线 |
| POST | /plans/{id}/archive | 停止跟踪并保留证据 |
| GET | /admin/themes/{code}/leaders | 管理员读取当前目录、修订主键与90天复核提醒 |
| POST | /admin/themes/{code}/leaders | expectedRevision、directory；锁主题、检查冲突、追加修订，不能倒填复核日期 |
| POST | /admin/jobs/market-history?date=YYYY-MM-DD | 管理员提交持久补齐任务，HTTP202；复用进度、取消和重试，不生成报告 |

目录冲突返回409；跨用户计划及复核按不存在处理。PUBLIC保留会话、CSRF与实际来源许可检查。计划原始研究和复核分别保存文本指纹；归档不删除证据。每位用户计划与复核总记录上限1000条，容量统计计入这些证据。

产品状态增加`runtimeIssues`，只列影响当前研究的问题；原`blockers`只列发布前待办。两者不能用来替代行情覆盖与方法效果判断。实际检查开关仍由`sourceLicenseEnforced`返回。

任务分类列表 `GET /admin/jobs?type=DAILY&page=1&size=20` 支持DAILY、WEEKLY、MARKET_HISTORY、MARKET_UNIVERSE和MARKET_FULL；省略type显示全部。`GET /admin/jobs/{id}/attempts?page=1&size=10`读取重试前的历史快照及旧版父重试链，按当前用户隔离。重复点击重试不会增加主列表记录；明确请求报告新修订仍产生新任务。

研究计划列表 `GET /plans` 使用page/size及symbol/horizon/archived筛选，只返回摘要；`GET /plans/{id}`读取完整冻结内容，`GET /plans/{id}/reviews?page=1&size=20`分页读取复核。

管理员 `PUT /admin/instruments/{symbol}/lifecycle` 使用listedAt、可空delistedAt、evidenceUrl和reference补录有公开依据的日期；这不会改写历史报告。`GET /admin/operations/storage`区分私人正文预算、平台正文预算、数据库物理分配和应用文件系统。`POST /admin/jobs/market-full?date=YYYY-MM-DD`显式核验三所同日证据，`GET /market/full-observation?date=YYYY-MM-DD`只读已归档结果；接口存在不代表真实历史完整。

# 后端代码导航

`com.alphascope` 下的模块化单体。启动类为 [AlphaScopeApplication](src/main/java/com/alphascope/AlphaScopeApplication.java)，运行配置为 [application.yml](src/main/resources/application.yml)。

## 模块职责

| 模块         | 职责                                       | 阅读入口                                             |
| ------------ | ------------------------------------------ | ---------------------------------------------------- |
| market       | 证券、日历、行情、截面、来源路由和市场观察 | `MarketService`、`MarketDataProvider`                |
| indicator    | 同源复权指标和技术证据                     | `IndicatorService`、`TechnicalEvidenceCalculator`    |
| fundamental  | 已披露财报及其来源、版本                   | `FundamentalService`、`FinancialWriteService`        |
| industry     | 时点主题成员、产业证据和强弱               | `IndustryService`、`EvidenceService`                 |
| signal       | 风险规则和证据                             | `SignalService`、`RiskRules`                         |
| portfolio    | 自选、首次初始化和手工持仓                 | `PortfolioService`、`WatchOnboardingService`         |
| intelligence | 冻结报告、多周期行动研究和版本对比               | `ReportService`、`HoldingResearchService`                 |
| scheduler    | 持久任务、调度、取消和恢复                 | `JobService`、`ResearchPipelineService`              |
| backtest     | 历史核验、冻结实验和离线复算               | `ValidationTaskService`、`ResearchValidationService` |
| identity     | 身份、账号维护和 SQL 用户隔离              | `AuthService`、`TenantContext`                       |
| operations   | 许可、配额、运行状态、告警及数据维护       | `ProductStatusService`、`DatabaseMaintenance`        |
| notification | 本地通知审计                               | `NotificationService`                                |
| common       | 公共协议、配置、异常与基础校验             | `Result`、`ApiExceptionHandler`                      |
| tools        | 显式开发命令与源码交付                     | `ProjectTools`、`SourceArchive`                      |

## 分层与文件位置

```text
模块/controller/          HTTP 映射、输入校验、Result<T> 响应
模块/service/             业务接口与方法 Javadoc
模块/service/impl/        业务实现、权限、事务及跨模块编排
模块/mapper/              类型化持久化契约
resources/mapper/模块/    唯一 SQL 实现
模块/entity/              与表字段对应的 @Data JavaBean
模块/dto/                 请求、内部命令和查询对象
模块/vo/                  明确的公开响应
模块/calculator|rule/     纯计算
模块/provider/            外部数据协议与能力差异
```

Controller 和跨模块调用依赖 Service 接口；Mapper 由 MyBatis 代理，不手写 MapperImpl。固定字段采用具名对象；仅动态证据允许 Map。不可变 DTO/VO 使用 record，无需 `@Data`。完整约束统一维护在[项目约定](../docs/architecture/project-conventions.md)。

数据库仅维护 [V1\_\_baseline.sql](src/main/resources/db/migration/V1__baseline.sql)，使用方式见[数据库入口](../database/README.md)。

## 建议阅读顺序

1. **财报录入**：[FinancialInput](src/main/java/com/alphascope/fundamental/dto/FinancialInput.java) → [FinancialWriteController](src/main/java/com/alphascope/fundamental/controller/FinancialWriteController.java) → [FinancialWriteServiceImpl](src/main/java/com/alphascope/fundamental/service/impl/FinancialWriteServiceImpl.java) → [FinancialWriteMapper.xml](src/main/resources/mapper/fundamental/FinancialWriteMapper.xml)。字段、来源、累计口径和版本约束沿同一条链核对。
2. **任务到报告**：`JobService` → `ResearchPipelineService` → 行情、指标及规则 Service → `ReportService`。新报告冻结输入；旧报告按冻结内容读取，不重新解释为新算法。
3. **免费市场截面**：`EastmoneyResearchProvider` → `FreeMarketNormalizer` → `MarketBreadthService`。目录、日期报价、停牌和访问冷却各自验证；来源证据与数据缺项保留，见[数据源手册](../docs/runbook/data-providers.md)。
4. **研究复算**：`ValidationTaskService` → `ValidationDataService` 一致读 → `ResearchValidationService` 事务外计算 → `ResearchValidationVerifier`。生产与回顾共用技术计算，见[历史验证](../docs/runbook/historical-validation.md)。
5. **用户边界**：`AuthService` → `TenantContext` → `TenantSqlConfig`。后台任务恢复用户身份，私人表自然键含用户字段，见[多用户设计](../docs/architecture/multi-user.md)。

## 开发与交付

从项目根目录执行：

```bash
bash scripts/run-backend.sh
./mvnw -pl backend spotless:check test
```

IDE 使用 Java 21，导入根 POM 并启用 Lombok 注解处理。常规后端开发无需前端产物；生产同源 JAR 先构建前端，再执行 `./mvnw -pl backend -Pproduction-web clean package`。

[POM、完整测试和构建](../docs/runbook/build-and-test.md) · [接口](../docs/api/endpoints.md) · [数据字典](../docs/database/schema.md) · [当前验收](../docs/verification/README.md)

近两月量价从 `PriceVolumeAnalysisBuilder` 读取本修订方法及日历，交给 `PriceVolumeCalculator` 纯计算；输出 `PriceVolumeStudy`。价格按同源因子复权，量能用人民币成交额，不能把价格因子当股本因子。公式与旧修订兼容见[量价方法](../docs/runbook/price-volume.md)。

条件式仓位方案由 `PositionStrategyCalculator` 从具名 `PositionStrategyInput` 计算，建仓/加仓ALL与减仓ANY逻辑分开；主量价风险标志只判定一次，策略直接复用，避免舍入造成风险结论不一致。旧报告不补写新策略字段。

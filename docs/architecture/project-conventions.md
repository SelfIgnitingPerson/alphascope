# 项目结构与开发约定

本页只维护开发规则。模块入口见[后端导航](../../backend/README.md)和[前端导航](../../frontend/README.md)，实际验收见[当前版本](../verification/README.md)。

## 目录与版本

| 位置                      | 唯一职责                                 |
| ------------------------- | ---------------------------------------- |
| `backend/`、`frontend/`   | 后端与前端源码、资源和测试               |
| `docs/`                   | 当前架构、接口、字典、手册和验收         |
| `database/`               | 数据库使用入口；不保存第二份 DDL         |
| `scripts/`                | 开发命令与质量检查；不复制业务流水线     |
| `deploy/`                 | 生产运行配置；开发数据库在根 Compose     |
| `.runtime/`、`artifacts/` | 本机运行证据、日志和交付产物，不属于源码 |

Maven Wrapper 仅根目录一套。根 POM 管理工程版本、Java 和自定义依赖版本，后端 POM 声明实际依赖。前端使用精确版本与锁文件。基础文本格式由 `.editorconfig` 管理，Java 用 Spotless，前端用 Prettier。

产品版本、算法版本、报告修订和数据库迁移各有用途。算法标识只在 `ResearchVersion` 维护，已保存报告保留冻结读取兼容。开发期仅维护唯一 `V1__baseline.sql`；已有库不得自动清理、重放种子或修改校验和，处理规则见[数据库入口](../../database/README.md)。

## 后端分层

```text
Controller → Service 接口 → ServiceImpl → Mapper / XML
                            ├─ calculator / rule / builder：计算与构建
                            ├─ validator / policy：校验与策略
                            └─ provider：外部数据协议
```

- Controller 负责 HTTP、输入校验和响应转换，不直接依赖 Mapper。跨模块调用依赖公开 Service 接口，不导入其他 `service.impl`。
- Service 接口声明业务契约，实现类放在 `service/impl` 并标注 `@Service`、`@Override`。接口不内嵌业务模型或静态业务实现。
- 网络和模型请求放在写事务外；有关联的数据库提交保持短事务。后台任务通过统一任务入口编排，并恢复任务所属用户身份。
- `entity` 为与表对应的内部 JavaBean，使用 Lombok `@Data`；`dto` 为请求、命令、查询及联查结果；`vo` 为公开响应。不可变 record 不叠加 `@Data`。
- 固定输入输出采用类型化对象和明确字段转换，不用 Map 或通用 JSON/反射转换器隐藏映射。动态证据、供应商原始协议和按代码索引的字典可使用 Map。
- 金额使用 BigDecimal，业务日期使用 LocalDate/LocalDateTime；实体 Boolean 属性不加 is 前缀。数据库及旧 JSON 名称通过 resultMap、`@JsonProperty` 显式兼容。

## HTTP 与持久化契约

| 边界         | 要求                                                                                                      |
| ------------ | --------------------------------------------------------------------------------------------------------- |
| HTTP 请求    | 成组日期、分页和筛选使用具名请求 DTO；`@Valid @ModelAttribute` 与 `@ParameterObject` 维护绑定、校验和文档 |
| 路径标识     | `@PathVariable` 单独接收，不能被同名查询参数覆盖                                                          |
| 参数数量     | Controller 最多两个业务入参；Mapper 超过三个独立参数时收敛为 Query、Command 或已有实体；构造器不机械包装  |
| 内部条件     | 来源、模式、权限由业务层确定，不暴露为可由客户端任意指定的内部 Query                                      |
| 成功与失败   | `/api/v1` 使用 `Result<T>`，提交任务保留 HTTP 202；框架协议和显式证据文件下载按自身协议返回               |
| 字段转换     | 实体/DO 到 VO 使用明确 `from` 或显式构造；分页统计与数据共用查询条件，在 Service 组装                     |
| Mapper       | 接口只声明类型化方法和必要 `@Param`，不接收/返回固定字段 Map，不放 SQL 注解                               |
| SQL          | 唯一实现放在 `resources/mapper/<模块>/`；明确列、分行子句，使用 `#{}` 绑定和 `<sql>/<include>` 复用       |
| 隔离与不可变 | 查询保留来源、模式、用户和业务时点条件；不可变实验、版本和冻结报告不得覆盖                                |

分页共用 `PageRequest`，日期顺序共用 `DateRangeValidator`。Controller 不把请求对象重新拆成长参数；Service 仍校验绕过 HTTP 的内部调用。对象优先使用 builder 和业务全称，避免多个 `at/url/hash/raw` 位置参数串位。

## 注释与可读性

所有类型的 Javadoc 放在注解之前，说明实际职责并保留作者。数据库实体示例：

```java
/**
 * 数据库记录实体，字段与当前SQL基线对应；不直接暴露为接口响应。
 *
 * @author chenxuefeng
 */
```

- 所有接口方法，包括 Mapper、供应商默认方法，提供标准 Javadoc；每个参数有 `@param`，非 void 有 `@return`，说明空值、排序、时点和调用方需要处理的异常。
- 锁方法说明事务、连接与释放边界；写方法说明主键、幂等、版本和覆盖语义。说明必须与 SQL、实际校验一致。
- DTO、VO、实体及具名内部 record 的字段说明单位、可空性、状态和业务时点。累计财报、同源因子、模式隔离等跨字段约束写在类型或方法契约中。
- 注释解释原因和业务边界，不逐行复述代码，不声称尚未实现的能力。Java 使用明确导入和大括号；复杂分支提取为职责明确的方法。
- 纯可读性整理不改变算法、冻结内容或接口字段；行为变化另行更新算法标识与相应验证。

## 前端约定

`main.ts` 装配应用，`plugins/` 注册公共 UI，`App.vue` 处理身份与布局。页面通过业务 `api/` 调用，类型按模块在 `types/` 维护，`models.ts` 只导出。查询和写入采用具名对象，路径标识与取消信号单独传递。

`useResource` 维护加载、取消和旧响应隔离，`useMutation` 防止重复提交，HTTP 超时与错误由 `api/client` 处理。页面不直接调用底层客户端，不自行重做后台任务。

样式从 `styles/app.css` 顺序加载。公共样式在基础/布局/组件文件；业务样式在 `styles/pages/`，`pages.css` 只编排。未知评分、未触发、证据不足和不适用分别显示；行情日期、采集时间、当前通道状态分别表达。

## 提交前检查

| 变化       | 必须核对                                                   |
| ---------- | ---------------------------------------------------------- |
| HTTP       | DTO/VO、前端类型、接口说明、相关契约测试                   |
| 数据库     | 唯一基线、数据字典、空库及保留数据的同步测试               |
| 行情与计算 | 日期、来源、单位、复权、缺项、算法标识、固定样本与时点回归 |
| 配置与脚本 | 环境模板、运行说明、语法、有效配置及实际命令               |
| 依赖与构建 | 父 POM/锁文件、实际解析、格式、构建与产物                  |

`node scripts/quality/check-project.mjs` 核对唯一 SQL、临时残留和文档本地链接。`ProjectArchitectureTest` 检查分层、Javadoc、作者、明确导入及 Mapper/HTTP 契约；`ViewMappingContractTest` 检查公开字段和固定 JSON。共同入口为 `bash scripts/verify.sh`，完整回归见[构建手册](../runbook/build-and-test.md)。

规范参考[阿里巴巴 Java 开发规范](https://github.com/alibaba/p3c)中适用的分层、命名、POJO、SQL、异常与注释要求；通过本项目检查不等于已覆盖其全部条款。

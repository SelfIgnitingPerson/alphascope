# Maven、测试与交付

除前端命令块外，本页命令均在项目根目录执行。使用 Java 21 和仓库自带 Maven Wrapper；本机检测失败时先配置 `JAVA_HOME`。

## POM 的职责

```text
Spring Boot parent 3.5.15
└── pom.xml                  AlphaScope 父工程及聚合入口
    └── backend/pom.xml      可执行后端模块
```

| 配置位置                                                    | 维护内容                                                      |
| ----------------------------------------------------------- | ------------------------------------------------------------- |
| [根 POM](../../pom.xml)                                     | 工程版本、模块、Java 版本、自定义依赖版本、格式与开发插件规则 |
| [后端 POM](../../backend/pom.xml)                           | 模块实际需要的依赖、测试范围和插件启用                        |
| [.mvn/wrapper](../../.mvn/wrapper/maven-wrapper.properties) | Maven 3.9.9 分发版本                                          |
| [frontend/package.json](../../frontend/package.json)        | 前端依赖与 npm 命令；锁文件记录完整解析结果                   |

子模块不重复声明工程版本或 Spring Boot 父配置。Maven 编译统一 `release=21`；Lombok 版本跟随 Boot，后端编译插件显式声明注解处理路径，不再出现 source/target 16 覆盖。`dependencyManagement` 只管理版本，不会自动加入依赖；`pluginManagement` 定义插件规则，子模块通过 `plugins` 启用。

### 版本由谁管理

| 依赖 / 插件                                | 管理方式                                                             |
| ------------------------------------------ | -------------------------------------------------------------------- |
| Spring Boot 3.5.15                         | 根 POM 的父版本                                                      |
| Java 21                                    | 根 POM `java.version`                                                |
| Spring AI 1.0.9、MyBatis starter 3.0.4     | 根 POM 属性及 `dependencyManagement`                                 |
| MyBatis-Plus SQL解析 3.5.18.1              | 根POM管理，专用于租户SQL语法树拦截                                   |
| springdoc 2.8.9、Rhino 1.7.15              | 根 POM 属性及 `dependencyManagement`                                 |
| Lombok                                     | Spring Boot 依赖管理，编译期 provided；显式注解处理器，运行 JAR 排除 |
| MySQL 驱动、Flyway、JUnit、Testcontainers  | Spring Boot 依赖管理；Testcontainers 当前统一为 1.21.4               |
| Spotless 2.43.0、Google Java Format 1.24.0 | 根 POM `pluginManagement`                                            |
| exec-maven-plugin 3.5.0                    | 根 POM `pluginManagement`，供 `scripts/tools.sh` 使用                |
| Vue / Vite / 测试框架等前端依赖            | `package.json` 精确版本与 `package-lock.json`                        |

变更后需要核对实际解析结果时运行 `./mvnw -pl backend dependency:tree`。Testcontainers 的 MySQL/JUnit 适配组件与核心库统一跟随 Boot 管理。

## 检查与测试

联合检查先运行 `node scripts/quality/check-project.mjs`，核对唯一 SQL、源码临时残留及文档本地链接；不读取本机配置或业务库。

| 范围                 | 命令                                     | 是否需要 Docker                        |
| -------------------- | ---------------------------------------- | -------------------------------------- |
| 日常联合检查         | `bash scripts/verify.sh`                 | 否；默认不运行容器测试或Compose检查 |
| Java 格式与普通测试  | `./mvnw -pl backend spotless:check test` | 容器测试默认跳过                       |
| 自动整理 Java 格式   | `./mvnw -pl backend spotless:apply`      | 否，会改源文件格式                     |
| 完整 Java 回归与打包 | 见下方命令                               | 是，创建独立临时 MySQL                 |

```bash
RUN_DB_TESTS=true RUN_CONTAINER_TESTS=true ./mvnw -pl backend clean spotless:check verify
```

macOS 若 Testcontainers 无法找到 Docker，可先设置：

```bash
export DOCKER_HOST="unix://$HOME/.docker/run/docker.sock"
```

日常开发使用本机MySQL，运行应用不需要Docker。`VERIFY_DOCKER_CONFIG=true bash scripts/verify.sh`才额外校验可选容器配置；GitLab CI仍执行完整隔离与容器检查。

数据库测试使用临时容器，不接入当前业务库。`test` 执行测试，`package` 继续生成 JAR，`verify` 执行到验证阶段；需要快速打包时可用 `-DskipTests package`，但不能将跳过测试称为验收通过。

删除或移动Java类型后使用 `clean`，避免旧class残留在交付JAR。普通测试同时包含任务硬截止、持久取消、成熟样本/留出区间、能力路由、源代码架构约束、请求对象绑定、响应字段兼容和证据指纹格式验证；容器测试继续核对真实MySQL语义，并将备份实际导入第二个隔离MySQL核验内容；须预备mysql:8.0.40镜像。

前端命令：

```bash
cd frontend
npm ci
npm run format:check
npm test
npm run build
```

`.npmrc` 固定 peer 依赖解析方式，避免已观察到的特定 npm 解析器崩溃；Node 版本见 `.nvmrc` 与 `package.json`。测试输出在终端，Java 详细报告在 `backend/target/surefire-reports/`，测试滚动日志在 `backend/target/test-logs/`，由clean统一清理，不混入业务运行目录。

## 构建与交付产物

| 产物                                          | 生成方式                        | 内容                            |
| --------------------------------------------- | ------------------------------- | ------------------------------- |
| `backend/target/alphascope-backend-0.1.0.jar` | `./mvnw -pl backend package`    | 后端可执行 JAR                  |
| `frontend/dist/`                              | `npm run build`                 | 前端静态文件                    |
| `artifacts/alphascope-source.zip`             | `bash scripts/tools.sh package` | 源码、构建配置、文档、测试和 CI |

源码包排除 `.env`、依赖目录、编译产物、日志、运行副本和数据库备份。运行中的 JAR 使用 `.runtime/` 内副本，避免 Maven 打包覆盖运行文件。

## CI

[GitLab CI](../../.gitlab-ci.yml)使用带alphascope标签的受信任Linux Runner，预装Java21、Node22、Docker、Chrome和OpenSSL。执行完整测试、双浏览器流程、前端构建、生产JAR、Compose检查、npm/OSV依赖审计和源码打包；安全扫描失败阻断打包。当前没有远程仓库与Runner，本机结果不代表远程流水线已执行。

当前版本的测试和真实行情验证见[版本与验收](../verification/README.md)。

## 真正浏览器流程回归

先在frontend安装锁定依赖，再从根目录执行：

```bash
RUN_DB_TESTS=true RUN_CONTAINER_TESTS=true RUN_BROWSER_TESTS=true ./mvnw -pl backend clean spotless:check verify
```

需要本机Chrome/Chromium，可用CHROME_BINARY指定路径，NODE_BINARY可指定Node可执行文件。BrowserWorkflowIntegrationTest启动独立MySQL、随机端口的Spring后端、独立Vite和临时Chrome配置；脚本明确拒绝用户8080端口，并检查DEMO范围。测试输入只写临时库，无真实供应商网络请求。

流程实际点击提交、运行中取消、重试和报告详情，核对父任务、冻结实验复算、模拟保存禁用、运维状态及五维分项。证据在backend/target/browser-test，包括workflow.json和截图；失败保存DOM及网络诊断。普通日常单元测试不要求Chrome，显式RUN_BROWSER_TESTS=true时缺浏览器会失败而不是冒充通过。

Runner预装Chrome或设置CHROME_BINARY。另有PublicBrowserWorkflowIntegrationTest，在隔离HTTPS和临时MySQL中执行双用户注册、登录、自选初始化、报告隔离及管理员拒绝，证据在backend/target/public-browser-test。测试证书只用于隔离环境，生产仍需要真实域名TLS。

## 同源生产JAR和安全资料

```bash
npm --prefix frontend run build
./mvnw -pl backend -Pproduction-web clean package
bash scripts/quality/security-audit.sh
node scripts/quality/release-manifest.mjs
```

production-web只复制dist到JAR的static目录，不产生第二套源码。安全脚本生成CycloneDX SBOM，执行npm官方审计及固定版本OSV扫描，再导出依赖许可证清单；清单需要审阅，不能把无扫描结果标为安全。镜像下载或扫描失败将返回非零。实际扫描状态见[当前验收](../verification/README.md)。

release-manifest记录实际JAR、SQL、前端锁文件及Dockerfile的SHA256；无Git提交时明确LOCAL_UNPUBLISHED，状态NOT_DEPLOYED。当前未提供仓库，不初始化或推送远程项目。生产配置见[部署入口](../../deploy/README.md)。

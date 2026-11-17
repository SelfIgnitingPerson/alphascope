# 前端代码导航

Vue 3 + TypeScript + Vite，使用 Hash 路由。依赖由 `package.json` 声明、`package-lock.json` 锁定；推荐 Node 22 LTS 或 24+，版本选择记录在 [.nvmrc](.nvmrc)。

## 目录与数据流

```text
src/
├── App.vue                身份就绪后挂载登录页或用户工作台
├── layouts/               导航、页头和全局状态
├── plugins/               公共 UI 组件的按需注册
├── router/                页面路由、标题与导航分组
├── views/                 路由页面，按需加载
├── api/                   按业务划分的 HTTP 请求
├── content/               状态释义、股票术语及规则名称对应关系
├── types/                 按业务模块维护的 REST 边界类型
├── composables/           读取与写操作状态
├── components/common/     卡片、状态、加载与错误组件
├── components/charts/     K 线及指标图表
├── utils/                 日期、数值和来源格式化
└── styles/                变量、布局、公共组件、业务页面与响应式样式
```

页面通过 `api` 获取数据；`useResource` 负责超时以外的页面读取生命周期、取消旧请求和防止旧响应回写，`useMutation` 负责防重复提交。HTTP 超时和错误编号由 `api/client.ts` 统一处理。

`types/models.ts` 仅导出各模块定义，实际类型在 `common.ts`、`market.ts`、`signals.ts`、`fundamental.ts`、`industry.ts`、`portfolio.ts`、`reports.ts`、`jobs.ts`；`requests.ts` 放具名查询和写入参数。报告查询、日期区间、任务提交和人工持仓使用对象，避免多个字符串或布尔参数串位。页面通过业务API封装读取，不直接依赖底层客户端。

报告列表支持日期、分页和修订分组；报告详情支持版本对比及冻结证据；任务页面提交后轮询，离开页面会停止读取，后台任务继续执行。

## 常用命令

以下命令在 `frontend/` 目录执行：

| 命令                   | 用途                                           |
| ---------------------- | ---------------------------------------------- |
| `npm ci`               | 按锁文件安装，自动读取 `.npmrc` 的依赖解析配置 |
| `npm run dev`          | 启动本机开发服务，默认 5173                    |
| `npm test`             | 执行 Vitest 交互与请求管理测试                 |
| `npm run test:watch`   | 开发时持续测试                                 |
| `npm run format`       | 写入统一格式                                   |
| `npm run format:check` | 只检查格式                                     |
| `npm run build`        | TypeScript 检查并生成 `dist/`                  |

开发服务通过Vite代理访问后端。生产使用production-web Maven配置将dist嵌入JAR，由同源TLS网关代理，不使用Vite作为生产服务。

接口字段见 [REST API](../docs/api/endpoints.md)，测试与 CI 见[构建与测试](../docs/runbook/build-and-test.md)。

PUBLIC先读取auth/status并恢复会话，未登录显示登录/邀请注册页；切换账号重建工作台，过期会话移除旧私人页面。会话令牌由HttpOnly Cookie管理，写请求从CSRF Cookie附请求头，不把密码或会话放入localStorage。管理员入口只对管理员显示，后端仍独立检查权限。

Dashboard各卡片分别加载、失败和重试，单只自选异常不会阻断其他证券。AsyncState支持保留并注明过期内容。基础组件只同步加载必要项，其余按需加载，图表按渲染器分块；不通过提高阈值隐藏构建警告。ProductView显示来源许可、账号支持、队列及存储状态；许可登记不等于已获得数据商授权。

## 样式定位

`styles/app.css` 是唯一入口，按变量、基础、布局、公共组件、页面、响应式加载。`styles/pages.css` 只编排业务文件；实际样式位于 `styles/pages/`：overview（首页/主题）、research（个股/持仓）、reports（列表/正文）、operations（任务/通道）、report-detail（事实/对比）、identity（登录）。修改页面先定位对应文件，避免在总入口末尾追加覆盖。

`main.ts` 只装配应用；Element Plus 的同步/异步组件和样式集中在 `plugins/element-plus.ts`。

## 状态与术语说明

页面右上角“术语与状态”和侧栏“帮助中心 → 术语与状态”均进入 `/#/terms`，按规则风险、价格量能、财报预测、市场主题、研究方法、数据与任务状态六个入口展示，一次阅读一类；输入关键词跨分类搜索，状态按用途折叠。规则表前说明已触发、未触发、数据不足、不适用与风险级别的区别；状态和带问号的术语可点击展开，Escape 关闭并返回触发位置。

释义只维护在 `content/status-help.ts`、`content/research-terms.ts`；`StateBadge`、`TermHelp`、`RuleStatusGuide` 共用这些定义。`HelpPopover` 将说明传送至 body，避免被表格裁切；滚动时跟随锚点，离开可见区域关闭，卸载时移除监听。列表链接内使用非交互状态，避免嵌套按钮。新增规则或调整指标公式时，应同步修改释义与契约测试；未知代码保留原值，不猜测定义。

说明以本项目实际计算口径为准，明确区分技术指标 Wilder ATR14 与量价止损前14期简单平均，以及已披露财报与未来机构预测。此入口不发起行情请求，不改写历史报告、不修改评分阈值。

`FailureExplanation`统一展示服务端受控诊断（原因/影响/操作/定位代码）；通道使用折叠说明，任务阶段默认展开。旧任务缺诊断时显示已保存错误的当前释义，不发起网络探测。API客户端只在fetch边界把浏览器连接失败映射为NETWORK_ERROR，主动取消不提示故障，写请求超时提醒先查询结果。

报告详情按重点概览、个股研究、市场与预测、正文、证据与版本五类阅读；切换只使用该修订冻结数据，不重新计算。ReportFacts按section与symbol显示对应部分，默认all供兼容；概览按实际触发与否突出需关注标的，点击卡片进入对应个股，财报和同业明细默认收起。标签支持方向键、Home/End，切换报告重置概览。JSON证据和版本对比只在对应标签展示。

## 页面布局约定

历史信号按“信号回顾 / 研究验证 / 复算归档 / 方法说明”阅读，默认先显示查询与样本结果。研究验证保留提交表单和当前任务，任务列表折叠后按需展开；收益中位数、基准均值与排除原因放在信号表的展开行。个股研究、主题研究、产品管理也按职责分类，机构预测一次查看一只证券；主题历史按最近日期优先分页，完整已读取数据仍可翻页查看。

`SectionTabs`统一原生标签的ARIA关系与方向键、Home/End操作，用`v-show`保留面板实例，切换分类不丢失表单或停止验证轮询。跨页面筛选使用`filter-bar`与`filter-field`：标签在上、32px控件等高，窄屏自动换行；卡片标题旁只放简短操作，详细筛选放正文。宽表格设置列的最小宽度，在表格内部滚动，不撑开整页。关键结论、数据不足和统计边界保持可见，原始证据与次级说明按需展开。

产业链入口逐主题读取已保存的观察，一项读取失败不隐藏其他主题；已登记样本与待建设分类分组展示。`components/themes/ThemeObservation.vue`共用评分、相对强度、均线广度与样本覆盖的显示，所有值来自后端存量记录，不在前端重算分数。详情分别读取样本与独立产业指标，展示披露定义的公开依据、页码和真实可用日期。样本覆盖与行业覆盖分开说明，无成员、缺指标、旧观测和读取失败分别处理。

使用说明`HelpView`（`/#/help`）和术语状态`TermsView`（`/#/terms`）各有独立路由、标题与导航。使用说明保留市场洞察→自选→同步→三周期计划→报告→验证的六步操作与模块分组；术语页独立搜索分类。旧`/help?term=...`、`?view=terms`及`?section=states`链接统一跳到术语页并保留定位，状态深链直接打开数据与任务状态分类；两个帮助页面均隐藏业务日期。个股默认进入`HoldingResearchPanel`，按短、中、长期切换同一份只读结果；数据接口失败局限于该面板。当前计划与报告冻结证据明确区分，旧报告只增加跳转入口，不追加分析。


研究计划页位于`views/ResearchPlansView.vue`；个股方案内的`SaveResearchPlan`仅提交研究身份，价格由服务端捕获。`StrategySimulationPanel`与`ValidationMarketPanel`分别展示执行假设和市场验证；`ChannelCapabilities`只负责通道能力对照。管理员`ThemeLeaderEditor`按需读取、显式保存当前目录，不通过刷新修改数据。

import { createRouter, createWebHashHistory, type RouteLocationNormalized } from 'vue-router';

/** 保留原hash地址；路由负责页面映射与元数据，业务数据由各页面自己加载。 */
export const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', redirect: '/dashboard' },
    {
      path: '/dashboard',
      component: () => import('../views/DashboardView.vue'),
      meta: {
        title: '市场总览',
        description: '先看需要优先核对的风险，再跟踪观察条件和市场背景。',
        section: '/dashboard',
      },
    },
    {
      path: '/themes',
      component: () => import('../views/ThemeListView.vue'),
      meta: {
        title: 'AI 产业链',
        description: '沿产业链查看主题，评分同时展示成分覆盖情况。',
        section: '/themes',
      },
    },
    {
      path: '/themes/:code',
      component: () => import('../views/ThemeDetailView.vue'),
      props: true,
      meta: {
        title: '主题研究',
        description: '历史有效成分、相对强度与产业证据。',
        section: '/themes',
      },
    },
    {
      path: '/stocks/:symbol',
      component: () => import('../views/StockDetailView.vue'),
      props: (route) => ({
        symbol: route.params.symbol,
        initialHorizon: ['SHORT', 'MEDIUM', 'LONG'].includes(String(route.query.horizon))
          ? String(route.query.horizon)
          : undefined,
      }),
      meta: {
        title: '个股研究',
        description: '按短、中、长期查看持有判断、买卖条件与原始证据。',
        section: '/watchlist',
      },
    },
    {
      path: '/watchlist',
      component: () => import('../views/WatchlistView.vue'),
      meta: {
        title: '自选股',
        description: '先看自选重点与风险，按需维护关注范围。',
        section: '/watchlist',
      },
    },
    {
      path: '/expectations',
      component: () => import('../views/ExpectationsView.vue'),
      meta: {
        title: '机构预测',
        description: '查看公开盈利预测、实际观测时点与已保存的同年变化。',
        section: '/expectations',
      },
    },
    {
      path: '/portfolio',
      component: () => import('../views/PortfolioView.vue'),
      meta: {
        title: '持仓管理',
        description: '手工登记资产和持仓，复核集中度风险。',
        section: '/portfolio',
      },
    },
    {
      path: '/reports',
      component: () => import('../views/ReportListView.vue'),
      meta: {
        title: '研究报告',
        description: '按业务日期查看日报与周报，保留每次修订。',
        section: '/reports',
      },
    },
    {
      path: '/reports/:id(\\d+)',
      component: () => import('../views/ReportDetailView.vue'),
      props: (route) => ({ reportId: Number(route.params.id) }),
      meta: {
        title: '报告详情',
        description: '按报告冻结日期阅读事实、推断、风险与待验证事项。',
        section: '/reports',
        hideBusinessDate: true,
      },
    },
    {
      path: '/jobs',
      component: () => import('../views/JobsView.vue'),
      meta: {
        title: '任务中心',
        description: '查看执行阶段、失败原因，按业务日期补跑。',
        section: '/jobs',
      },
    },
    {
      path: '/channels',
      component: () => import('../views/ChannelsView.vue'),
      meta: {
        title: '行情通道',
        description: '通道当前状态与已入库数据来源分开查看。',
        section: '/channels',
        hideBusinessDate: true,
      },
    },
    {
      path: '/operations',
      component: () => import('../views/OperationsView.vue'),
      meta: {
        title: '运行检查',
        description: '核对行情时效、备份结果和第二存储。',
        section: '/operations',
        hideBusinessDate: true,
      },
    },
    {
      path: '/product',
      component: () => import('../views/ProductView.vue'),
      meta: {
        title: '产品与账号',
        description: '来源许可、运行就绪与账号支持。',
        section: '/product',
        hideBusinessDate: true,
      },
    },
    {
      path: '/plans',
      component: () => import('../views/ResearchPlansView.vue'),
      props: (route) => ({
        initialPlanId: /^\d+$/.test(String(route.query.plan || ''))
          ? Number(route.query.plan)
          : undefined,
      }),
      meta: {
        title: '研究计划',
        description: '冻结原始依据，按研究日追加复核，跟踪变化与失效条件。',
        section: '/plans',
      },
    },
    {
      path: '/backtest',
      component: () => import('../views/BacktestView.vue'),
      meta: {
        title: '历史信号',
        description: '复核历史信号后的表现，关注样本数量与研究边界。',
        section: '/backtest',
      },
    },
    {
      path: '/help',
      component: () => import('../views/HelpView.vue'),
      meta: {
        title: '使用说明',
        description: '从快速上手到各模块串联，按步骤完成市场研究。',
        section: '/help',
        hideBusinessDate: true,
      },
    },
    {
      path: '/terms',
      component: () => import('../views/TermsView.vue'),
      meta: {
        title: '术语与状态',
        description: '按分类查阅股票术语、规则含义与数据和任务状态。',
        section: '/terms',
        hideBusinessDate: true,
      },
    },
    {
      path: '/:pathMatch(.*)*',
      component: () => import('../views/NotFoundView.vue'),
      meta: {
        title: '页面不存在',
        description: '请从左侧导航选择工作区。',
        hideBusinessDate: true,
      },
    },
  ],
});

/** 兼容旧术语链接，保留具体词条、状态分类及锚点；同页查询参数切换也生效。 */
export function redirectLegacyHelp(to: Pick<RouteLocationNormalized, 'path' | 'query' | 'hash'>) {
  if (
    to.path !== '/help' ||
    !(to.query.view === 'terms' || to.query.term || to.query.section === 'states')
  )
    return true;
  const query = { ...to.query };
  delete query.view;
  return { path: '/terms', query, hash: to.hash, replace: true };
}
router.beforeEach(redirectLegacyHelp);

import type { ObservationSummaryData } from '../types/research-priority';
import type {
  ReportAnalysis,
  ReportAssessment,
  ReportStockFact,
  ForecastView,
  ThemeStrength,
} from '../types/models';
import { number, percent, timestamp } from './format';

/** 报告关注顺序只用本修订冻结事实，禁止拿当前行情补写历史风险。 */
export function reportStockPriority(input: {
  analysis: ReportAnalysis;
  stock: ReportStockFact;
  date?: string;
}) {
  const { analysis, stock, date } = input;
  const study = analysis.priceVolume?.find(
    (p) => p.symbol === stock.symbol && p.status === 'AVAILABLE' && (!date || p.asOf === date),
  );
  const rules = analysis.assessments
    .filter(
      (r) => r.symbol === stock.symbol && r.status === 'TRIGGERED' && (!date || r.date === date),
    )
    .sort((a, b) => Number(b.severity === 'HIGH') - Number(a.severity === 'HIGH'));
  const high = rules.filter((r) => r.severity === 'HIGH');
  const breach =
    study?.stopStatus === 'BREACHED' &&
    stock.status === 'TRADING' &&
    (!date || stock.asOf === date);
  return {
    study,
    rules,
    high,
    breach,
    order: breach
      ? 0
      : high.length
        ? 1
        : rules.length
          ? 2
          : stock.status !== 'TRADING' || (date && stock.asOf !== date) || stock.insufficient > 0
            ? 3
            : 4,
  };
}
function evidence(rule: ReportAssessment) {
  return rule.evidence
    .slice(0, 2)
    .map(
      (value) =>
        `${value.label}${value.unit === 'PERCENT' ? percent(value.value, 2) : number(value.value) + (value.unit === 'CNY' ? '元' : value.unit === 'TIMES' ? '倍' : '')}`,
    )
    .join('；');
}
/** 先把最重要的冻结风险及下一次核对提到报告首页。 */
export function reportObservation(input: {
  analysis: ReportAnalysis;
  date?: string;
}): ObservationSummaryData {
  const { analysis, date } = input;
  const ordered = analysis.stocks
    .map((stock) => ({ stock, ...reportStockPriority({ analysis, stock, date }) }))
    .sort((a, b) => a.order - b.order);
  const first = ordered[0];
  if (!first)
    return {
      level: 'DATA',
      title: '本修订缺少可用个股事实',
      explanation: '不补造个股判断，请查看冻结正文和原始证据。',
      facts: analysis.missingData.slice(0, 3),
      nextChecks: ['核对本修订的数据日期与缺项，再决定是否补跑新修订。'],
    };
  const checks = analysis.checks
    .filter((c) => c.symbol === first.stock.symbol)
    .map((c) => `${c.title}：${c.detail}`);
  if (first.breach)
    return {
      level: 'FIRST',
      title: `先核对${first.stock.name}的结构风险`,
      explanation: '本报告冻结时已触及结构参考；以下是报告当期事实，不代表当前行情或个人委托结果。',
      facts: [
        `收盘${number(first.study!.close)}元；本期结构参考${number(first.study!.stopReference)}元`,
        ...first.rules.slice(0, 2).map((r) => `${r.ruleName}：${evidence(r)}`),
      ],
      nextChecks: checks.slice(0, 2).length
        ? checks.slice(0, 2)
        : ['按自己的原计划风险边界核对，不能用后来行情改写这份历史结论。'],
    };
  if (first.rules.length)
    return {
      level: first.high.length ? 'IMPORTANT' : 'WATCH',
      title: `先看${first.stock.name}：${first.rules[0]!.ruleName}`,
      explanation: `本修订共有${ordered.filter((s) => s.rules.length).length}只证券保存了已触发规则；资料不足与已触发分开核对。`,
      facts: first.rules
        .slice(0, 3)
        .map((r) => `${r.ruleName}：${evidence(r) || r.explanation || '明细见本股冻结证据'}`),
      nextChecks: checks.length
        ? checks.slice(0, 2)
        : ['进入该股的冻结研究，先核对触发事实与有效日期，再检查下一期条件是否变化。'],
    };
  if (first.order === 3)
    return {
      level: 'DATA',
      title: '先核对行情日期与规则覆盖',
      explanation: '没有触发记录不能据此认定没有风险。',
      facts: [
        `${first.stock.name}行情状态：${first.stock.status === 'TRADING' ? '当期有效' : '需核对当期记录'}`,
        `该股有${first.stock.insufficient}项规则证据不足`,
        ...analysis.missingData.slice(0, 1),
      ],
      nextChecks: ['补齐报告标明的缺项，重新生成报告时保留本修订原证据。'],
    };
  return {
    level: 'WATCH',
    title: '本修订暂无已触发规则，继续观察',
    explanation: '已覆盖范围内没有触发记录，仍需跟踪条件变化。',
    facts: [
      `已核对${analysis.stocks.length}只证券的冻结记录`,
      `报告基准收益${percent(analysis.benchmarkReturn, 2)}`,
    ],
    nextChecks: analysis.checks.slice(0, 2).map((c) => `${c.title}：${c.detail}`),
  };
}
/** 预测按相同财务年度的已存快照变化核对，不把跨年EPS差异称作预测修订。 */
export function forecastObservation(forecast: ForecastView): ObservationSummaryData {
  if (!forecast.latest)
    return {
      level: 'DATA',
      title: '先补齐可用的预测观测',
      explanation: forecast.explanation,
      facts: ['当前没有该时点可用的预测快照'],
      nextChecks: ['到任务中心核对采集情况；没有预测不等于盈利预期为0。'],
    };
  const down = forecast.changes.filter((c) => c.difference < 0);
  const up = forecast.changes.filter((c) => c.difference > 0);
  const orderedChanges = [...forecast.changes].sort(
    (a, b) => Number(b.difference < 0) - Number(a.difference < 0) || a.year - b.year,
  );
  const facts = orderedChanges
    .slice(0, 3)
    .map(
      (c) => `${c.year}年预测EPS：${number(c.previousEps, 3)} → ${number(c.currentEps, 3)}元/股`,
    );
  const base = {
    facts: facts.length
      ? [`最新快照可用时刻${timestamp(forecast.latest.availableAt)}`, ...facts.slice(0, 2)]
      : [
          `首次观测${timestamp(forecast.latest.availableAt)}`,
          `近六个月评级机构${forecast.latest.ratingAgencies}家，不等于逐年预测覆盖数`,
        ],
    nextChecks: [
      '核对下一次同年度预测是否继续修订，并与实际披露财报交叉检查。',
      '预测是机构估计，不能替代长期估值、经营证据或个人买卖条件。',
    ],
  };
  if (down.length)
    return {
      ...base,
      level: 'IMPORTANT',
      title: `${down.length}个年度的盈利预测下修需关注`,
      explanation: up.length
        ? '不同年度修订方向不一致，应分别核对下修年度。'
        : '先核对预测下调的年度、观测时间与公开说明，不能直接据此推断股价。',
    };
  if (up.length)
    return {
      ...base,
      level: 'WATCH',
      title: '盈利预测有所上修，先核对持续性',
      explanation: '上修不是公司已经兑现的利润，也不是买入确认。',
    };
  return {
    ...base,
    level: 'WATCH',
    title: forecast.changes.length
      ? '可比快照暂无数值修订'
      : '尚无同年度可比修订，不能判断预期趋势',
    explanation: forecast.explanation,
  };
}
function finite(value: unknown) {
  if (value == null || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}
/** 主题只解释所选日的固定披露样本，不把目录资历或不完整覆盖当作行业强弱。 */
export function themeObservation(input: {
  strength: ThemeStrength;
  date: string;
}): ObservationSummaryData {
  const { strength, date } = input;
  const latest = strength.items.at(-1);
  if (
    !latest ||
    latest.trade_date !== date ||
    latest.is_synthetic ||
    !strength.members.length ||
    !strength.cohort ||
    strength.cohort.available_at.slice(0, 10) > date
  )
    return {
      level: 'DATA',
      title: '先补齐本期可用的样本观察',
      explanation: '龙头目录说明行业位置；目录本身不能证明今天的主题强弱。',
      facts: [
        `所选日期${date}；最近样本观测${latest?.trade_date || '暂无'}`,
        `当前披露样本${strength.members.length}只，不等于全行业`,
      ],
      nextChecks: ['先核对成员有效期、同源行情与当期样本覆盖，再判断主题变化。'],
    };
  const breadth = finite(latest.breadth),
    rs = finite(latest.rs20),
    coverage = finite(latest.coverage);
  const facts = [
    `20日相对沪深300表现${percent(rs, 2)}`,
    `样本中站上MA20的比例${percent(breadth, 1)}`,
    `有效覆盖${percent(coverage, 1)}，分母仅为${strength.members.length}只披露样本`,
  ];
  if (coverage == null || coverage !== 1)
    return {
      level: 'DATA',
      title: '样本覆盖未齐，先核对缺项',
      explanation: '缺失成员不能作为下跌或弱势成员补入，也不能据此判断整个行业。',
      facts,
      nextChecks: ['补齐当期样本覆盖后再比较相对强弱；完整行业覆盖仍需另行核验。'],
    };
  return {
    level: rs != null && rs < 0 ? 'IMPORTANT' : 'WATCH',
    title:
      rs == null
        ? '相对强弱尚无充分依据'
        : rs < 0
          ? '样本近期跑输基准，优先观察是否继续走弱'
          : rs === 0
            ? '样本相对基准持平，继续观察'
            : '样本近期相对跑赢，关注能否持续',
    explanation: '相对跑赢时仍可能下跌；目录中的企业资历排名不等于当前投资机会排名。',
    facts,
    nextChecks: [
      '继续核对同一批样本的相对强度、均线广度与覆盖变化。',
      '进入成员个股核对量价与财报；样本结论不能直接推广为全行业判断。',
    ],
  };
}

/** 仅在当前页及所选日期范围内标出计划待办，不把旧复核视作本日判断。 */
export function planObservation(input: {
  rows: import('../api/plans').PlanSummary[];
  date: string;
}): ObservationSummaryData {
  const { rows, date } = input;
  const active = rows.filter((row) => !row.archived && row.asOf <= date);
  const known = active.filter((row) => row.latestReviewDate && row.latestReviewDate <= date);
  if (!active.length)
    return {
      level: 'WATCH',
      title: '本页没有需按所选日跟踪的有效计划',
      explanation: '已归档或原研究日晚于所选日的计划不作为本日风险判断。',
      facts: [`本页保存记录${rows.length}份`],
      nextChecks: ['切换跟踪中计划，并核对所选研究日期。'],
    };
  const insufficient = known.filter((row) => row.latestReviewStatus === 'DATA_INSUFFICIENT');
  const breached = known.filter((row) => row.latestReviewStatus === 'ORIGINAL_STOP_BREACHED');
  const changed = known.filter((row) =>
    ['CONDITIONS_CHANGED', 'METHOD_CHANGED', 'SOURCE_CHANGED'].includes(
      row.latestReviewStatus || '',
    ),
  );
  const due = active.filter((row) => row.latestReviewDate !== date);
  if (breached.length)
    return {
      level: 'FIRST',
      title: `本页${breached.length}份计划曾触及原风险参考，先复核`,
      explanation:
        '这里来自最近保存的复核，旧日触及记录不是本日新发生；后续收复也不能抹掉原风险事实。',
      facts: breached.slice(0, 3).map((row) => `${row.name} · 最近保存复核${row.latestReviewDate}`),
      nextChecks: [
        `按${date}显式复核，检查原风险边界与来源口径；不要下移原价位。`,
        '打开原始条件与复核记录，再到当前个股核对是否出现有效变化。',
      ],
    };
  if (changed.length)
    return {
      level: 'IMPORTANT',
      title: `本页${changed.length}份计划的条件、方法或来源需核对`,
      explanation: '条件有变化或比较口径改变时，不能直接延续原判断。',
      facts: changed.slice(0, 3).map((row) => `${row.name} · ${row.latestReviewDate}的已存复核`),
      nextChecks: ['逐份打开具体原因，对照原始方案核对变化，再评估是否仍适用。'],
    };
  if (insufficient.length)
    return {
      level: 'DATA',
      title: `本页${insufficient.length}份计划复核证据不足`,
      explanation: '虽然已有复核记录，仍不能确认原风险线是否失守或原条件是否成立。',
      facts: insufficient
        .slice(0, 3)
        .map((row) => `${row.name} · 最近保存复核${row.latestReviewDate}`),
      nextChecks: ['先补齐具体缺失的行情或价格因子，再显式追加复核；缺资料不表示风险解除。'],
    };
  return {
    level: due.length ? 'DATA' : 'WATCH',
    title: due.length
      ? `本页${due.length}份计划尚待所选日复核`
      : '本页计划已有所选日复核，继续观察后续变化',
    explanation: '刷新与切换日期只读取记录，不会自动追加复核或下单。',
    facts: [
      `所选日期${date}；本页跟踪中计划${active.length}份`,
      `${due.length}份计划最近保存复核不属于所选日，或尚未复核`,
    ],
    nextChecks: [`先对需要跟踪的计划按${date}显式复核，再检查变化原因与原风险参考。`],
  };
}

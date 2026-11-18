import type { Financial } from '../types/fundamental';

/** 财报对比只使用同一累计期间，不把半年、三季和全年相互当作增长。 */
export const financialPeriods: Record<string, { label: string; end: string }> = {
  Q1_CUMULATIVE: { label: '一季报（1—3月累计）', end: '03-31' },
  H1_CUMULATIVE: { label: '半年报（1—6月累计）', end: '06-30' },
  Q3_CUMULATIVE: { label: '三季报（1—9月累计）', end: '09-30' },
  ANNUAL: { label: '年报（全年累计）', end: '12-31' },
};
export const financialMetrics = [
  { key: 'revenue', label: '营业收入', term: 'revenue' },
  { key: 'net_profit', label: '归母净利润', term: 'revenue' },
  { key: 'operating_cashflow', label: '经营现金流', term: 'cashflow' },
  { key: 'gross_margin', label: '毛利率', term: 'gross-margin' },
] as const;
export type FinancialMetricKey = (typeof financialMetrics)[number]['key'];
export type FinancialTone = 'positive' | 'negative' | 'neutral' | 'unknown';
export interface FinancialMetricComparison {
  key: FinancialMetricKey;
  label: string;
  term: string;
  current: number | null;
  previous: number | null;
  difference: number | null;
  growth: number | null;
  tone: FinancialTone;
  change: string;
}
/** 严格保留缺失；空字符串和非法数字不转换成0。 */
export function financialNumber(value: unknown): number | null {
  if (value == null || (typeof value === 'string' && !value.trim())) return null;
  if (typeof value !== 'number' && typeof value !== 'string') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}
/** 数据已由后端按可用时点筛选，这里额外排除未来报告期、未来披露和不匹配累计类型。 */
export function usableFinancialReports(items: Financial[], date: string): Financial[] {
  const rows = items.filter(
    (row) =>
      /^\d{4}-\d{2}-\d{2}$/.test(row.period_end) &&
      row.period_end <= date &&
      financialPeriods[row.report_type]?.end === row.period_end.slice(5) &&
      row.published_at?.slice(0, 10) <= date,
  );
  // 正常接口每期仅有一个版本；异常重复不任意选择其中一份来推断增长。
  const counts = new Map<string, number>();
  for (const row of rows) counts.set(row.period_end, (counts.get(row.period_end) || 0) + 1);
  return rows
    .filter((row) => counts.get(row.period_end) === 1)
    .sort((a, b) => a.period_end.localeCompare(b.period_end));
}
function compareMetric({
  key,
  label,
  term,
  current,
  previous,
}: {
  key: FinancialMetricKey;
  label: string;
  term: string;
  current: Financial | undefined;
  previous: Financial | undefined;
}): FinancialMetricComparison {
  const value = financialNumber(current?.[key]),
    before = financialNumber(previous?.[key]);
  const base = {
    key,
    label,
    term,
    current: value,
    previous: before,
    difference: null,
    growth: null,
    tone: 'unknown' as FinancialTone,
    change: '缺少同口径上年同期数据，暂不判断变化',
  };
  if (value == null || before == null)
    return { ...base, change: value == null ? '本期数值缺失，暂不判断' : base.change };
  const delta = value - before;
  const same = Math.abs(delta) < 1e-8;
  const tone: FinancialTone = same ? 'neutral' : delta > 0 ? 'positive' : 'negative';
  const growth = key !== 'gross_margin' && before > 0 ? delta / before : null;
  let change = same ? '与上年同期持平' : delta > 0 ? '较上年同期增加' : '较上年同期减少';
  if (key === 'gross_margin') change = same ? '同比持平' : delta > 0 ? '同比提高' : '同比下降';
  if (key === 'net_profit') {
    if (before < 0 && value > 0) change = '同比扭亏为盈';
    else if (before > 0 && value < 0) change = '同比由盈转亏';
    else if (before < 0 && value <= 0)
      change = same
        ? '仍为亏损，较同期持平'
        : delta > 0
          ? '同比亏损收窄，仍未盈利'
          : '同比亏损扩大';
    else if (before === 0)
      change =
        value > 0 ? '上年同期为0，本期转为盈利' : value < 0 ? '上年同期为0，本期亏损' : '两期均为0';
  }
  if (key === 'operating_cashflow') {
    if (before < 0 && value > 0) change = '同比由净流出转为净流入';
    else if (before > 0 && value < 0) change = '同比由净流入转为净流出';
    else if (before < 0 && value <= 0)
      change = same ? '仍为净流出，较同期持平' : delta > 0 ? '同比净流出收窄' : '同比净流出扩大';
    else if (before === 0)
      change =
        value > 0 ? '上年同期为0，本期净流入' : value < 0 ? '上年同期为0，本期净流出' : '两期均为0';
  }
  return { ...base, difference: same ? 0 : delta, growth, tone, change };
}

/** 对四项已披露事实作方向性说明；不打综合投资分，不推断财报未披露的原因。 */
export function analyzeFinancialReports({
  items,
  date,
  reportType: requestedType = '',
}: {
  items: Financial[];
  date: string;
  reportType?: string;
}) {
  const usable = usableFinancialReports(items, date);
  const types = [...new Set(usable.map((row) => row.report_type))].sort((a, b) =>
    financialPeriods[a]!.end.localeCompare(financialPeriods[b]!.end),
  );
  const type = types.includes(requestedType) ? requestedType : usable.at(-1)?.report_type || '';
  const rows = usable.filter((row) => row.report_type === type);
  const latest = rows.at(-1);
  const previousDate = latest
    ? `${Number(latest.period_end.slice(0, 4)) - 1}-${latest.period_end.slice(5)}`
    : '';
  const previous = rows.find((row) => row.period_end === previousDate);
  const comparable = Boolean(
    latest?.source && previous?.source && latest.source === previous.source,
  );
  const metrics = financialMetrics.map((metric) =>
    compareMetric({ ...metric, current: latest, previous: comparable ? previous : undefined }),
  );
  const positives = metrics.filter((m) => m.tone === 'positive');
  const negatives = metrics.filter((m) => m.tone === 'negative');
  const unknown = metrics.filter((m) => m.tone === 'unknown');
  const currentNegative = metrics.some(
    (m) =>
      ['net_profit', 'operating_cashflow'].includes(m.key) && m.current != null && m.current < 0,
  );
  const title = !latest
    ? '暂无可用的累计财报'
    : !comparable
      ? '同期对照不足，暂不评价整体变化'
      : positives.length && (negatives.length || currentNegative)
        ? '经营表现分化：有改善，也有压力'
        : negatives.length || currentNegative
          ? '已覆盖的同口径指标偏弱'
          : positives.length
            ? '已覆盖的同口径指标有所改善'
            : '已覆盖的同口径指标基本持平';
  const followUps: string[] = [];
  const revenue = metrics[0]!,
    profit = metrics[1]!,
    cash = metrics[2]!,
    margin = metrics[3]!;
  if (revenue.tone === 'positive' && profit.tone === 'negative')
    followUps.push(
      '重点核对“增收未增利”：从公告查看成本费用、产品结构及非经常性损益的变化；现有字段不能确认具体原因。',
    );
  else if (profit.tone === 'negative' || (profit.current != null && profit.current < 0))
    followUps.push(
      '关注后续同口径利润能否改善，并核对扣非净利润与费用变化，区分经营变化和一次性因素。',
    );
  if (margin.tone === 'negative')
    followUps.push(
      '关注下一份同口径财报的毛利率能否改善，并核对产品价格、材料成本及业务组合的公开说明。',
    );
  if (cash.current != null && cash.current < 0)
    followUps.push('经营现金流仍为净流出，关注回款、应收账款和存货变化，以及后续是否持续改善。');
  else if (cash.current === 0)
    followUps.push('经营现金流目前为0，关注后续回款及能否形成持续净流入。');
  else if (cash.current != null)
    followUps.push(
      '核对经营现金流能否持续净流入，并与利润、应收账款和存货变化交叉检查；现金流与归母净利润不能直接等同。',
    );
  if (!comparable)
    followUps.unshift(
      previous
        ? '两期来源未确认一致：先补齐同一来源的同期版本，再判断同比变化。'
        : `补齐${previousDate || '上年同期'}同口径财报，再核对收入、利润、现金流和毛利率。`,
    );
  if (unknown.length)
    followUps.push(`补齐缺项：${unknown.map((m) => m.label).join('、')}的本期或同口径同期数据。`);
  if (comparable && !negatives.length && !currentNegative)
    followUps.push(
      '关注改善能否在后续披露中持续；仍需结合估值、负债与行业情况研究，不能仅凭四项改善推断可以买入。',
    );
  const lastYear = latest ? Number(latest.period_end.slice(0, 4)) : 0;
  const firstYear = rows.length
    ? Math.max(lastYear - 9, Number(rows[0].period_end.slice(0, 4)))
    : 0;
  const sourceRows = latest
    ? Array.from({ length: lastYear - firstYear + 1 }, (_, index) => {
        const year = firstYear + index;
        const row = rows.find((item) => Number(item.period_end.slice(0, 4)) === year) || null;
        return {
          year,
          row,
          comparableSource:
            row != null &&
            (Boolean(latest.source && row.source === latest.source) || row === latest),
        };
      })
    : [];
  return {
    type,
    types,
    latest,
    previous,
    previousDate,
    comparable,
    metrics,
    positives,
    negatives,
    unknown,
    title,
    followUps,
    history: sourceRows,
    excluded: items.length - usable.length,
    scope:
      '仅比较同一累计报告期的现存版本；这四项是经营观察，与风险规则是否触发分别判断，不是股票买卖评级。原因还需原始公告核实。',
  };
}

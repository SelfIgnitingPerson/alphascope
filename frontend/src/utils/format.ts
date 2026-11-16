import type { Numeric } from '../types/models';
export function number(value: Numeric | null | undefined, digits = 2): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const n = Number(value);
  return Number.isFinite(n)
    ? n.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
    : '—';
}
export function percent(value: Numeric | null | undefined, digits = 1): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  return Number.isFinite(Number(value)) ? `${number(Number(value) * 100, digits)}%` : '—';
}
/** 带时区的通道时间统一转上海；无时区的数据库时间按既有上海口径显示。 */
export function timestamp(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  if (/Z$|[+-]\d{2}:\d{2}$/.test(value)) {
    const instant = new Date(value);
    if (Number.isFinite(instant.getTime())) {
      return new Intl.DateTimeFormat('sv-SE', {
        timeZone: 'Asia/Shanghai',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hourCycle: 'h23',
      }).format(instant);
    }
  }
  return value.replace('T', ' ').replace(/\.\d+$/, '');
}
export function pretty(value: unknown): string {
  if (value === null || value === undefined) {
    return '暂无证据';
  }
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
}
export function sourceName(value: string | undefined): string {
  const names: Record<string, string> = {
    MULTI: '多通道',
    BAOSTOCK: 'BaoStock',
    SINA: '新浪财经',
    EASTMONEY: '东方财富',
    EASTMONEY_DIRECTORY: '东方财富 · 证券目录',
    EASTMONEY_FINANCIAL: '东方财富 · 财报',
    EASTMONEY_FORECAST: '东方财富 · 机构预测',
    EASTMONEY_PUBLIC: '东方财富公开预测',
    EXCHANGE_CALENDAR: '交易所公告日历',
    EASTMONEY_FREE: '东方财富公开财报',
    AKSHARE: 'AKShare（历史）',
    AKSHARE_SINA: 'AKShare · 新浪',
    AKSHARE_EASTMONEY: 'AKShare · 东方财富',
    TUSHARE: 'Tushare',
    DEMO: '模拟数据',
    NONE: '尚未采集',
  };
  return value ? names[value] || value : '暂无来源';
}
export const ruleNames: Record<string, string> = {
  TREND_BREAK_20: '20日趋势转弱',
  TREND_BREAK_60: '60日趋势转弱',
  VOLUME_SELL: '放量下跌',
  NEW_LOW_20: '20日收盘新低',
  RELATIVE_WEAK: '相对强度走弱',
  PROFIT_SLOWDOWN: '利润增速放缓',
  MARGIN_DECLINE: '毛利率下降',
  CASHFLOW_WARNING: '现金流恶化',
  POSITION_CONCENTRATION: '持仓集中度偏高',
  INDUSTRY_DIVERGENCE: '主题走势分化',
};
export const stageNames: Record<string, string> = {
  CALENDAR: '交易日历',
  MARKET_SYNC: '行情采集',
  DATA_QUALITY: '数据检查',
  RESEARCH_DATA: '财报与市场截面',
  THEME_MEMBERS: '披露主题样本',
  FORECASTS: '机构预测采集',
  MARKET_OBSERVATION: '沪深市场分项观察',
  INDICATORS: '指标计算',
  SCORE_RULES: '评分与规则',
  REPORT: '生成报告',
  NOTIFICATION: '本地通知',
};

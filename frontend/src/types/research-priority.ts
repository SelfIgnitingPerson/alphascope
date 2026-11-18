/** 事实驱动的阅读顺序；不是亏损概率或交易评分。 */
export interface ObservationSummaryData {
  level: 'FIRST' | 'IMPORTANT' | 'WATCH' | 'DATA';
  title: string;
  explanation: string;
  facts: string[];
  nextChecks: string[];
}
/** 首页与个股共用服务端观察事项，旧冻结内容允许没有此扩展。 */
export interface ResearchPriority extends ObservationSummaryData {
  code: string;
  horizon: string | null;
  actionCode: string | null;
}

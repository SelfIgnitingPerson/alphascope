import type { ReportType } from './reports';

/** 日期闭区间，ISO 格式 YYYY-MM-DD；不允许客户端指定数据源或真实/演示模式。 */
export interface DateRangeQuery {
  /** 起始日期，包含当日。 */
  start: string;
  /** 结束日期，包含当日，不能早于start。 */
  end: string;
}
/** 分页条件；省略时采用第1页、每页20条。 */
export interface PageQuery {
  /** 从1开始的页码，最大100000。 */
  page?: number;
  /** 每页1至100条。 */
  size?: number;
}
/** 报告归档条件，保留原查询名称及默认起始日期。 */
export interface ReportSearchQuery extends PageQuery {
  /** DAILY收盘日报或WEEKLY周报。 */
  type: ReportType;
  /** 截止业务日期，包含当日。 */
  end: string;
  /** 起始日期；前端缺省为1990-01-01，以便查询全部归档。 */
  start?: string;
  /** 默认true，仅取同业务日最新修订；false保留全部修订。 */
  latestOnly?: boolean;
}
/** 已保存信号的收益统计，证券代码在查询参数中。 */
export interface SignalStatisticsQuery extends DateRangeQuery {
  /** 收益评估截止日期，省略时等于end；不能使用未来行情。 */
  evaluationDate?: string;
  /** 较晚留出区间首日；省略按日期预先分割。 */
  splitDate?: string;
  /** 回顾统计或严格按实际保存/修订时刻检查。 */
  availabilityMode?: 'RETROSPECTIVE' | 'POINT_IN_TIME';
  /** 指定规则版本；未指定时不混合不同版本。 */
  ruleVersion?: string;
  /** 统一证券代码，如300476.SZ。 */
  symbol: string;
}
/** 持久任务提交请求；force显式要求新修订，提交返回后由后台执行。 */
export interface JobSubmitInput {
  /** DAILY日报或WEEKLY周报。 */
  type: ReportType;
  /** 不晚于上海当前日期的研究业务日期。 */
  date: string;
  /** 默认false；true请求新修订，仍复用同范围正在执行的任务。 */
  force?: boolean;
}
/** 人工持仓记录；数量单位股、成本单位人民币元，日期为当前资产业务日期。 */
export interface PositionInput {
  /** 非负持仓股数。 */
  quantity: number;
  /** 非负的人民币每股成本。 */
  costPrice: number;
  /** 人工记录对应的业务日期。 */
  asOfDate: string;
}

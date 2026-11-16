import { api, query } from './client';
import type { ReportSearchQuery } from '../types/requests';
import type { ReportSummary, ReportDetail, ReportComparison, PageResponse } from '../types/models';
/** 分页查询报告归档，默认只展示各业务日最新修订。 */
export const getReports = (request: ReportSearchQuery, signal?: AbortSignal) => {
  const { type, end, page = 1, size = 20, start = '1990-01-01', latestOnly = true } = request;
  return api<PageResponse<ReportSummary>>(
    '/reports?' + query({ type, start, end, page, size, latestOnly }),
    { signal },
  );
};
/** 读取一个不可变报告修订及其冻结分析。 */
export const getReport = (id: number, signal?: AbortSignal) =>
  api<ReportDetail>('/reports/' + id, { signal });
/** 读取同日期、类型和模式的报告修订列表。 */
export const getReportVersions = (id: number, signal?: AbortSignal) =>
  api<ReportSummary[]>('/reports/' + id + '/versions', { signal });
/** 比较可比修订的正文及输入是否改变，不重跑研究任务。 */
export const compareReports = (id: number, other: number, signal?: AbortSignal) =>
  api<ReportComparison>('/reports/' + id + '/compare/' + other, { signal });

import { api, write, query } from './client';
import type { PageResponse } from '../types/models';
import type { HoldingResearch } from '../types/holding-research';
export interface ResearchPlan {
  id: number;
  horizon: string;
  actionCode: string;
  archived: boolean;
  createdAt: string;
  snapshotHash: string;
  research: HoldingResearch;
}
export interface PlanReview {
  planId: number;
  date: string;
  status: string;
  explanation: string;
  adjustedOriginalStop: number | null;
  close: number | null;
  currentActionStatus: string | null;
  limitations: string[];
}
/** 只读个人计划，刷新不复核、不采集。 */
export type PlanSummary = Pick<
  ResearchPlan,
  'id' | 'horizon' | 'actionCode' | 'archived' | 'createdAt'
> & { symbol: string; name: string; asOf: string };
export interface PlanFilters {
  page: number;
  size: number;
  symbol?: string;
  horizon?: string;
  archived?: boolean;
}
export const getPlans = (filters: PlanFilters, signal?: AbortSignal) =>
  api<PageResponse<PlanSummary>>(`/plans?${query({ ...filters })}`, { signal });
export const getPlan = (id: number, signal?: AbortSignal) =>
  api<ResearchPlan>(`/plans/${id}`, { signal });
/** 服务端重新读取并冻结证据，客户端不上传价位。 */
export const savePlan = (input: {
  symbol: string;
  date: string;
  horizon: string;
  actionCode: string;
}) => api<ResearchPlan>('/plans', write('POST', input));
export const reviewPlan = (id: number, date: string) =>
  api<PlanReview>(`/plans/${id}/reviews`, write('POST', { date }));
export const getPlanReviews = (id: number, page = 1, signal?: AbortSignal) =>
  api<PageResponse<PlanReview>>(`/plans/${id}/reviews?${query({ page, size: 20 })}`, { signal });
export const archivePlan = (id: number) => api<null>(`/plans/${id}/archive`, write('POST'));

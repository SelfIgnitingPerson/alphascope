import { api, query, write } from './client';
import type { JobSubmitInput, PageQuery } from '../types/requests';
import type { Job, JobDetail, PageResponse } from '../types/models';
/** 分页读取持久任务记录，不因页面离开而取消后台任务。 */
export const getJobs = (
  { page = 1, size = 20, type }: PageQuery & { type?: string } = {},
  signal?: AbortSignal,
) => api<PageResponse<Job>>('/admin/jobs?' + query({ page, size, type }), { signal });
/** 读取单次任务的心跳、阶段和失败原因。 */
export const getJob = (id: number, signal?: AbortSignal) =>
  api<JobDetail>('/admin/jobs/' + id, { signal });
/** 只等待服务端接收异步任务；force显式创建报告新修订。 */
export const runJob = ({ type, date, force = false }: JobSubmitInput) =>
  api<JobDetail>(
    '/admin/jobs/' + (type === 'DAILY' ? 'daily' : 'weekly') + '?' + query({ date, force }),
    write('POST'),
  );
/** 沿用原任务编号重新执行，之前的状态及原因保留在执行历史。 */
export const retryJob = (id: number) =>
  api<JobDetail>('/admin/jobs/' + id + '/retry', write('POST'));

/** 持久化取消请求；运行任务会先释放资源再进入最终状态。 */
export const cancelJob = (id: number) =>
  api<JobDetail>('/admin/jobs/' + id + '/cancel', write('POST'));

/** 管理员显式提交可取消、可重试的沪深历史补齐；不生成报告。 */
export const fillMarketHistory = (date: string) =>
  api<JobDetail>('/admin/jobs/market-history?' + query({ date }), write('POST'));

/** 显式采集当前北交所目录；不采集历史行情、不生成报告。 */
export const fillMarketUniverse = (date: string) =>
  api<JobDetail>('/admin/jobs/market-universe?' + query({ date }), write('POST'));

/** 分页读取该任务重试前的不可变状态、原因和阶段。 */
export const getJobAttempts = (id: number, page = 1, signal?: AbortSignal) =>
  api<PageResponse<JobDetail>>(`/admin/jobs/${id}/attempts?${query({ page, size: 10 })}`, {
    signal,
  });

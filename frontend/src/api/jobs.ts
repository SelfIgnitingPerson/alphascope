import { api, query, write } from './client';
import type { JobSubmitInput, PageQuery } from '../types/requests';
import type { Job, JobDetail, PageResponse } from '../types/models';
/** 分页读取持久任务记录，不因页面离开而取消后台任务。 */
export const getJobs = ({ page = 1, size = 20 }: PageQuery = {}, signal?: AbortSignal) =>
  api<PageResponse<Job>>('/admin/jobs?' + query({ page, size }), { signal });
/** 读取单次任务的心跳、阶段和失败原因。 */
export const getJob = (id: number, signal?: AbortSignal) =>
  api<JobDetail>('/admin/jobs/' + id, { signal });
/** 只等待服务端接收异步任务；force显式创建报告新修订。 */
export const runJob = ({ type, date, force = false }: JobSubmitInput) =>
  api<JobDetail>(
    '/admin/jobs/' + (type === 'DAILY' ? 'daily' : 'weekly') + '?' + query({ date, force }),
    write('POST'),
  );
/** 为失败或中断任务创建带父任务引用的新尝试，保留原失败记录。 */
export const retryJob = (id: number) =>
  api<JobDetail>('/admin/jobs/' + id + '/retry', write('POST'));

/** 持久化取消请求；运行任务会先释放资源再进入最终状态。 */
export const cancelJob = (id: number) =>
  api<JobDetail>('/admin/jobs/' + id + '/cancel', write('POST'));

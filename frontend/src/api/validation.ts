import { api, write } from './client';
import type { PageResponse } from '../types/common';
import type { ResearchValidationRequest, ValidationTask } from '../types/validation';

/** 显式提交后台验证；页面刷新不提交计算。 */
export const submitValidation = (request: ResearchValidationRequest) =>
  api<ValidationTask>('/admin/backtest-validation-tasks', write('POST', request));
/** 只读取元数据，不下载原价和逐日信号。 */
export const getValidationTasks = (signal?: AbortSignal) =>
  api<PageResponse<ValidationTask>>('/backtest/validation-tasks?page=1&size=20', { signal });
/** 单独读取实际进度和已完成摘要。 */
export const getValidationTask = (id: number, signal?: AbortSignal) =>
  api<ValidationTask>(`/backtest/validation-tasks/${id}`, { signal });
/** 取消请求持久保存，终态以服务端实际结果为准。 */
export const cancelValidation = (id: number) =>
  api<ValidationTask>(`/admin/backtest-validation-tasks/${id}/cancel`, write('POST'));
/** 浏览器显式下载完整JSON，避免自动请求数兆字节证据。 */
export const validationEvidenceUrl = (id: number) =>
  `/api/v1/backtest/validation-tasks/${id}/evidence`;

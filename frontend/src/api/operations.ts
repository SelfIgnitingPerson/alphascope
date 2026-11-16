import { api, write } from './client';
import type { OperationsStatus } from '../types/operations';
/** 只读核对业务数据日期与备份证明，不触发采集或恢复。 */
export const getOperations = (signal?: AbortSignal) =>
  api<OperationsStatus>('/admin/operations', { signal });
/** 请求独立后台备份；响应只表示接收，实际验证结果须继续查询。 */
export const requestBackup = () => api<string>('/admin/operations/backup', write('POST'));

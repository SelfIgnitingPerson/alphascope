import { api, write, query } from './client';
import type { PageResponse } from '../types/common';
import type { SignalStatisticsQuery } from '../types/requests';
import type { ValidationExperiment, ExperimentReplay } from '../types/experiments';
/** 显式保存实验，GET历史统计不会产生写入。 */
export const saveExperiment = (request: SignalStatisticsQuery) =>
  api<ValidationExperiment>('/admin/backtest-experiments', write('POST', request));
/** 分页读取保存过的独立实验。 */
export const getExperiments = (signal?: AbortSignal) =>
  api<PageResponse<ValidationExperiment>>('/backtest/experiments?' + query({ page: 1, size: 20 }), {
    signal,
  });
/** 读取当时冻结的结果，不取当前值补造。 */
export const getExperiment = (id: number, signal?: AbortSignal) =>
  api<ValidationExperiment>('/backtest/experiments/' + id, { signal });
/** 验证指纹并只用冻结输入重新运行同一方法。 */
export const reproduceExperiment = (id: number) =>
  api<ExperimentReplay>('/admin/backtest-experiments/' + id + '/reproduce', write('POST'));

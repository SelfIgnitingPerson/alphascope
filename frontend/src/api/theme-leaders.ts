import { api, write } from './client';
import type { ThemeLeaders } from '../types/theme-leaders';

/** 只读当前全部核心企业组，供产业链总览展示。 */
export const getThemeLeadersCatalog = (signal?: AbortSignal) =>
  api<ThemeLeaders[]>('/themes/leaders', { signal });

/** 只读当前核心企业目录，服务端不联网、不改变历史样本或个人自选。 */
export const getThemeLeaders = (code: string, signal?: AbortSignal) =>
  api<ThemeLeaders>(`/themes/${encodeURIComponent(code)}/leaders`, { signal });

export interface ThemeLeaderRevision {
  revision: number;
  needsReview: boolean;
  directory: ThemeLeaders;
}
/** 当前管理员维护修订，无采集副作用。 */
export const getThemeLeaderRevision = (code: string, signal?: AbortSignal) =>
  api<ThemeLeaderRevision>(`/admin/themes/${encodeURIComponent(code)}/leaders`, { signal });
/** 追加修订，服务端核对管理员、并发修订和当前日期。 */
export const updateThemeLeaders = (
  code: string,
  input: { expectedRevision: number; directory: ThemeLeaders },
) =>
  api<ThemeLeaderRevision>(
    `/admin/themes/${encodeURIComponent(code)}/leaders`,
    write('POST', input),
  );

import { api } from './client';
import type { ThemeLeaders } from '../types/theme-leaders';

/** 只读当前全部核心企业组，供产业链总览展示。 */
export const getThemeLeadersCatalog = (signal?: AbortSignal) =>
  api<ThemeLeaders[]>('/themes/leaders', { signal });

/** 只读当前核心企业目录，服务端不联网、不改变历史样本或个人自选。 */
export const getThemeLeaders = (code: string, signal?: AbortSignal) =>
  api<ThemeLeaders>(`/themes/${encodeURIComponent(code)}/leaders`, { signal });

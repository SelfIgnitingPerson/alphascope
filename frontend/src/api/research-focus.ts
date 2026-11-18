import { api, query } from './client';
import type { ResearchPriority } from '../types/research-priority';
/** 当前页自选的研究变化，不等同全市场机会排序。 */
export interface ResearchFocus {
  asOf: string;
  total: number;
  page: number;
  size: number;
  items: {
    symbol: string;
    name: string;
    category: 'RISK' | 'MATCH' | 'DATA_GAP' | 'CHANGED' | 'WAIT';
    title: string;
    explanation: string;
    horizon: string | null;
    quoteDate: string | null;
    comparisonDate: string | null;
    newlyMatched: boolean;
    changeLabel?: string;
    priorities?: ResearchPriority[];
  }[];
}
/** 仅读取服务端当前用户的自选及两期现存研究，不采集或写入。 */
export const getResearchFocus = (
  input: { asOf: string; page: number; size: number },
  signal?: AbortSignal,
) => api<ResearchFocus>(`/research-focus?${query(input)}`, { signal });

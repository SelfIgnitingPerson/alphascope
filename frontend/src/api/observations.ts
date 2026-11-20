import { api, query, write } from './client';
export interface CaptureResult {
  date: string;
  saved: number;
  reused: number;
  issues: string[];
  captureKind?: string;
}
export const captureObservation = (date: string) =>
  api<CaptureResult>('/research-focus/capture', write('POST', { date }));
export interface ForwardStatistics {
  windowDays: number;
  status: string;
  calculated: number;
  attempted: number;
  meanNetReturn: number | null;
  positiveRatio: number | null;
  profitLossRatio: number | null;
  worstDrawdown: number | null;
  meanExcessReturn: number | null;
  benchmarkSamples: number;
  exclusions: Record<string, number>;
}
export interface ForwardSummary {
  date: string;
  observationCount?: number;
  candidateObservations: number;
  noEntryReasons: Record<string, number>;
  truncated: boolean;
  groups: {
    methodVersion: string;
    source: string;
    horizon: string;
    actionCode: string;
    protocolVersion: string;
    windows: ForwardStatistics[];
  }[];
  limitations: string[];
}
export const getForwardSummary = (
  input: { asOf: string; symbol?: string; planId?: number },
  signal?: AbortSignal,
) => api<ForwardSummary>(`/research-focus/forward?${query(input)}`, { signal });
export const evaluateForward = (date: string) =>
  api<CaptureResult>('/research-focus/forward/evaluate', write('POST', { date }));

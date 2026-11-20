import { api, query, write } from './client';
export interface ValuationShareBasis {
  currentShares: number;
  annualWeightedShares: number;
  parValue: number;
  sourceUrl: string;
}
export interface ComparablePeer {
  symbol: string;
  parValue: number;
  sourceUrl: string;
  rationale: string;
}
export interface ValuationThesis {
  shareBasis?: ValuationShareBasis | null;
  peers?: ComparablePeer[];
  financialSource: string;
  symbol: string;
  asOf: string;
  validUntil: string;
  epsLow: number;
  epsHigh: number;
  peLow: number;
  peHigh: number;
  safetyMargin: number;
  businessConfirmed: boolean;
  rationale: string;
  invalidation: string;
  sourceUrl: string;
  exitPolicy?: {
    minimumProfitYoy?: number | null;
    checkNegativeCashflow: boolean;
    consecutiveReports: number;
  } | null;
}
export interface CorporateAction {
  symbol: string;
  exDate: string;
  paymentDate: string;
  shareListingDate: string;
  priceSource: string;
  factorBefore: number;
  factorAfter: number;
  shareMultiplier: number;
  cashPerShare: number;
  exReferencePrice: number;
  sourceUrl: string;
}
export const getValuationThesis = (symbol: string, asOf: string, signal?: AbortSignal) =>
  api<ValuationThesis | null>(
    `/stocks/${encodeURIComponent(symbol)}/valuation-thesis?${query({ asOf })}`,
    { signal },
  );
export const getValuationThesisAtClose = (symbol: string, asOf: string, signal?: AbortSignal) =>
  api<ValuationThesis | null>(
    `/stocks/${encodeURIComponent(symbol)}/valuation-thesis/at-close?${query({ asOf })}`,
    { signal },
  );
export const saveValuationThesis = (input: ValuationThesis) =>
  api<null>('/valuation-theses', write('POST', input));
export const getCorporateActions = (symbol: string, asOf: string, signal?: AbortSignal) =>
  api<CorporateAction[]>(
    `/stocks/${encodeURIComponent(symbol)}/corporate-actions?${query({ asOf })}`,
    { signal },
  );
export const saveCorporateAction = (input: CorporateAction) =>
  api<null>('/admin/corporate-actions', write('POST', input));

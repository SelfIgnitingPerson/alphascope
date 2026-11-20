import { api, query } from './client';
/** 披露事实与私人估值假设的独立核对，未知原因不自动补全。 */
export interface BusinessEvidence {
  date: string;
  period: string | null;
  source: string | null;
  sourceUrl: string | null;
  cashConversion: number | null;
  marginChange: number | null;
  disclosedAnnualEps: number | null;
  findings: string[];
  unknowns: string[];
  valuationStatus: string;
  operatingQuality?: {
    cashBasis: string;
    metrics: {
      label: string;
      value: number | null;
      unit: string;
      sourceField: string;
      sourceUrl: string | null;
      meaning: string;
    }[];
    missing: string[];
  };
  references: {
    purpose: string;
    period: string;
    version: number | null;
    publishedAt: string | null;
    availableAt: string | null;
    sourceUrl: string | null;
    contentHash: string | null;
  }[];
}
export const getBusinessEvidence = (symbol: string, asOf: string, signal?: AbortSignal) =>
  api<BusinessEvidence>(
    `/instruments/${encodeURIComponent(symbol)}/business-evidence?${query({ asOf })}`,
    { signal },
  );

export interface ValuationAudit {
  version: string;
  date: string;
  status: string;
  observedTtmEps: number | null;
  peerPeLowerQuartile: number | null;
  conservativeCap: number | null;
  checks: { label: string; observed: string; passed: boolean | null }[];
  peers: {
    symbol: string;
    pe: number | null;
    status: string;
    reason: string;
    financialUrl: string | null;
    comparabilityUrl: string | null;
  }[];
  limitations: string[];
}
export const getValuationAudit = (symbol: string, asOf: string, signal?: AbortSignal) =>
  api<ValuationAudit>(
    `/instruments/${encodeURIComponent(symbol)}/valuation-audit?${query({ asOf })}`,
    { signal },
  );

import type { BacktestResult } from './signals';
/** 不可覆盖实验的元数据，列表不加载结果。 */
export interface ValidationExperiment {
  id: number;
  symbol: string;
  dataScope: string;
  availabilityMode: string;
  methodVersion: string;
  inputHash: string;
  resultHash: string;
  createdAt: string;
  result: BacktestResult | null;
}
/** 完全基于冻结输入的复算核验。 */
export interface ExperimentReplay {
  experimentId: number;
  inputVerified: boolean;
  resultMatches: boolean;
  expectedHash: string;
  actualHash: string;
  result: BacktestResult;
}

/** 五维规则评分；代理范围只展示分项，不计入完整全A总分。 */
export interface ResearchScore {
  modelVersion: string;
  scope: string;
  businessDate: string;
  score: number | null;
  state: string;
  validationStatus: string;
  dimensions: Record<
    string,
    {
      score: number | null;
      status: string;
      sourceScope: string;
      formula: string;
      evidence: Record<string, unknown>;
    }
  >;
  parameters: Record<string, number>;
  limitations: string[];
}

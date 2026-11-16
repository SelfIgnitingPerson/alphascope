/** 当前产业核心企业目录；不能用于倒推历史名单或计算原披露样本的收益。 */
export interface ThemeLeaders {
  themeCode: string;
  title: string;
  marketAlias: string;
  reviewedAt: string;
  selectionBasis: string;
  limitation: string;
  members: {
    /** 研究展示优先序，不代表统一行业排名或买入优先级。 */
    displayOrder: number;
    symbol: string;
    name: string;
    tier: string;
    segment: string;
    standing: string;
    rationale: string;
    /** 仅展示明确标注年份和比较范围的名次；没有则为空。 */
    formalRanking: string | null;
    sources: { title: string; url: string }[];
  }[];
}

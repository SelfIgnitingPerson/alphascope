/** 北交所当日目录与收盘行情分开核验，数量只代表已归档的供应商目录。 */
export interface MarketUniverse {
  businessDate: string;
  status: 'AVAILABLE' | 'NOT_ARCHIVED' | 'NOT_CONFIGURED';
  source: string;
  memberCount: number | null;
  snapshotId: number | null;
  contentHash: string | null;
  observedAt: string | null;
  sourceUrl: string;
  limitations: string[];
  members?: { symbol: string; name: string }[] | null;
}

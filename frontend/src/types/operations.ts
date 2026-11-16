/** 本机运维状态；技术健康、行情时效和备份证明分开。 */
export interface OperationsStatus {
  checkedAt: string;
  status: string;
  expectedTradeDate: string | null;
  calendarStatus: string;
  scheduledBackupEnabled: boolean;
  backupCron: string;
  backup: {
    status: string;
    fileName: string | null;
    verifiedAt: string | null;
    nextDue: string | null;
    sha256: string | null;
  };
  latestAttempt: {
    status: string;
    observedAt: string;
    fileName: string | null;
    reason: string;
  } | null;
  secondary: {
    status: string;
    observedAt: string | null;
    differentFileSystem: boolean;
    reason: string;
  };
  instruments: {
    symbol: string;
    status: string;
    tradeDate: string | null;
    fetchedAt: string | null;
    source: string | null;
  }[];
  issues: string[];
}

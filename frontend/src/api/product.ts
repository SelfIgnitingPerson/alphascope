import { api, write, query } from './client';
export interface SourceLicense {
  source: string;
  status: string;
  permissions: string[];
  validUntil: string | null;
  evidenceUrl: string | null;
  reference: string | null;
}
export interface WorkspaceStatus {
  checkedAt: string;
  status: string;
  expectedTradeDate: string | null;
  calendarStatus: string;
  instruments: { symbol: string; status: string; tradeDate: string | null }[];
  capacity: {
    activeJobs: number;
    activeValidations: number;
    evidenceBytes: number;
    oldestPendingAt: string | null;
  };
  issues: string[];
}
export interface ProductStatus {
  workspace: WorkspaceStatus;
  licenses: SourceLicense[];
  isolation: string;
  baselineSha256: string;
  backupEnabled: boolean;
  /** 服务端实际许可检查开关；不能仅凭本机/公网模式猜测。 */
  sourceLicenseEnforced: boolean;
  runtimeIssues: string[];
  blockers: string[];
}
export interface LicenseInput {
  source: string;
  permissions: string[];
  validFrom: string;
  validUntil: string;
  evidenceUrl: string;
  reference: string;
}
export interface ManagedUser {
  id: number;
  username: string;
  role: string;
  status: string;
  createdAt: string;
}
/** 当前账号的数据时效和资源状态，无上游采集副作用。 */
export const getWorkspaceStatus = (signal?: AbortSignal) =>
  api<WorkspaceStatus>('/workspace/status', { signal });
/** 仅管理员可见的来源、发布及运行资料。 */
export const getProductStatus = (signal?: AbortSignal) =>
  api<ProductStatus>('/admin/product/status', { signal });
export const declareLicense = (input: LicenseInput) =>
  api<null>('/admin/product/licenses', write('POST', input));
export const getManagedUsers = (page = 1) =>
  api<{ items: ManagedUser[]; total: number }>(`/admin/users?${query({ page, size: 20 })}`);
export const updateManagedUser = (id: number, status: string, password?: string) =>
  api<null>(`/admin/users/${id}`, write('PUT', { status, password }));

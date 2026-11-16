/** 公共分页、数值及页面上下文类型；保持现有 REST 字段名称。 */

export type Numeric = number | string;

export interface PageProps {
  /** 当前页面所选业务日期，接口使用YYYY-MM-DD格式。 */
  date: string;
  /** 刷新序号；变化时触发当前页面重新读取，不能当作请求时间。 */
  refreshToken: number;
}

export interface PageResponse<T> {
  /** 当前页或当前响应包含的业务记录列表。 */
  items: T[];
  /** 符合查询条件的全部记录数，不是当前页长度。 */
  total: number;
  /** 当前页码，从1开始。 */
  page: number;
  /** 每页返回条数；接口对最大值另有校验。 */
  size: number;
}

/** 服务端受控诊断；未知原因不根据异常类名猜测，原始响应及密钥不进入此对象。 */
export interface FailureDiagnosis {
  code: string;
  title: string;
  reason: string;
  impact: string;
  action: string;
  /** 供应商已确认的数字码与释义；原文缺失不能伪装为原始响应。 */
  providerError?: ProviderError | null;
}

export interface ProviderError {
  provider: string;
  code: string;
  message: string | null;
  meaning: string;
  messageState: 'UPSTREAM_SAFE_TEXT' | 'NOT_RECORDED' | 'WITHHELD';
}

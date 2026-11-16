/** 所有HTTP请求统一超时、取消和错误编号；提交任务只等待队列接收。 */
export class ApiError extends Error {
  constructor(
    message: string,
    public readonly code: string,
    public readonly requestId: string,
  ) {
    super(message + (requestId ? '（错误编号：' + requestId + '）' : ''));
  }
}
/** 全部业务接口统一使用的响应外壳；页面只接收解包后的 data。 */
export interface Result<T> {
  code: string;
  message: string;
  data: T | null;
  requestId: string;
}
export type ApiOptions = RequestInit & { timeoutMs?: number };
/** 发送统一REST请求；合并调用方取消信号和超时，失败时保留服务端requestId。 */
export async function api<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const { timeoutMs = 15000, signal: external, ...request } = options;
  const controller = new AbortController();
  let timedOut = false;
  const abort = () => controller.abort();
  if (external?.aborted) {
    abort();
  } else {
    external?.addEventListener('abort', abort, { once: true });
  }
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  try {
    // RequestInit同时接受对象、Headers和键值对；对象展开会丢失后两种形式。
    const headers = new Headers(request.headers);
    if (!headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json');
    }
    if (request.method && !['GET', 'HEAD', 'OPTIONS'].includes(request.method.toUpperCase())) {
      const csrf = document.cookie
        .split('; ')
        .find((value) => value.startsWith('ALPHASCOPE_CSRF='))
        ?.split('=')[1];
      if (csrf) headers.set('X-CSRF-Token', decodeURIComponent(csrf));
    }
    let response: Response;
    try {
      response = await fetch('/api/v1' + path, {
        ...request,
        signal: controller.signal,
        credentials: 'same-origin',
        headers,
      });
    } catch (error) {
      if (error instanceof TypeError && !external?.aborted) {
        throw new ApiError(
          '浏览器未能取得后端响应，具体原因尚未确认。请检查本机服务、网络和代理；提交操作先查询是否已受理，避免重复提交。',
          'NETWORK_ERROR',
          '',
        );
      }
      throw error;
    }
    const requestId = response.headers.get('X-Request-Id') || '';
    let data: unknown;
    try {
      data = await response.json();
    } catch {
      throw new ApiError(
        `服务未返回有效响应（HTTP ${response.status}）。本次操作结果未确认；请检查后端或代理，提交操作先查询任务结果，避免重复提交。`,
        'INVALID_RESPONSE',
        requestId,
      );
    }
    if (
      typeof data !== 'object' ||
      data === null ||
      !('code' in data) ||
      !('message' in data) ||
      !('data' in data) ||
      !('requestId' in data) ||
      typeof data.code !== 'string' ||
      typeof data.message !== 'string' ||
      typeof data.requestId !== 'string'
    ) {
      throw new ApiError(
        '服务返回的响应格式无效；可能存在前后端版本或代理不一致。请刷新页面，持续发生时保留错误编号联系维护人员，不要反复提交。',
        'INVALID_RESPONSE',
        requestId,
      );
    }
    const result = data as Result<T>;
    if (response.status === 401 && !path.startsWith('/auth/'))
      window.dispatchEvent(new Event('alphascope-session-expired'));
    if (!response.ok || result.code !== 'SUCCESS') {
      throw new ApiError(
        result.message || '请求失败',
        result.code || String(response.status),
        result.requestId || requestId,
      );
    }
    return result.data as T;
  } catch (error) {
    if (timedOut) {
      throw new ApiError(
        request.method && !['GET', 'HEAD'].includes(request.method.toUpperCase())
          ? '等待响应超时，不能据此判断提交失败；后台任务可能继续执行。请先查询任务状态，避免重复提交。'
          : '读取服务响应超时，本次读取尚未完成；请稍后重新读取，持续发生时检查后端负载和连接。',
        'TIMEOUT',
        '',
      );
    }
    throw error;
  } finally {
    clearTimeout(timer);
    external?.removeEventListener('abort', abort);
  }
}
/** 把可选筛选值编码为查询参数；只忽略undefined，保留0、false及空字符串。 */
export function query(params: Record<string, string | number | boolean | undefined>): string {
  const result = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) {
      result.set(key, String(value));
    }
  }
  return result.toString();
}
/** 构造JSON写请求，不在这里发送；表单层负责防止重复提交。 */
export function write(method: 'POST' | 'PUT' | 'DELETE', body?: unknown): RequestInit {
  return { method, body: body === undefined ? undefined : JSON.stringify(body) };
}

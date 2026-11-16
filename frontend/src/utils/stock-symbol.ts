/** 规范股票查询代码；无法可靠识别交易所时返回空值，由表单明确提示。 */
export function normalizeStockSymbol(input: string): string | null {
  const value = input.trim().toUpperCase();
  if (/^\d{6}\.(SH|SZ|BJ)$/.test(value)) return value;
  if (!/^\d{6}$/.test(value)) return null;
  if (/^[03]/.test(value)) return `${value}.SZ`;
  if (/^6/.test(value)) return `${value}.SH`;
  if (/^[48]|^92/.test(value)) return `${value}.BJ`;
  return null;
}

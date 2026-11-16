/** 所有业务日期按上海时区生成，避免UTC跨日。 */
export function today(): string {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date());
}
export function daysBefore(value: string, days: number): string {
  const date = new Date(`${value}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() - days);
  return date.toISOString().slice(0, 10);
}
export const yearBefore = (value: string) => daysBefore(value, 365);

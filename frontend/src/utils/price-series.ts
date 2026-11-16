import type { DailyBar, Indicator } from '../types/models';
const positive = (value: unknown): value is string | number =>
  (typeof value === 'number' || (typeof value === 'string' && value.trim() !== '')) &&
  Number.isFinite(Number(value)) &&
  Number(value) > 0;

/** 只画同源、同演示模式且有效的复权价格；缺项留空，不能把null转成0。 */
export function chartPrices(bars: DailyBar[], indicators: Indicator[]) {
  const anchor = bars.at(-1);
  const anchorValid = Boolean(anchor && positive(anchor.factor));
  const byDate = new Map(indicators.map((row) => [row.trade_date, row]));
  let omitted = 0;
  const prices = bars.map((bar) => {
    const values = [bar.open, bar.close, bar.low, bar.high];
    if (
      !anchorValid ||
      !positive(bar.factor) ||
      !bar.source ||
      bar.source !== anchor?.source ||
      bar.is_synthetic !== anchor?.is_synthetic ||
      !values.every(positive) ||
      Number(bar.low) > Math.min(Number(bar.open), Number(bar.close)) ||
      Number(bar.high) < Math.max(Number(bar.open), Number(bar.close))
    ) {
      omitted++;
      return null;
    }
    const factor = Number(bar.factor) / Number(anchor!.factor);
    const candle = values.map((value) => Number(value) * factor);
    if (!candle.every(positive)) {
      omitted++;
      return null;
    }
    return { candle, factor };
  });
  return {
    candles: prices.map((price) => price?.candle ?? ['-', '-', '-', '-']),
    ma20: line('ma20'),
    ma60: line('ma60'),
    warning: !anchorValid
      ? '末日复权因子缺失或无效，不能确定图表口径，暂不绘图。'
      : omitted
        ? `${omitted}条行情的价格、因子或来源口径无法核验，图中保留缺口；缺口不表示价格为0。`
        : '',
  };
  function line(key: 'ma20' | 'ma60') {
    return bars.map((bar, index) => {
      const value = byDate.get(bar.trade_date)?.[key];
      const price = prices[index];
      if (!price || !positive(value)) return null;
      const adjusted = Number(value) * price.factor;
      return positive(adjusted) ? adjusted : null;
    });
  }
}

/** 仅比较同源、同模式、价格与因子有效的两条收盘；不承诺两条记录为相邻交易日。 */
export function comparableReturn(latest?: DailyBar, previous?: DailyBar): number | null {
  if (
    !latest ||
    !previous ||
    !latest.source ||
    latest.source !== previous.source ||
    latest.is_synthetic !== previous.is_synthetic ||
    latest.trade_date <= previous.trade_date ||
    ![latest.close, latest.factor, previous.close, previous.factor].every(positive)
  )
    return null;
  const current = Number(latest.close) * Number(latest.factor);
  const before = Number(previous.close) * Number(previous.factor);
  const result = current / before - 1;
  return positive(current) && positive(before) && Number.isFinite(result) ? result : null;
}

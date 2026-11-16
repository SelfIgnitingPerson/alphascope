/** 将早期任务的机器格式转成可读摘要；不能从“少一条”推断具体缺哪一天。 */
export function readableJobError(message?: string | null) {
  const raw = message || '';
  const match = raw.match(
    /^行情采集失败：([0-9]{6}\.(?:SH|SZ|BJ))（(\d{4}-\d{2}-\d{2})至(\d{4}-\d{2}-\d{2})，已保存(\d+)条\/应有(\d+)个交易日；无完整可用版本）。/,
  );
  if (match) {
    const saved = Number(match[4]),
      required = Number(match[5]);
    const reason =
      saved < required
        ? `缺${required - saved}天收盘数据，旧记录没有保存具体缺失日期。`
        : `本地已有这个时间窗口的行情，但本次刷新或完整性核验没有通过。`;
    return {
      summary: `股票${match[1]}的行情没有补齐，本次报告未生成。${reason}补采原因和处理建议见下方，已有历史数据保留。`,
      technical: raw,
    };
  }
  if (raw.startsWith('所有真实行情通道不可用') || raw.startsWith('真实数据能力不可用'))
    return {
      summary: '本次所需行情未能采集完成。请查看下方各来源的原因和处理建议；已有历史数据保留。',
      technical: raw,
    };
  if (raw.startsWith('阶段失败，错误类型：'))
    return {
      summary:
        '本阶段未完成，旧记录只保存了异常类型。请参考任务说明和定位编号，不据此猜测为网络或权限问题。',
      technical: raw,
    };
  return { summary: raw, technical: '' };
}

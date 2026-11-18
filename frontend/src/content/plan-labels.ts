/** 当前研究方案和复核状态的统一中文名称，不把状态直接显示成内部代码。 */
export const planActionLabels: Record<string, string> = {
  OPEN_PULLBACK: '回踩企稳建仓',
  OPEN_BREAKOUT: '放量突破建仓',
  ADD_RETEST: '突破确认后回踩加仓',
  REDUCE: '减仓与风险退出',
  MEDIUM_OPEN: '中期回踩建仓',
  MEDIUM_ADD: '中期确认后加仓',
  MEDIUM_REDUCE: '中期遇压减仓',
  MEDIUM_EXIT: '中期退出复核',
  LONG_OPEN: '长期建仓',
  LONG_ADD: '长期加仓',
  LONG_REDUCE: '长期风险与减仓复核',
  LONG_EXIT: '长期退出复核',
};
export const planReviewLabels: Record<string, string> = {
  UNCHANGED: '方案暂无变化',
  CONDITIONS_CHANGED: '行情条件已变化',
  ORIGINAL_STOP_BREACHED: '原风险线已触及',
  METHOD_CHANGED: '方法已变更，需重评',
  SOURCE_CHANGED: '来源已变化，需核对',
  DATA_INSUFFICIENT: '复核资料不足',
  REVIEW_REQUIRED: '需人工复核',
};

/** 复核状态的就地解释，原始复核文本另行保留供追溯。 */
export const planReviewExplanations: Record<string, string> = {
  UNCHANGED:
    '与保存时相比，方案状态和每项条件的满足情况没有变化。原来未满足的买入条件，不会因此变成满足。',
  CONDITIONS_CHANGED:
    '方案状态或至少一项条件的满足情况已改变，请进入当前个股依据逐项核对。发生变化不等于可以直接买入。',
  ORIGINAL_STOP_BREACHED:
    '已有有效行情触及或跌破原方案风险参考。应优先重新评估原方案，即使之后收盘收复，也保留这次触及记录。',
  METHOD_CHANGED:
    '本次采用的分析规则与保存时不同，不能直接沿用新规则确认旧方案；原计划和依据仍然保留。',
  SOURCE_CHANGED:
    '本次行情来源与保存时不同，需要先核对价格口径，暂不能直接判断原风险线是否被触及。',
  DATA_INSUFFICIENT:
    '复核需要的行情或价格因子不完整，暂不能判断原方案是否仍然成立。缺资料不表示风险已经解除。',
  REVIEW_REQUIRED: '现有资料不足以自动完成核对，需要结合原方案与当前证据重新评估。',
};

/** 已知版本显示中文名称；未知旧规则保留内部编号，不猜测其含义。 */
export function researchMethodLabel(version: string) {
  const match = /^HOLDING_RESEARCH_V(\d+)$/.exec(version);
  return match ? `持有研究规则 · 第${match[1]}版` : '历史分析规则（详见内部编号）';
}

import { getStatusHelp, type StatusExplanation } from './status-help';
import type { HoldingResearch, HoldingHorizon, HoldingAction } from '../types/holding-research';
import type { ProviderChannel } from '../types/market';
import type { Job, JobStage } from '../types/jobs';
import { sourceName, stageNames, timestamp } from '../utils/format';
import { capabilityNames } from './channel-guides';

/** 直接使用本条计划的日期与缺项，不将数据不足推断成供应商封禁。 */
export function holdingStatusExplanation(input: {
  research: HoldingResearch;
  horizon: HoldingHorizon;
  action?: HoldingAction;
}): StatusExplanation {
  const { research, horizon, action } = input;
  const item = action || horizon;
  const suspended = item.status === 'UNAVAILABLE' && research.priceVolume?.status === 'SUSPENDED';
  const demo = item.status === 'UNAVAILABLE' && research.priceVolume?.status === 'DEMO';
  const dateGap =
    item.status === 'UNAVAILABLE' && research.quoteDate && research.quoteDate < research.asOf;
  const unknown = action?.conditions.filter((condition) => condition.met == null) ?? [];
  const unmet = action?.conditions.filter((condition) => condition.met === false) ?? [];
  const missing = [
    ...horizon.missing,
    ...unknown.map(
      (condition) => `${condition.label}：${condition.observed}；要求${condition.required}`,
    ),
  ];
  return {
    subject: `${research.name} · ${horizon.title}${action ? ' · ' + action.title : ''}`,
    reason: demo
      ? '当前为演示资料，该周期不形成真实行情的可执行价位。'
      : suspended
        ? `所选日期${research.asOf}已确认停牌，该周期暂停当日买卖计划。`
        : dateGap
          ? `研究日${research.asOf}缺少对应有效行情，最近已保存至${research.quoteDate}，该周期暂停判断。`
          : item.conclusion,
    facts: [
      `研究日期：${research.asOf}`,
      `最近已保存行情日：${research.quoteDate || '尚无记录'}`,
      ...(research.source ? [`本期采用来源：${sourceName(research.source)}`] : []),
      ...unmet.map(
        (condition) =>
          `未满足：${condition.label}；实测${condition.observed}；要求${condition.required}`,
      ),
    ],
    missing: [...new Set(missing)],
    action: demo
      ? '先在行情通道确认并配置真实模式，再同步明确业务日期；演示资料不能转换为真实价位。'
      : suspended
        ? '先核对已保存的停牌证据与复牌情况；该日不应补造日K或用停牌前价格当作当日成交价。'
        : dateGap
          ? `先在页头核对研究日期：可选择已有数据的${research.quoteDate}回顾研究；需要${research.asOf}结果时，待该日收盘数据齐备后，再到任务中心核查采集及缺日原因。不会自动替换研究日期。`
          : item.status === 'UNAVAILABLE'
            ? '按列出的缺项补齐行情、日历或资料；任务中心可查看采集阶段和来源诊断。'
            : '对照方案的实测值、判断条件、失效条件及个人适用前提复核；估值与经营逻辑等人工缺项需单独核实。',
    ...(demo
      ? { next: { to: '/channels', label: '查看当前行情模式与通道配置' } }
      : item.status === 'UNAVAILABLE'
        ? { next: { to: '/jobs', label: '查看任务阶段与具体采集错误' } }
        : {}),
  };
}

/** 按能力呈现已记录的错误、时间与处理建议，未请求不伪装为失败。 */
export function channelStatusExplanation(input: {
  channel: string;
  capability: NonNullable<ProviderChannel['capabilities']>[number];
}): StatusExplanation {
  const { channel, capability } = input;
  return {
    subject: `${sourceName(channel)} · ${capabilityNames[capability.capability] || capability.capability}`,
    diagnostic: capability.diagnostic,
    facts: [
      `最近成功：${timestamp(capability.lastSuccess)}`,
      ...(capability.lastFailure ? [`最近实际失败：${timestamp(capability.lastFailure)}`] : []),
      ...(capability.retryAt ? [`冷却截止：${timestamp(capability.retryAt)}`] : []),
    ],
    ...(capability.diagnostic
      ? { next: { to: '/jobs', label: '查看相关采集任务的阶段明细' } }
      : {}),
  };
}

/** 数据质量取对应任务或阶段的已存证据，不用0/0表示核验通过。 */
export function qualityStatusExplanation(input: { job: Job; stage?: JobStage }): StatusExplanation {
  const { job, stage } = input;
  const quality = stage?.details?.quality;
  return {
    subject: `任务#${job.id}${stage ? ' · ' + (stageNames[stage.stage_name] || stage.stage_name) : ''} · 数据质量`,
    diagnostic: stage
      ? stage.details?.diagnostic ||
        (['FAILED', 'TIMED_OUT', 'INTERRUPTED'].includes(stage.status) ? job.diagnostic : null)
      : job.diagnostic,
    reason: quality?.note,
    facts: [
      `业务日期：${job.business_date}`,
      `${stage ? '阶段' : '任务'}执行状态：${getStatusHelp(stage?.status || job.status).label}`,
      ...(quality
        ? [
            quality.expected > 0
              ? `已核验可用${quality.available} / 应有${quality.expected}项`
              : quality.status === 'NOT_APPLICABLE'
                ? '本阶段不适用，不要求提供该项资料'
                : '尚未形成有效核验分母，不能判为核验通过',
          ]
        : []),
    ],
    missing: quality?.issues?.map((item) => `${item.scope}：${item.reason}`),
    action:
      '展开当前任务的执行阶段，先核对具体缺项、供应商错误和数据质量，再决定是否需要补采或重试。',
  };
}

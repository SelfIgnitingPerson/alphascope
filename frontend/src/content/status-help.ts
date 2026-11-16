import type { FailureDiagnosis } from '../types/common';

/** 状态的唯一展示与释义入口；执行结果、数据覆盖和规则触发不能混为同一判断。 */
export interface StatusHelp {
  label: string;
  tone: 'success' | 'warning' | 'danger' | 'info' | 'muted';
  summary: string;
  detail?: string;
}

/** 本条记录的真实说明；不以静态状态词猜测供应商原因。 */
export interface StatusExplanation {
  subject: string;
  reason?: string;
  facts?: readonly string[];
  missing?: readonly string[];
  diagnostic?: FailureDiagnosis | null;
  action?: string;
  next?: { to: string; label: string };
}
export type StatusContext = 'general' | 'channel' | 'holding' | 'quality';

export const statusHelp: Record<string, StatusHelp> = {
  TRIGGERED: {
    label: '已触发',
    tone: 'danger',
    summary: '本期证据满足这条风险规则的条件。',
    detail: '先查看该条规则的数值和原因；这不是未来下跌概率，也不自动产生卖出委托。',
  },
  NOT_TRIGGERED: {
    label: '未触发',
    tone: 'success',
    summary: '必要数据足够，但本期没有满足这条规则的触发条件。',
    detail: '只说明这一条规则未触发，不代表整只股票安全、适合买入或必须持有。',
  },
  INSUFFICIENT_DATA: {
    label: '数据不足',
    tone: 'muted',
    summary: '缺少关键输入或覆盖条件不足，暂时无法判断。',
    detail: '例如缺同期财报、完整价格窗口或足够主题成员。不能理解为未触发；展开记录查看具体缺项。',
  },
  NOT_APPLICABLE: {
    label: '不适用',
    tone: 'muted',
    summary: '当前模式不进行这项评估，不是数据缺失。',
    detail: '例如自选研究模式不评估个人持仓集中度，因此集中度规则是不适用，而不是安全或已通过。',
  },
  HIGH: {
    label: '高',
    tone: 'danger',
    summary: '严重程度较高，触发后通常需要优先核查。',
    detail:
      '这是严重度标签，不表示本次已经触发，也不是下跌概率或收益预测。“未触发｜高”完全可以同时出现。',
  },
  MEDIUM: {
    label: '中',
    tone: 'warning',
    summary: '严重程度中等，需要结合其他证据核查。',
    detail: '严重度与“是否触发”分开显示，不是买卖建议或发生概率。',
  },
  LOW: {
    label: '低',
    tone: 'muted',
    summary: '相对较低的严重度或优先级。',
    detail: '低不代表没有风险；是否触发和缺项仍需分别查看。',
  },
  UNKNOWN: {
    label: '待补齐',
    tone: 'warning',
    summary: '当前证据不足以形成该项结论。',
    detail: '全A评级中表示范围或维度尚未满足，不等于市场已转弱，也不按0分处理。',
  },
  PARTIAL: {
    label: '部分覆盖',
    tone: 'warning',
    summary: '部分数据或方法条件已经可用，但仍有缺项。',
    detail: '只按实际覆盖范围阅读，不能扩大为完整全A；具体缺项见对应说明。',
  },
  COMPLETE: {
    label: '数据完整',
    tone: 'success',
    summary: '已满足当前声明范围的数据核对要求。',
    detail: '例如沪深供应商目录完整，不等于完整全A或全部研究维度已经齐备。',
  },
  AVAILABLE: {
    label: '可用',
    tone: 'success',
    summary: '该项资料或计算满足其已声明的可用条件。',
    detail: '只针对这项数据，不代表全部来源可用、模型已验证或适合买入。',
  },
  UNAVAILABLE: {
    label: '不可用',
    tone: 'warning',
    summary: '这一项没有产生可用结果；单凭状态名称无法确定具体原因。',
    detail: '请结合本条记录的具体缺项、失败阶段和诊断阅读，不将缺失结果填为0。',
  },
  INSUFFICIENT_HISTORY: {
    label: '历史不足',
    tone: 'muted',
    summary: '有效历史窗口不足，不能计算该项指标。',
  },
  SUCCESS: {
    label: '已完成',
    tone: 'success',
    summary: '任务执行流程已完成。',
    detail: '执行成功与数据完整分开判断，仍需核对数据质量、覆盖范围和研究边界。',
  },
  RUNNING: { label: '进行中', tone: 'info', summary: '后台任务正在执行，尚未形成最终结果。' },
  PENDING: { label: '等待中', tone: 'muted', summary: '任务已入队，等待后台执行。' },
  FAILED: {
    label: '失败',
    tone: 'danger',
    summary: '本次执行遇到错误，未完成预期流程。',
    detail: '查看失败阶段和具体原因，不用演示数据代替真实行情。',
  },
  CANCEL_REQUESTED: {
    label: '取消收尾中',
    tone: 'warning',
    summary: '已请求取消，后台正在停止与收尾，尚未完全结束。',
  },
  TIMEOUT_REQUESTED: {
    label: '超时收尾中',
    tone: 'warning',
    summary: '已到执行期限，后台正在停止与收尾。',
  },
  CANCELLED: {
    label: '已取消',
    tone: 'muted',
    summary: '该任务已取消，不表示此前所有资料都不可用。',
  },
  TIMED_OUT: { label: '已超时', tone: 'warning', summary: '本次任务超过允许执行时间后已结束。' },
  INTERRUPTED: {
    label: '执行中断',
    tone: 'warning',
    summary: '执行未正常完成，需要核对恢复或重试状态。',
  },
  SKIPPED: {
    label: '已跳过',
    tone: 'muted',
    summary: '本次无需重复执行或不满足执行条件，具体原因见任务记录。',
  },
  NOT_CHECKED: {
    label: '尚未请求',
    tone: 'muted',
    summary: '本轮还没有对此项能力进行有效请求或检查。',
  },
  NOT_READY: { label: '尚未就绪', tone: 'muted', summary: '执行所需的前置条件还未满足。' },
  UNSUPPORTED: {
    label: '不支持',
    tone: 'muted',
    summary: '这个数据通道或当前适配器没有提供所需能力。',
    detail: '例如能取个股日线，不表示也能取指数、历史目录或复权因子。',
  },
  COOLDOWN: {
    label: '冷却中',
    tone: 'warning',
    summary: '为控制重复失败和请求频率，正在等待再次尝试的时间。',
  },
  ACCESS_RESTRICTED: {
    label: '访问保护中',
    tone: 'warning',
    summary: '此前收到上游拒绝，当前保留本机访问保护；没有再次确认今天仍受限。',
  },
  ACCESS_COOLDOWN: {
    label: '访问冷却中',
    tone: 'warning',
    summary: '上游拒绝或限流后暂停请求，等待冷却截止。',
  },
  NOT_ARCHIVED: {
    label: '历史尚未归档',
    tone: 'info',
    summary: '这一天没有已保存且可核验的历史证据。',
    detail: '不能用今天的证券名单或报价倒填过去。',
  },
  RETRY_READY: {
    label: '等待重试',
    tone: 'warning',
    summary: '符合重试条件，等待后台按预算和间隔执行。',
  },
  SMALL_SAMPLE: { label: '小样本', tone: 'warning', summary: '样本数量少，统计结果稳定性不足。' },
  NO_TRIGGERED_SIGNALS: {
    label: '无触发样本',
    tone: 'muted',
    summary: '该范围没有可纳入统计的已触发信号，不等于不存在风险。',
  },
  WAITING_WINDOW: {
    label: '窗口未成熟',
    tone: 'muted',
    summary: '后续观察交易日尚不够，不能提前计算完整结果。',
  },
  SYNTHETIC: {
    label: '模拟数据',
    tone: 'warning',
    summary: '用于演示或测试的资料，不用于真实交易研究。',
  },
  DEMO: {
    label: '演示',
    tone: 'warning',
    summary: '演示输入，只用于熟悉流程，不生成真实买卖参考。',
  },
  OBSERVED: {
    label: '已观测',
    tone: 'info',
    summary: '已经保存该次实际观测，不代表已独立核验全部来源或效果。',
  },
  TRADING: {
    label: '当日有效',
    tone: 'success',
    summary: '有该业务日的有效交易行情；不表示行情正在实时更新。',
  },
  SUSPENDED: {
    label: '已确认停牌',
    tone: 'muted',
    summary: '该日有明确停牌证据，不能把旧收盘价当作当日成交价。',
  },
  STALE: {
    label: '行情陈旧',
    tone: 'warning',
    summary: '只有更早日期的记录，不能作为当前业务日的有效行情。',
  },
  MISSING: { label: '缺少行情', tone: 'muted', summary: '尚无该项所需行情，不能当作零价格。' },
  PROXY: {
    label: '代理观察',
    tone: 'info',
    summary: '采用指定指数或样本作观察代理，不能替代目标全市场范围。',
  },
  CONDITIONAL_MATCH: {
    label: '条件满足待复核',
    tone: 'info',
    summary: '行情条件已满足，但个人持仓、资金与风险前提尚未核实。',
    detail: '这是人工复核入口，不是立即下单或保证收益的指令。',
  },
  WAIT: {
    label: '等待条件',
    tone: 'muted',
    summary: '所需行情条件尚未全部满足，按方案列出的缺项等待确认。',
  },
  WATCH: {
    label: '继续复核',
    tone: 'warning',
    summary: '已有需要关注的变化，但尚未形成完整确认组合。',
  },
  RISK_BLOCKED: {
    label: '风险过滤',
    tone: 'warning',
    summary: '当前未通过方案的风险保护条件。',
    detail: '例如候选价带至结构止损距离过大，或已触发减仓条件。不要通过放宽止损绕过过滤。',
  },
};

/** 通道可用与资料可用采用不同措辞，未收录代码保留原值并明确释义缺失。 */
export function getStatusHelp(
  value?: string | null,
  context: StatusContext = 'general',
): StatusHelp {
  if (value === 'UNAVAILABLE' && context === 'holding') {
    return {
      label: '证据不足 · 暂不判断',
      tone: 'warning',
      summary: '该持有周期或行动方案的证据条件尚未满足，当前没有形成可用计划。',
      detail: '查看本条计划列出的研究日期、已有行情日期及具体缺项，再补齐相应资料。',
    };
  }
  if (value === 'UNAVAILABLE' && context === 'channel') {
    return {
      label: '最近采集未成功',
      tone: 'warning',
      summary: '这项通道能力最近采集未成功；具体原因以对应请求的诊断为准。',
      detail: '区分供应商拒绝、网络异常和数据校验失败，再按具体原因处理。',
    };
  }
  if (value === 'UNAVAILABLE' && context === 'quality') {
    return {
      label: '无可用结果',
      tone: 'warning',
      summary: '本次数据核验没有形成可用结果，需要查看执行阶段的实际缺项或错误。',
      detail: '任务执行状态与数据质量分开判断；未完成核验不能算作检查通过。',
    };
  }
  if (value === 'AVAILABLE' && context === 'channel') {
    return {
      label: '已连通',
      tone: 'success',
      summary: '该通道或这项能力最近一次请求可用。',
      detail: '其他能力、账号权限和已保存数据日期仍需分别核对，不代表全部数据持续可用。',
    };
  }
  return (
    (value && Object.hasOwn(statusHelp, value) ? statusHelp[value] : undefined) || {
      label: value || '暂无数据',
      tone: 'muted',
      summary: value ? `系统返回“${value}”，暂未收录该代码的专门释义。` : '尚未返回该项状态。',
      detail: '请结合该记录的具体原因阅读，不能据此判断风险已消失。',
    }
  );
}

export const ruleStatusCodes = [
  'TRIGGERED',
  'NOT_TRIGGERED',
  'INSUFFICIENT_DATA',
  'NOT_APPLICABLE',
] as const;

import type { ProviderChannel, SourceRecord } from '../types/market';

/** 通道能力说明与处理顺序；运行结论必须另取实际状态及入库证据。 */
export interface ChannelGuide {
  scope: string;
  boundary: string;
  steps: readonly string[];
  reference?: { label: string; url: string };
}
export const capabilityNames: Record<string, string> = {
  TRADING_CALENDAR: '交易日历',
  STOCK_DAILY: '个股日线',
  INDEX_DAILY: '指数日线',
  MARKET_SNAPSHOT: '市场截面',
  ADJUSTMENT_FACTORS: '截面复权因子',
};
export const channelGuides: Record<string, ChannelGuide> = {
  EXCHANGE_CALENDAR: {
    scope: '本地读取已核验的交易所年度休市通知，不连接行情服务器。',
    boundary: '目前覆盖2024—2026年；只承担日历。股票、指数和截面显示“不支持”是正常跳过。',
    steps: [
      '查询在已核验年份内：由交易所日历直接提供开休市日期，不需要购买接口。',
      '查询超出已核验年份：维护人员补充对应年度官方休市通知及测试，或使用其他真实日历来源。',
      '遇到临时休市公告：核对并更新年度资源；不能仅按星期补造交易日。',
    ],
  },
  BAOSTOCK: {
    scope: 'Java直接接入免费节点，承担日历、沪深个股及指数日线、同源因子与沪深截面。',
    boundary:
      '10001011是上游黑名单拒绝代码。当前保护说明以前被拒绝，不证明今天仍在黑名单，也不能证明当时是超量还是并发触发。',
    steps: [
      '先打开官方访问统计页，按实际出口IP核对待释放时间、日请求数与并发数。首次冻结6小时，本年多次限制按累计次数×6小时增长；以官方实际记录为准。',
      '停止同一出口IP下重复的采集进程，等待官方释放；待释放时间为空时按官方提示稍后查看，其他异常通过官网QQ群联系管理员。购买会员不是本错误的必需解决办法。',
      '官方确认恢复后，由维护人员记录确认依据再恢复本机访问；先核验一个标的的短窗口，成功后再继续串行任务。页面刷新和重启不会解除保护。',
    ],
    reference: { label: '官方限制规则与访问统计', url: 'https://www.baostock.com/blacklist' },
  },
  SINA: {
    scope: '免费公开历史接口：交易日历、个股原始日线及该个股同源复权因子。',
    boundary:
      '指数接口缺少可靠成交额，项目明确不启用。截面复权因子“不支持”指全市场研究能力，不代表个股日线没有因子。',
    steps: [
      '指数显示“不支持”：交给BaoStock、东方财富沪深300备用能力或已配置的Tushare，不重复请求新浪指数。',
      '日线出现日期不齐或日历区间不足：查看实际缺失日期，选择已完成交易日；有响应但日期不齐不等于网络被封。',
      'DNS、超时、断连分别检查网络与代理；缺因子、字段或解码失败由维护人员修正适配。只有记录了403/429才按明确访问拒绝或限流处理。',
    ],
  },
  EASTMONEY: {
    scope:
      '指数适配器承担沪深300日线；独立研究适配器承担当日市场截面。财报和机构预测是另外的采集能力。',
    boundary:
      '个股完整历史、日历和全市场历史复权因子尚未由该适配器实现；公开接口有数据，不等于项目已完成所有接口适配。',
    steps: [
      '个股或日历显示“不支持”：选择已实现对应能力的来源；若要新增东方财富个股能力，需要开发与核验同源因子，不能靠开会员消除本地缺项。',
      '当日截面等待收盘16:10后采集。过去日期没有自然归档时，当前名单不能倒填历史；复用已有归档，或接入有可靠历史清单的来源。',
      '已记录403/429时等待持久冷却截止；超时或连接提前关闭先查网络和代理。没有明确拒绝证据时，不称为已被封禁。',
    ],
  },
  TUSHARE: {
    scope: '可选账号来源，日线、指数、日历、历史清单及因子分别核验接口权限。',
    boundary: '未配置Token会在组合启动时跳过；“未配置”“未启用”和“权限被拒绝”是不同状态。',
    steps: [
      '先查看本页的配置状态；需要启用时，将tushare列入MARKET_CHANNELS，并在本机填写TUSHARE_TOKEN后重启。不要把Token贴进聊天或提交仓库。',
      '登录官方控制台逐个核验daily、index_daily、trade_cal、adj_factor、bak_basic和suspend_d权限。日线可试用不代表其他接口权限相同，也不自动获得商用展示许可。',
      '收到明确权限拒绝后，按对应接口补足权限或继续使用免费组合；网络超时、适配缺项和缺失历史不能通过统一购买套餐解决。',
    ],
    reference: { label: '官方日线接口与权限说明', url: 'https://tushare.pro/document/2?doc_id=27' },
  },
};

/** 只比较实际来源标识，不把另一家成功入库当成本通道成功。 */
export function savedChannelEvidence(channel: string, rows: readonly SourceRecord[]) {
  const matching = rows.filter(
    (row) => row.source === channel || row.source.startsWith(channel + '_'),
  );
  const latestDate =
    matching
      .map((row) => row.trade_date)
      .sort()
      .at(-1) ?? null;
  const latestFetched =
    matching
      .map((row) => row.fetched_at)
      .sort()
      .at(-1) ?? null;
  return { count: new Set(matching.map((row) => row.symbol)).size, latestDate, latestFetched };
}

/** 总体提示优先呈现实际受限能力；不将一次成功扩展成整条通道全部可用。 */
export function channelConclusion(channel: ProviderChannel): string {
  const abilities = channel.capabilities?.filter((item) => item.supported) ?? [];
  if (
    channel.state === 'ACCESS_RESTRICTED' ||
    abilities.some((item) => item.state === 'ACCESS_RESTRICTED')
  )
    return '本机访问保护中，尚未重新确认上游是否恢复。';
  if (abilities.some((item) => item.state === 'ACCESS_COOLDOWN'))
    return '部分能力保留上游拒绝后的访问冷却，请按能力查看截止时间。';
  if (abilities.some((item) => ['COOLDOWN', 'UNAVAILABLE', 'RETRY_READY'].includes(item.state)))
    return '部分能力最近采集失败；其他能力或已存历史可能仍可用。';
  if (abilities.some((item) => item.state === 'AVAILABLE') || channel.state === 'AVAILABLE')
    return '已有能力最近请求成功，其他能力仍须分别核对。';
  return '本次进程尚无成功请求证据；不表示受限，也不表示已经连通。';
}

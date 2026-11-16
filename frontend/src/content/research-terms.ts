/** 页面术语以本项目实际计算口径为准；一般概念不表示该指标已经接入。 */
export interface ResearchTerm {
  id: string;
  name: string;
  category: string;
  aliases: string[];
  meaning: string;
  example?: string;
  note: string;
}

export const termCategories = [
  '价格与量能',
  '风险规则',
  '财报与预测',
  '市场与主题',
  '研究方法',
] as const;

export const researchTerms: ResearchTerm[] = [
  {
    id: 'rule-status',
    name: '规则状态',
    category: '风险规则',
    aliases: ['已触发', '未触发', '数据不足', '不适用'],
    meaning:
      '回答“本期是否满足这条规则”。已触发是符合条件；未触发是资料足够但不符合；数据不足是无法判断；不适用是当前模式不使用。',
    example: '现金流恶化未触发，只说明这一条未触发，不代表整只股票没有风险。',
    note: '状态与严重度、执行是否成功、行情覆盖是否完整分别判断。',
  },
  {
    id: 'risk-level',
    name: '风险级别（严重度）',
    category: '风险规则',
    aliases: ['程度', '高', '中', '低', 'HIGH', 'MEDIUM', 'LOW'],
    meaning:
      '说明该条规则触发后需要多重视，不说明本期是否触发。高、中、低是相对严重度或核查优先级。',
    example: '“未触发｜高”表示高严重度规则本期未触发，并不矛盾。',
    note: '不是发生概率、下跌幅度、买卖评级或收益保证。',
  },
  {
    id: 'ma',
    name: '移动平均线（MA）',
    category: '价格与量能',
    aliases: ['MA5', 'MA10', 'MA20', 'MA60', 'MA250', '250日均线', '均线', '20日均线', '60日均线'],
    meaning:
      '最近指定数量有效交易日的同锚点复权收盘均价。MA20观察较短窗口，MA60观察中期趋势，MA250用于更长周期的趋势复核；它们不等同于合理估值。',
    example: '收盘100元、MA20为110元，距MA20为100÷110−1，约−9.09%。',
    note: '20日不是20个自然日；均线会随新K线变化，不是长期固定买卖价。',
  },
  {
    id: 'ma-distance',
    name: '距均线（乖离）',
    category: '价格与量能',
    aliases: ['距20日均线', '距60日均线', '偏离', '乖离率'],
    meaning: '收盘价÷对应均线−1。正数在均线上方，负数在均线下方。',
    note: '偏离大不必然意味着反弹或回落；必须核对同一复权锚点。',
  },
  {
    id: 'kline',
    name: '日K线与OHLC',
    category: '价格与量能',
    aliases: ['开盘', '最高', '最低', '收盘', '日线', 'K线', 'OHLC'],
    meaning: '一根日K记录一个交易日的开盘、最高、最低、收盘，并配合成交股数和成交金额观察。',
    note: '停牌或缺失记录不能补成0；不完整的盘中记录不能冒充正式收盘。',
  },
  {
    id: 'adjustment',
    name: '复权与复权因子',
    category: '价格与量能',
    aliases: ['同源因子', '后复权', '未复权', '锚点', '除权'],
    meaning:
      '用同一来源的价格因子统一历史价格尺度，减少分红、送转等事件造成的机械价格跳变。本系统价格换算为原价×当期因子÷截止日因子。',
    note: '价格因子不是股本因子，不能直接拿它调整成交股数；新量价策略用人民币成交额核对量能。',
  },
  {
    id: 'return',
    name: '复权收益',
    category: '价格与量能',
    aliases: ['涨跌幅', '5日收益', '20日收益', '区间收益'],
    meaning:
      '用同源复权价格比较期末与期初的变化，按小数存储、百分比展示。5期收益比较相隔5个有效交易日的两个收盘。',
    example: '不存在除权变化时，从100元变为110元，对应10%。',
    note: '不是个人盈亏；未计入个人成交价、费用和仓位。',
  },
  {
    id: 'volume',
    name: '成交量与成交额',
    category: '价格与量能',
    aliases: ['股', '手', '成交金额', '量能'],
    meaning:
      '成交量是成交股数；成交额是成交金额。本系统股票股数按股保存、金额按人民币元保存，部分页面换算为亿元展示。',
    example: '交易所股票数量常见1手=100股；金额不能与股数直接相除当成同一种量比。',
    note: '两者含义不同；不要把放量直接解读为资金净流入或一定看涨。',
  },
  {
    id: 'volume-ratio',
    name: '成交量比（技术指标页）',
    category: '价格与量能',
    aliases: ['量比', 'vol_ratio', '成交量比'],
    meaning: '本系统技术指标页用当日成交股数÷此前20个有效交易日的平均股数，不含当日进入分母。',
    example: '量比2倍表示当日成交股数是该历史均量的2倍。',
    note: '不是行情软件常见的盘中量比；不能与量价策略的成交额量比混用，也不区分主动买入和卖出。',
  },
  {
    id: 'turnover-ratio',
    name: '成交额量比（量价策略）',
    category: '价格与量能',
    aliases: ['5日成交额比', '当日成交额量比', '成交额量比', '量能比'],
    meaning:
      '当日比：当日成交额÷前20期平均成交额。5日比：最近5期平均成交额÷此前独立20期平均成交额，两个窗口不重叠。',
    example: '1.2倍是比对应历史均额高20%，不表示股价上涨20%。',
    note: '当前量价规则以1.2/0.8观察放量/缩量，属于未验证研究阈值。V1旧修订的代理量仍按原冻结口径标注。',
  },
  {
    id: 'atr14',
    name: 'ATR14（技术指标）',
    category: '价格与量能',
    aliases: ['ATR', '平均真实波幅', 'ATR14'],
    meaning:
      '衡量价格波动幅度。真实波幅取高低差、最高与前收差绝对值、最低与前收差绝对值中的最大值。技术指标页采用14期起始均值及后续Wilder平滑。',
    note: '单位是元，不是百分比，不代表方向；量价止损页另用“前14期简单平均真实波幅”，两种口径不要混用。',
  },
  {
    id: 'atr-reference',
    name: '前14期ATR（量价止损口径）',
    category: '价格与量能',
    aliases: ['前14期 ATR', '止损波动缓冲'],
    meaning: '量价参考取截止日前14期真实波幅的简单平均，不包括本期，供支撑区间宽度和止损缓冲使用。',
    note: '这是本项目明确的简单均值口径，区别于技术指标页的Wilder平滑ATR14。波动大不表示一定上涨或下跌。',
  },
  {
    id: 'rs20',
    name: '20日相对强度（RS20）',
    category: '价格与量能',
    aliases: ['RS20', '相对沪深300', '相对强度'],
    meaning:
      '比较个股与沪深300的20期收益比：(个股期末价÷期初价)÷(基准期末价÷期初价)−1。正数表示相对跑赢。',
    example: '个股涨10%、基准涨5%，RS20约为1.10÷1.05−1=4.76%。',
    note: '不是简单收益相减，也不是0至100的RSI。相对跑赢时个股仍可能下跌。',
  },
  {
    id: 'support',
    name: '支撑区间',
    category: '价格与量能',
    aliases: ['结构支撑', '原支撑', '支撑位'],
    meaning:
      '本项目根据已确认波谷或此前20期低点形成的价格参考带。原支撑已被跌破时保留原位置并标明失守。',
    note: '只是一种历史结构观察，不保证价格会在这里止跌。',
  },
  {
    id: 'resistance',
    name: '压力区间',
    category: '价格与量能',
    aliases: ['压力位', '上方压力', '最近压力区间'],
    meaning: '来自已确认波峰、两月前高或日K近似成交额价桶的上方观察带。',
    note: '不是必然回落的位置；日K价桶不是逐笔筹码或机构持仓。红色只是强调价位，不表示已触发卖出。',
  },
  {
    id: 'stop-reference',
    name: '结构止损参考',
    category: '价格与量能',
    aliases: ['止损位', '止损价', '结构止损', 'stopReference'],
    meaning: '本项目量价方法为原结构支撑下沿减0.5倍前14期平均真实波幅，并按收盘核验是否触及。',
    note: '不是个人可承担亏损或保证成交价；盘中穿越需另行核查。跳空、涨跌停和交易制度可能影响实际执行。',
  },
  {
    id: 'entry-band',
    name: '建仓／加仓观察带',
    category: '价格与量能',
    aliases: ['候选观察带', '建仓观察带', '加仓观察带', '建仓', '加仓', '回踩', '突破'],
    meaning:
      '只有价格、成交额、趋势和风险过滤都满足时，才进入人工复核的候选价格区间。加仓还需已有持仓和原逻辑有效。',
    note: '不是限价委托，不应因“未触发减仓”就买入；系统未核实个人是否持有或可用资金。',
  },
  {
    id: 'risk-distance',
    name: '参考风险距离',
    category: '价格与量能',
    aliases: ['技术上限', '8%', '追价', '风险过滤'],
    meaning:
      '候选观察带上沿至结构止损的价格距离。本项目新建／加仓用8%作技术过滤，上沿还受2%或3%价差限制。',
    note: '不是账户最大亏损；该阈值未验证效果，不能通过下移止损来凑条件。',
  },
  {
    id: 'trend-20',
    name: '20日趋势转弱',
    category: '风险规则',
    aliases: ['TREND_BREAK_20'],
    meaning: '本系统要求本期及前期收盘都低于各自MA20，并且本期MA20低于前期MA20，才触发。',
    note: '不只是单次跌破均线，也不等于已经发生或必将发生大跌。',
  },
  {
    id: 'trend-60',
    name: '60日趋势转弱',
    category: '风险规则',
    aliases: ['TREND_BREAK_60'],
    meaning: '本期收盘低于MA60，且MA60低于前期MA60时触发。',
    note: '60指有效交易日窗口；仍应结合量价、来源、财报及其他风险。',
  },
  {
    id: 'volume-sell',
    name: '放量下跌',
    category: '风险规则',
    aliases: ['VOLUME_SELL'],
    meaning: '当日复权收益低于保存的下跌阈值，同时成交量比超过保存的放量阈值。',
    note: '两项要同时满足；实际阈值以当前记录的冻结参数为准，不用现在的配置改写旧报告。',
  },
  {
    id: 'new-low',
    name: '20日收盘新低',
    category: '风险规则',
    aliases: ['NEW_LOW_20'],
    meaning: '本期收盘低于此前20个有效交易日的最低复权收盘价。',
    note: '比较的是收盘，不是盘中最低；此前20期不包括本期。',
  },
  {
    id: 'relative-weak',
    name: '相对强度走弱',
    category: '风险规则',
    aliases: ['RELATIVE_WEAK'],
    meaning: 'RS20低于本修订阈值，且比前期RS20更弱时触发。',
    note: '是相对基准的变化，不等于个股当日一定下跌。',
  },
  {
    id: 'profit-slowdown',
    name: '利润增速放缓（本系统规则）',
    category: '风险规则',
    aliases: ['PROFIT_SLOWDOWN'],
    meaning:
      '实际规则检查“营收同比高于阈值，同时利润同比低于阈值”，用来观察收入增长与盈利增长不同步。',
    note: '不是对未来利润的预测，也不是单纯比较前后两季增速；必须使用相同累计报告口径。',
  },
  {
    id: 'margin-rule',
    name: '毛利率下降规则',
    category: '风险规则',
    aliases: ['MARGIN_DECLINE'],
    meaning: '与上年相同累计报告期比较，毛利率下降超过本修订保存的百分点阈值时触发。',
    note: '下降3个百分点不同于相对下降3%；缺少同期毛利率时只能判数据不足。',
  },
  {
    id: 'cashflow-rule',
    name: '现金流恶化规则',
    category: '风险规则',
    aliases: ['CASHFLOW_WARNING'],
    meaning: '本系统要求本期经营现金流为负，且本期低于上年同期、上年同期又低于前年同期，才触发。',
    example: '三年同期从1亿元到−1亿元再到−3亿元，满足负值且连续下降的结构。',
    note: '未触发只代表这个组合不成立；不能仅凭现金流规则判断公司经营安全。',
  },
  {
    id: 'concentration',
    name: '持仓集中度',
    category: '风险规则',
    aliases: ['POSITION_CONCENTRATION', '持仓占比'],
    meaning: '用个人持仓相对总资产的占比观察单只证券风险是否过于集中，触发阈值以保存参数为准。',
    note: '自选研究不读取个人资产，集中度规则显示不适用；不适用不是数据不足。',
  },
  {
    id: 'theme-divergence',
    name: '主题走势分化',
    category: '风险规则',
    aliases: ['INDUSTRY_DIVERGENCE'],
    meaning:
      '主题本期收益仍为正，但有效主题成员中站上MA20的比例连续三期下降，说明参与强势的成员减少。',
    note: '至少需要2个有效成员、连续3个交易日记录及本修订的覆盖门槛；成员太少或历史不足不能判断。',
  },
  {
    id: 'revenue',
    name: '营业收入与归母净利润',
    category: '财报与预测',
    aliases: ['营业收入', '营收', '净利润', '归母净利润'],
    meaning:
      '营业收入是经营业务形成的收入；归母净利润是扣除相关成本费用等后、归属于母公司股东的净利润。',
    note: '收入增长不等于利润增长；累计财报不能与单季度或不同报告期直接混比。',
  },
  {
    id: 'cashflow',
    name: '经营现金流',
    category: '财报与预测',
    aliases: ['经营活动现金流量净额', 'OCF'],
    meaning: '经营活动现金流入减去现金流出的净额，反映经营环节实际现金收支。',
    note: '不等于净利润或现金余额，负值需要结合营运资金及业务阶段核查。',
  },
  {
    id: 'gross-margin',
    name: '毛利率',
    category: '财报与预测',
    aliases: ['grossMargin'],
    meaning: '通常为营业收入扣除营业成本后的毛利，占营业收入的比例。',
    example: '收入100元、营业成本70元，毛利率为30%。',
    note: '不等于净利率；本系统展示已披露累计口径，规则与上年同期比较。',
  },
  {
    id: 'yoy',
    name: '同比与百分点',
    category: '财报与预测',
    aliases: ['营收同比', '利润同比', 'YoY', '百分点'],
    meaning: '同比与上年相同期间比较。百分点是两个百分率相减的差，区别于相对百分比变化。',
    example: '毛利率30%降至27%，下降3个百分点，相对下降10%。',
    note: '负基数或接近0的基数会影响同比解释，需核对原始数值和供应商口径。',
  },
  {
    id: 'cumulative',
    name: '累计财报口径',
    category: '财报与预测',
    aliases: ['Q1', 'H1', 'Q3', 'ANNUAL', '累计', '同期'],
    meaning: 'Q1为一季度累计，H1为上半年累计，Q3为前三季度累计，ANNUAL为全年。',
    note: '前三季度累计不能直接与上半年累计比较增速，也不能当作第三季度单季值。',
  },
  {
    id: 'eps',
    name: 'EPS与机构盈利预测',
    category: '财报与预测',
    aliases: ['EPS', '每股收益', '机构预测', '分析师预期'],
    meaning: 'EPS是每股收益，单位元／股；机构预测页展示供应商汇总的未来年度预期及实际观测时间。',
    note: '预测不是已实现利润，不保证兑现；当前汇总值不能倒填为过去已经知道的预期。',
  },
  {
    id: 'breadth',
    name: '市场均线广度',
    category: '市场与主题',
    aliases: ['广度', '站上MA20', '站上MA60'],
    meaning: '观察多少有效交易股票站在各自MA20或MA60之上，并核对有效均线样本覆盖。',
    note: '不是指数涨幅，也不是上涨家数占比；当前沪深观察不包括完整北交所证明。',
  },
  {
    id: 'coverage',
    name: '覆盖率与市场范围',
    category: '市场与主题',
    aliases: ['SH_SZ_A', 'FULL_A', '覆盖率', '全A', '沪深A股'],
    meaning:
      '实际有效数据数量与明确分母比较。SH_SZ_A是沪深A股，FULL_A目标还包括北交所等完整范围核验。',
    note: '供应商沪深目录100%不等于全A或全部方法完整；不能拿几只自选代替全市场。',
  },
  {
    id: 'snapshot',
    name: '市场截面与历史均值',
    category: '市场与主题',
    aliases: ['截面', '连续完整截面', '相对5日 / 20日均值'],
    meaning:
      '截面是同一业务日多个证券的资料快照。市场成交额5／20日均值要求连续完整交易日记录，并包含业务日。',
    note: '这不同于个股策略中互不重叠的5／20日成交额比；没有归档的过去资料不倒填。',
  },
  {
    id: 'benchmark',
    name: '沪深300与基准',
    category: '市场与主题',
    aliases: ['基准', '沪深300', 'INDEX_PROXY'],
    meaning: '本系统采用沪深300作部分趋势和相对收益的比较基准。',
    note: '基准表现不能代表每只股票或完整全市场；指数代理分项不自动计入全A总分。',
  },
  {
    id: 'correlation',
    name: '相关系数与同业联动',
    category: '市场与主题',
    aliases: ['相关系数', '同业组合', '控制市场', '留一法', '量子纠缠'],
    meaning:
      '相关系数在−1至1之间，描述收益同向或反向程度。同业组合排除该股自身；控制市场后的相关性只剔除沪深300的线性影响。',
    note: '相关不证明因果或未来预测能力；“量子纠缠”只作类比，不是股票之间的物理规律。',
  },
  {
    id: 'excess-return',
    name: '同业收益差',
    category: '市场与主题',
    aliases: ['个股减同业', '超额收益', '收益差'],
    meaning: '同业联动页用个股区间收益减去同业组合区间收益，按百分点显示。',
    note: '它不同于RS20的收益比公式；不要把两个数值当成同一指标。',
  },
  {
    id: 'frozen',
    name: '冻结证据与报告修订',
    category: '研究方法',
    aliases: ['冻结', '修订', '版本', '输入证据'],
    meaning:
      '每次报告把实际输入和计算结果一同保存，新算法或新资料通过新修订呈现，不用今天行情改写旧报告。',
    note: '旧修订可应用新的样式与帮助说明，但不补算新策略或替换其数字。',
  },
  {
    id: 'dates',
    name: '业务日期、采集时间与可用时点',
    category: '研究方法',
    aliases: ['业务日期', '采集时间', '可用时间', '观测时间'],
    meaning:
      '业务日期是行情归属的交易日；采集时间是系统实际取得资料的时间；可用时点是研究允许使用证据的起点。',
    note: '今天采集到的历史资料不证明过去当时已经知道，时间不同不能互相替换。',
  },
  {
    id: 'validation',
    name: '历史验证与样本成熟',
    category: '研究方法',
    aliases: ['回测', '历史验证', '留出区间', '成熟样本', '严格时点', 'PIT'],
    meaning:
      '历史研究核对已保存信号和后续完整观察期；成熟样本需具备足够后续交易日，留出区间用于减少只适配已有样本的偏差。',
    note: '复算一致只证明链路和证据一致，不证明策略有效；事后取得的当前版本不等于严格时点回测。',
  },
  {
    id: 'sample-count',
    name: '有效样本与观察窗口',
    category: '研究方法',
    aliases: ['样本数', '观察窗口', '样本成熟'],
    meaning: '历史统计只纳入符合输入和完整后续窗口要求的已触发信号。保存记录条数不等于有效样本数。',
    note: '同一股票或相邻日期的样本可能相关，不能把小样本当作稳定有效的结论。',
  },
  {
    id: 'positive-ratio',
    name: '正收益占比',
    category: '研究方法',
    aliases: ['胜率', 'positiveRatio'],
    meaning: '有效样本中后续观察收益为正的数量占比，只是当前样本的描述统计。',
    note: '不是实际交易胜率，未扣交易费用，也不证明未来获得同样比例的收益。',
  },
  {
    id: 'return-statistics',
    name: '平均收益与收益中位数',
    category: '研究方法',
    aliases: ['平均收益', '收益中位数', '基准均值', '超额均值'],
    meaning:
      '平均收益是样本收益的算术平均；中位数是排序后居中的水平。历史统计的超额收益按个股收益减去同期基准收益解释。',
    note: '均值可能受少数极端样本影响，超额收益可能仍对应绝对亏损；未纳入实际成交费用。',
  },
  {
    id: 'position-actions',
    name: '建仓、加仓、减仓与清仓',
    category: '价格与量能',
    aliases: ['仓位', '建仓', '加仓', '减仓', '清仓'],
    meaning: '建仓是首次建立持仓；加仓是已有持仓后增加数量；减仓是减少持仓；清仓是全部卖出。',
    note: '当前自选模式未核实个人是否持有、成本和资金，页面仅给条件式研究，不执行这些操作或给出数量。',
  },
  {
    id: 'breakout-retest',
    name: '突破与回踩',
    category: '价格与量能',
    aliases: ['突破', '回踩', '假突破', '企稳'],
    meaning:
      '突破是价格越过原参考区；回踩是越过后回到参考附近，再观察能否守住。企稳需要按方案核对收盘、量能和其他条件。',
    note: '越过一次不保证突破成功；本系统加仓要求参考在确认日前形成，并核对连续两次收盘，不能使用当日新高倒设门槛。',
  },
  {
    id: 'trading-day',
    name: '交易日、有效K线与停牌',
    category: '价格与量能',
    aliases: ['交易日', '自然日', '有K线交易日', '停牌'],
    meaning:
      '自然日是日历中的每一天；交易日是交易所开市日期。单只股票停牌时可能没有正常成交K线。窗口使用满足来源与日期要求的有效记录。',
    note: '缺一条行情不能自动当作停牌，必须有明确停牌证据；停牌旧收盘不能当作当日成交。',
  },
  {
    id: 'unchanged',
    name: '平盘与涨跌家数',
    category: '市场与主题',
    aliases: ['平盘', '上涨家数', '下跌家数', '上涨占比'],
    meaning:
      '平盘是本期收盘相对所用前收盘没有变化，仍可能发生大量成交；涨跌家数统计有效交易股票的方向。',
    note: '平盘不等于停牌。未知、缺失或停牌不能填成平盘，上涨占比也不同于站上均线的广度。',
  },
  {
    id: 'price-limit',
    name: '涨停与跌停',
    category: '价格与量能',
    aliases: ['涨停', '跌停', '价格涨跌幅限制'],
    meaning:
      '价格触及该证券当日适用的涨跌幅限制边界。具体幅度和适用情况随证券、板块与当日状态而不同。',
    note: '触及边界不等于一定能成交；本系统的收盘研究未保证实际委托可执行，需核对交易所规则和真实流动性。',
  },
];

export const ruleTermIds: Record<string, string> = {
  TREND_BREAK_20: 'trend-20',
  TREND_BREAK_60: 'trend-60',
  VOLUME_SELL: 'volume-sell',
  NEW_LOW_20: 'new-low',
  RELATIVE_WEAK: 'relative-weak',
  PROFIT_SLOWDOWN: 'profit-slowdown',
  MARGIN_DECLINE: 'margin-rule',
  CASHFLOW_WARNING: 'cashflow-rule',
  POSITION_CONCENTRATION: 'concentration',
  INDUSTRY_DIVERGENCE: 'theme-divergence',
};

export function findTerm(id?: string): ResearchTerm | undefined {
  return researchTerms.find((item) => item.id === id);
}

/** 名称、别名和正文均支持搜索；分类只筛选显示，不修改任何研究资料。 */
export function searchTerms(query = '', category = ''): ResearchTerm[] {
  const words = query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
  return researchTerms.filter(
    (item) =>
      (!category || item.category === category) &&
      words.every((word) =>
        [item.name, ...item.aliases, item.meaning, item.example || '', item.note]
          .join(' ')
          .toLocaleLowerCase()
          .includes(word),
      ),
  );
}

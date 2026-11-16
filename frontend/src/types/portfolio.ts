/** 自选与人工资产记录类型；保持现有 REST 字段名称。 */
import type { Numeric } from './common';
import type { Instrument } from './market';

export interface WatchItem extends Instrument {
  /** 研究或排序的优先级，具体取值由该结构的业务说明确定。 */
  priority: number;
  /** 是否纳入当前自选研究；不改变证券主数据。 */
  enabled: boolean;
}

export interface Position extends Instrument {
  /** 当前手工持仓数量，单位股；必须非负。 */
  quantity: Numeric;
  /** 人工登记的每股成本，单位人民币元；必须非负。 */
  cost_price: Numeric;
  /** 本次资产或产业指标登记对应的业务日期。 */
  as_of_date: string;
}

export interface Account {
  /** 手工登记的投资资产总额，单位人民币元；未知时不能推算持仓集中度。 */
  total_assets?: Numeric | null;
  /** 本次资产或产业指标登记对应的业务日期。 */
  as_of_date?: string;
  /** 数据完整性状态；COMPLETE表示完整，证据或历史不足时不能按零值解释。 */
  dataStatus?: string;
}

export interface PositionsResponse {
  /** 当前页或当前响应包含的业务记录列表。 */
  items: Position[];
  /** 人工登记的资产总额及数据状态；未知时不推算集中度。 */
  account: Account;
  /** 资产货币标识，当前固定CNY。 */
  currency: string;
}

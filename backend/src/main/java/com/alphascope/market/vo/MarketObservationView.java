package com.alphascope.market.vo;

import com.alphascope.common.vo.CollectionView;
import java.time.LocalDateTime;

/**
 * 已保存市场观察及最近采集结果；查询只读，不触发上游回补。
 *
 * @author chenxuefeng
 */
public record MarketObservationView(
    /** 该对象的可用性或执行状态；须结合所属结构的状态口径理解。 */
    String status,

    /** 已保存不可变观察快照的主键；未取得快照时为空。 */
    Long snapshotId,

    /** 原始数据实际采集时间；复用缓存时不改成当前时间。 */
    LocalDateTime fetchedAt,

    /** 该市场快照已经保存的独立分项分析；尚无可用观察时为空。 */
    MarketObservation analysis,

    /** 采集状态及失败原因；只读页面不触发新的上游请求。 */
    CollectionView collection) {}

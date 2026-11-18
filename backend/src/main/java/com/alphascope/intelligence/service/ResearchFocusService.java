package com.alphascope.intelligence.service;

import com.alphascope.intelligence.dto.ResearchFocusQuery;
import com.alphascope.intelligence.vo.ResearchFocusView;

/**
 * 当前身份自选研究的只读变化摘要。
 *
 * @author chenxuefeng
 */
public interface ResearchFocusService {
  /**
   * 按自选优先级分页核对两期研究；单只失败不能遮住其他股票。
   *
   * @param query 截止日期与分页
   * @return 本页优先事项及完整自选数量
   */
  ResearchFocusView read(ResearchFocusQuery query);
}

package com.alphascope.identity.service;

import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.response.PageResponse;
import com.alphascope.identity.dto.UserAdminInput;
import com.alphascope.identity.vo.UserAdminView;

/**
 * 平台管理员账号支持，不读取用户私人研究内容。
 *
 * @author chenxuefeng
 */
public interface UserAdminService {
  /**
   * 分页查看账号及启用状态，不读取密码。
   *
   * @param page 页码及大小
   * @return 账号分页
   */
  PageResponse<UserAdminView> page(PageRequest page);

  /**
   * 恢复密码或变更状态，同时撤销旧会话。
   *
   * @param id 被管理账号
   * @param input 新状态及可选密码
   */
  void update(long id, UserAdminInput input);
}

package com.alphascope.identity.service.impl;

import com.alphascope.common.dto.PageRequest;
import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.exception.NotFoundException;
import com.alphascope.common.response.PageResponse;
import com.alphascope.common.response.ResultCode;
import com.alphascope.common.util.Jsons;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.dto.AuthEventWrite;
import com.alphascope.identity.dto.UserAdminInput;
import com.alphascope.identity.dto.UserAdminMutation;
import com.alphascope.identity.mapper.AuthMapper;
import com.alphascope.identity.service.PasswordHasher;
import com.alphascope.identity.service.UserAdminService;
import com.alphascope.identity.vo.UserAdminView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员仅处理账号支持；不能停用保留管理员或提升用户权限。
 *
 * @author chenxuefeng
 */
@Service
public class UserAdminServiceImpl implements UserAdminService {
  private final AuthMapper mapper;
  private final PasswordHasher passwords;

  public UserAdminServiceImpl(AuthMapper mapper, PasswordHasher passwords) {
    this.mapper = mapper;
    this.passwords = passwords;
  }

  /** {@inheritDoc} */
  @Override
  public PageResponse<UserAdminView> page(PageRequest page) {
    requireAdmin();
    page.validate();
    return new PageResponse<>(
        mapper.users(page), mapper.userCount(), page.getPage(), page.getSize());
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void update(long id, UserAdminInput input) {
    requireAdmin();
    if (mapper.byId(id) == null) throw new NotFoundException("账号不存在");
    if (id == 1 && !"ACTIVE".equals(input.status()))
      throw new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "不能停用保留管理员账号");
    mapper.updateUser(
        new UserAdminMutation(
            id,
            input.status(),
            input.password() == null ? null : passwords.hash(input.password())));
    mapper.revokeUser(id);
    mapper.event(new AuthEventWrite(Jsons.hash("user:" + id), true, "ADMIN_ACCOUNT_UPDATED"));
  }

  private void requireAdmin() {
    if (TenantContext.user() == null || !"ADMIN".equals(TenantContext.user().role()))
      throw new BusinessException(ResultCode.FORBIDDEN, 403, "需要管理员权限");
  }
}

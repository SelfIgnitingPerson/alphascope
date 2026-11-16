package com.alphascope.identity.service;

import com.alphascope.identity.dto.IssuedSession;
import com.alphascope.identity.dto.LoginInput;
import com.alphascope.identity.dto.RegistrationInput;
import com.alphascope.identity.entity.AuthSession;
import com.alphascope.identity.vo.UserView;
import java.util.List;

/**
 * 账号认证与会话契约，控制器不读取密码表。
 *
 * @author chenxuefeng
 */
public interface AuthService {
  /**
   * 验证密码并签发一次性随机会话。
   *
   * @param input 登录字段
   * @return Cookie写入所需的临时凭据
   */
  IssuedSession login(LoginInput input);

  /**
   * 校验邀请并创建独立用户。
   *
   * @param input 注册字段
   * @return 新会话及公开资料
   */
  IssuedSession register(RegistrationInput input);

  /**
   * 校验令牌，不从客户端读取租户号。
   *
   * @param token Cookie中的随机串
   * @return 有效会话或null
   */
  AuthSession authenticate(String token);

  /**
   * 注销会话。
   *
   * @param token Cookie随机串
   */
  void logout(String token);

  /**
   * 返回后台可执行用户。
   *
   * @return 不含密码的身份列表
   */
  List<UserView> activeUsers();
}

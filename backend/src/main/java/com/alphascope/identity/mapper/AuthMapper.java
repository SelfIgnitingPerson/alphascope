package com.alphascope.identity.mapper;

import com.alphascope.common.dto.PageRequest;
import com.alphascope.identity.dto.AuthEventWrite;
import com.alphascope.identity.dto.SessionQuery;
import com.alphascope.identity.dto.UserAdminMutation;
import com.alphascope.identity.entity.AppUser;
import com.alphascope.identity.entity.AuthSession;
import com.alphascope.identity.vo.UserAdminView;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 认证专用持久层，不通过私人研究SQL读取其他用户数据。
 *
 * @author chenxuefeng
 */
@Mapper
public interface AuthMapper {
  /**
   * 按规范化名称读取密码校验资料。
   *
   * @param username 登录名
   * @return 账号或null
   */
  AppUser byName(String username);

  /**
   * 读取指定后台执行身份。
   *
   * @param id 账号主键
   * @return 账号或null
   */
  AppUser byId(long id);

  /**
   * 注册新的普通账号。
   *
   * @param row 规范化名称、密码摘要与USER角色
   */
  void create(AppUser row);

  /**
   * 仅初始化尚未设置密码的保留管理员。
   *
   * @param row 首次管理员凭据
   * @return 实际更新行数
   */
  int bootstrap(AppUser row);

  /**
   * 保存摘要会话，不保存原始Cookie。
   *
   * @param row 用户、摘要、到期时间
   */
  void session(AuthSession row);

  /**
   * 校验摘要与有效期、启用状态。
   *
   * @param query 摘要及当前时间
   * @return 有效关联会话或null
   */
  AuthSession findSession(SessionQuery query);

  /**
   * 注销指定摘要会话。
   *
   * @param tokenHash 随机令牌摘要
   */
  void revoke(String tokenHash);

  /**
   * 清理已过期会话。
   *
   * @param now 当前上海时间
   */
  void expire(LocalDateTime now);

  /**
   * 限制单账号并行会话，保留最新记录。
   *
   * @param userId 用户主键
   * @param maximum 最多记录数
   */
  void trim(@Param("userId") long userId, @Param("maximum") int maximum);

  /**
   * 列出可处理后台任务的身份。
   *
   * @return 启用账号，不包含密码字段
   */
  List<AppUser> activeUsers();

  /**
   * 记录匿名化认证结果供限流审计。
   *
   * @param event 事件字段，不含凭据
   */
  void event(AuthEventWrite event);

  /**
   * 统计时间窗内失败次数。
   *
   * @param key 规范化账号摘要
   * @param since 窗口起点
   * @return 失败次数
   */
  int failures(@Param("key") String key, @Param("since") LocalDateTime since);

  /**
   * 分页查看非敏感账号资料。
   *
   * @param page 页码与大小
   * @return 当前页资料
   */
  List<UserAdminView> users(PageRequest page);

  /**
   * 统计所有账号，不读取私人研究。
   *
   * @return 账号数
   */
  long userCount();

  /**
   * 管理员更新启用状态和可空密码摘要。
   *
   * @param input 已校验的账号变更
   */
  void updateUser(UserAdminMutation input);

  /**
   * 撤销指定账号全部会话。
   *
   * @param id 被管理账号
   */
  void revokeUser(long id);

  /**
   * 清理过期认证审计，失败限流窗口小于保留期限。
   *
   * @param before 最早应保留时间
   */
  void purgeEvents(LocalDateTime before);

  /**
   * 序列化账号注册总量核查，仅锁定控制平面的保留管理员。
   *
   * @return 被锁定主键
   */
  Long lockRegistration();

  /**
   * 识别仅通过当前SQL首次安装的空白实例；已接纳的开发库BASELINE不符合此条件。
   *
   * @return 当前V1由SQL首次安装时为true
   */
  boolean freshInstall();

  /** 仅移除当前身份下的两条默认样例自选，不改共享证券、行情或历史研究。 */
  void clearSeedWatches();
}

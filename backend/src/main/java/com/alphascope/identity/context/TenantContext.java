package com.alphascope.identity.context;

import com.alphascope.identity.vo.UserView;

/**
 * 请求与后台执行的身份范围；必须通过作用域恢复，不能接受客户端传入租户号。
 *
 * @author chenxuefeng
 */
public final class TenantContext {
  private static final ThreadLocal<UserView> CURRENT = new ThreadLocal<>();

  private TenantContext() {}

  /** 读取已经过会话认证的当前身份，未绑定时为null。 */
  public static UserView user() {
    return CURRENT.get();
  }

  /** 只供本地兼容及显式内部执行读取标识；公网SQL拦截另行要求已绑定身份。 */
  public static long id() {
    return CURRENT.get() == null ? 1L : CURRENT.get().id();
  }

  /**
   * 在当前线程绑定身份并保存前一范围。
   *
   * @param user 会话验证或持久任务所属的身份
   * @return 必须在finally或try-with-resources关闭的身份作用域
   */
  public static Scope bind(UserView user) {
    if (user == null || user.id() < 1) throw new IllegalArgumentException("身份范围无效");
    UserView previous = CURRENT.get();
    CURRENT.set(user);
    return new Scope(previous);
  }

  /**
   * 可嵌套的范围恢复器，防止线程池复用泄漏身份。
   *
   * @author chenxuefeng
   */
  public static final class Scope implements AutoCloseable {
    private final UserView previous;

    private Scope(UserView previous) {
      this.previous = previous;
    }

    @Override
    public void close() {
      if (previous == null) CURRENT.remove();
      else CURRENT.set(previous);
    }
  }
}

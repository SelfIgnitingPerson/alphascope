package com.alphascope.identity.service.impl;

import com.alphascope.common.exception.BusinessException;
import com.alphascope.common.response.ResultCode;
import com.alphascope.common.util.Jsons;
import com.alphascope.identity.config.AccessProperties;
import com.alphascope.identity.context.TenantContext;
import com.alphascope.identity.dto.AuthEventWrite;
import com.alphascope.identity.dto.IssuedSession;
import com.alphascope.identity.dto.LoginInput;
import com.alphascope.identity.dto.RegistrationInput;
import com.alphascope.identity.dto.SessionQuery;
import com.alphascope.identity.entity.AppUser;
import com.alphascope.identity.entity.AuthSession;
import com.alphascope.identity.mapper.AuthMapper;
import com.alphascope.identity.service.AuthService;
import com.alphascope.identity.service.PasswordHasher;
import com.alphascope.identity.vo.UserView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 摘要会话、邀请制注册与账号级失败限制；不公开账号是否存在。
 *
 * @author chenxuefeng
 */
@Service
public class AuthServiceImpl implements AuthService, ApplicationRunner {
  private final AuthMapper mapper;
  private final PasswordHasher passwords;
  private final AccessProperties properties;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final Semaphore hashing = new Semaphore(4);
  private final SecureRandom random = new SecureRandom();
  private final String dummy;

  public AuthServiceImpl(
      AuthMapper mapper,
      PasswordHasher passwords,
      AccessProperties properties,
      Clock clock,
      TransactionTemplate tx) {
    this.mapper = mapper;
    this.passwords = passwords;
    this.properties = properties;
    this.clock = clock;
    this.tx = tx;
    this.dummy = passwords.hash("constant-time-invalid-account");
  }

  @Override
  public void run(ApplicationArguments arguments) {
    if (properties.getMode() != AccessProperties.Mode.PUBLIC) return;
    if (properties.getOrigins().isEmpty()
        || properties.getOrigins().stream()
            .anyMatch(origin -> !origin.startsWith("https://") || origin.contains("*")))
      throw new IllegalStateException("公网部署必须配置明确HTTPS来源");
    if (!properties.isSecureCookie()) throw new IllegalStateException("公网部署必须启用Secure会话Cookie");
    if (properties.isRegistrationEnabled() && properties.getInvitation().length() < 24)
      throw new IllegalStateException("开放注册必须配置至少24字符的邀请口令");
    AppUser owner = mapper.byId(1);
    if (owner != null && "ACTIVE".equals(owner.getStatus())) return;
    String name = normalize(properties.getBootstrapUsername()),
        password = properties.getBootstrapPassword();
    if (!name.matches("[a-z0-9_-]{3,64}") || password.length() < 12 || password.length() > 128)
      throw new IllegalStateException("首次公网启动需设置管理员名称及至少12字符的初始密码");
    AppUser row = new AppUser();
    row.setUsername(name);
    row.setPasswordHash(passwords.hash(password));
    mapper.bootstrap(row);
    if (mapper.freshInstall())
      try (var ignored = TenantContext.bind(new UserView(1, name, "ADMIN"))) {
        mapper.clearSeedWatches();
      }
    if (!"ACTIVE".equals(mapper.byId(1).getStatus()))
      throw new IllegalStateException("保留管理员不可用，请通过受控账号恢复处理");
  }

  /** {@inheritDoc} */
  @Override
  public IssuedSession login(LoginInput input) {
    String name = normalize(input.username()), key = Jsons.hash(name);
    if (mapper.failures(key, now().minusMinutes(15)) >= 10) throw limited("登录失败次数过多，请15分钟后重试");
    if (!hashing.tryAcquire()) throw limited("登录请求繁忙，请稍后再试");
    try {
      AppUser user = mapper.byName(name);
      boolean valid =
          passwords.matches(
              input.password(),
              user == null || user.getPasswordHash() == null ? dummy : user.getPasswordHash());
      if (!valid || user == null || !"ACTIVE".equals(user.getStatus())) {
        mapper.event(new AuthEventWrite(key, false, "CREDENTIAL_REJECTED"));
        throw new BusinessException(ResultCode.UNAUTHENTICATED, 401, "账号或密码错误");
      }
      mapper.event(new AuthEventWrite(key, true, "LOGIN", user.getId()));
      return issue(user);
    } finally {
      hashing.release();
    }
  }

  /** {@inheritDoc} */
  @Override
  public IssuedSession register(RegistrationInput input) {
    String invitation = properties.getInvitation();
    if (!properties.isRegistrationEnabled()
        || invitation.length() < 24
        || !MessageDigest.isEqual(
            invitation.getBytes(StandardCharsets.UTF_8),
            input.invitation().getBytes(StandardCharsets.UTF_8)))
      throw new BusinessException(ResultCode.FORBIDDEN, 403, "注册尚未开放或邀请码无效");
    if (!hashing.tryAcquire()) throw limited("注册请求繁忙，请稍后再试");
    try {
      var row = new AppUser();
      row.setUsername(normalize(input.username()));
      row.setPasswordHash(passwords.hash(input.password()));
      row.setRole("USER");
      row.setStatus("ACTIVE");
      return tx.execute(
          status -> {
            try {
              mapper.lockRegistration();
              if (mapper.userCount() >= properties.getMaximumAccounts())
                throw new BusinessException(
                    ResultCode.CAPACITY_EXCEEDED, 429, "账号数量已达当前实例上限，请联系管理员");
              mapper.create(row);
            } catch (DuplicateKeyException e) {
              throw new BusinessException(ResultCode.CONFLICT, 409, "该登录名称不可用，请更换");
            }
            mapper.event(
                new AuthEventWrite(Jsons.hash(row.getUsername()), true, "REGISTER", row.getId()));
            return issue(row);
          });
    } finally {
      hashing.release();
    }
  }

  /** {@inheritDoc} */
  @Override
  public AuthSession authenticate(String token) {
    return token != null && token.matches("[A-Za-z0-9_-]{43}")
        ? mapper.findSession(new SessionQuery(Jsons.hash(token), now()))
        : null;
  }

  /** {@inheritDoc} */
  @Override
  public void logout(String token) {
    if (token != null) mapper.revoke(Jsons.hash(token));
  }

  /** {@inheritDoc} */
  @Override
  public List<UserView> activeUsers() {
    var users =
        mapper.activeUsers().stream()
            .map(value -> new UserView(value.getId(), value.getUsername(), value.getRole()))
            .toList();
    if (properties.getMode() == AccessProperties.Mode.LOCAL
        && users.stream().noneMatch(value -> value.id() == 1)) {
      var all = new ArrayList<>(users);
      all.addFirst(new UserView(1, "local-owner", "ADMIN"));
      return List.copyOf(all);
    }
    return users;
  }

  private IssuedSession issue(AppUser user) {
    mapper.expire(now());
    String token = token(), csrf = token();
    var row = new AuthSession();
    row.setUserId(user.getId());
    row.setTokenHash(Jsons.hash(token));
    row.setCsrfHash(Jsons.hash(csrf));
    row.setExpiresAt(now().plusSeconds(properties.getSessionSeconds()));
    mapper.session(row);
    mapper.trim(user.getId(), properties.getMaximumSessions());
    return new IssuedSession(
        new UserView(user.getId(), user.getUsername(), user.getRole()), token, csrf);
  }

  private String token() {
    byte[] value = new byte[32];
    random.nextBytes(value);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private String normalize(String name) {
    return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai"));
  }

  private BusinessException limited(String message) {
    return new BusinessException(ResultCode.RATE_LIMITED, 429, message);
  }
}
